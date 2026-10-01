package monitoring

import (
	"context"
	"encoding/json"
	"fmt"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/infrastructure"
	"golang.org/x/crypto/ssh"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"

	"time"
)

type Target struct{ Component, Host, User, Port, Container string }
type Docker interface {
	Resolve(context.Context, string) (string, error)
	Stats(context.Context, string) (Stats, error)
	Close() error
}
type Factory func(context.Context, Target) (Docker, error)
type statusError struct {
	code    int
	message string
}

func (e statusError) Error() string { return fmt.Sprintf("Docker HTTP %d: %s", e.code, e.message) }

type remoteDocker struct {
	ssh       *ssh.Client
	transport *http.Transport
	http      *http.Client
	version   string
}

func SSHFactory(socket string) Factory {
	return func(ctx context.Context, t Target) (Docker, error) {
		config, err := dockerSSHConfig(t)
		if err != nil {
			return nil, err
		}
		client, err := connectDockerSSH(ctx, t, config)
		if err != nil {
			return nil, err
		}
		d := &remoteDocker{ssh: client}
		forwardingTimeout, err := dockerForwardingTimeout(ctx)
		if err != nil {
			d.ssh.Close()
			return nil, err
		}
		d.transport = dockerTransport(d.ssh, socket, forwardingTimeout)
		d.http = &http.Client{Transport: d.transport}
		if err := d.negotiateVersion(ctx); err != nil {
			d.Close()
			return nil, err
		}
		return d, nil
	}
}

func dockerSSHConfig(t Target) (*ssh.ClientConfig, error) {
	settings := infrastructure.NewSSHClient()
	key, err := os.ReadFile(settings.PrivateKeyPath)
	if err != nil {
		return nil, err
	}
	signer, err := ssh.ParsePrivateKey(key)
	if err != nil {
		return nil, err
	}
	return &ssh.ClientConfig{User: t.User, Auth: []ssh.AuthMethod{ssh.PublicKeys(signer)}, HostKeyCallback: settings.HostKeyCallback}, nil
}

func connectDockerSSH(ctx context.Context, t Target, config *ssh.ClientConfig) (*ssh.Client, error) {
	conn, err := (&net.Dialer{}).DialContext(ctx, "tcp", net.JoinHostPort(t.Host, t.Port))
	if err != nil {
		return nil, err
	}
	if deadline, ok := ctx.Deadline(); ok {
		conn.SetDeadline(deadline)
	}
	finished := make(chan struct{})
	go func() {
		select {
		case <-ctx.Done():
			conn.Close()
		case <-finished:
		}
	}()
	cc, ch, rq, err := ssh.NewClientConn(conn, net.JoinHostPort(t.Host, t.Port), config)
	close(finished)
	if err != nil {
		conn.Close()
		return nil, err
	}
	conn.SetDeadline(time.Time{})
	return ssh.NewClient(cc, ch, rq), nil
}

func dockerForwardingTimeout(ctx context.Context) (time.Duration, error) {
	timeout := 5 * time.Second
	if deadline, ok := ctx.Deadline(); ok {
		timeout = time.Until(deadline)
		if timeout <= 0 {
			return 0, context.DeadlineExceeded
		}
	}
	return timeout, nil
}

func dockerTransport(client *ssh.Client, socket string, forwardingTimeout time.Duration) *http.Transport {
	return &http.Transport{DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
		ctx, cancel := context.WithTimeout(ctx, forwardingTimeout)
		defer cancel()
		// SSH streamlocal channel requests have no context API. Closing this dedicated
		// connection bounds a blocked forwarding request and permits next-tick recovery.
		done := make(chan struct{})
		go func() {
			select {
			case <-ctx.Done():
				client.Close()
			case <-done:
			}
		}()
		c, e := client.Dial("unix", socket)
		close(done)
		return c, e
	}}
}

type dockerVersion struct {
	APIVersion    string `json:"ApiVersion"`
	MinAPIVersion string `json:"MinAPIVersion"`
	OS            string `json:"Os"`
}

func (d *remoteDocker) negotiateVersion(ctx context.Context) error {
	var version dockerVersion
	if err := d.get(ctx, "/version", &version); err != nil {
		return fmt.Errorf("check Docker socket permissions and SSH Unix forwarding: %w", err)
	}
	selected, err := selectDockerVersion(version)
	if err != nil {
		return err
	}
	d.version = selected
	return nil
}

func selectDockerVersion(version dockerVersion) (string, error) {
	minor, e := strconv.Atoi(trimVersion(version.APIVersion))
	if e != nil || minor < 41 || version.OS != "linux" {
		return "", fmt.Errorf("Linux Docker API >=1.41 required, got %s on %s", version.APIVersion, version.OS)
	}
	selected := 41
	if version.MinAPIVersion != "" {
		minimum, e := strconv.Atoi(trimVersion(version.MinAPIVersion))
		if e != nil || minimum > minor {
			return "", fmt.Errorf("invalid Docker minimum API version %s", version.MinAPIVersion)
		}
		if minimum > selected {
			selected = minimum
		}
	}
	return fmt.Sprintf("/v1.%d", selected), nil
}

func trimVersion(v string) string {
	if len(v) > 2 && v[:2] == "1." {
		return v[2:]
	}
	return ""
}
func (d *remoteDocker) get(ctx context.Context, path string, out any) error {
	req, err := http.NewRequestWithContext(ctx, "GET", "http://docker"+path, nil)
	if err != nil {
		return err
	}
	res, err := d.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != 200 {
		b, _ := io.ReadAll(io.LimitReader(res.Body, 1024))
		return statusError{res.StatusCode, string(b)}
	}
	return json.NewDecoder(io.LimitReader(res.Body, 4<<20)).Decode(out)
}
func (d *remoteDocker) Resolve(ctx context.Context, name string) (string, error) {
	var c struct {
		ID    string `json:"Id"`
		State struct {
			Running bool `json:"Running"`
		}
	}
	err := d.get(ctx, d.version+"/containers/"+url.PathEscape(name)+"/json", &c)
	if err != nil {
		return "", err
	}
	if !c.State.Running || c.ID == "" {
		return "", fmt.Errorf("container %s is not running", name)
	}
	return c.ID, nil
}
func (d *remoteDocker) Stats(ctx context.Context, id string) (Stats, error) {
	var s Stats
	err := d.get(ctx, d.version+"/containers/"+url.PathEscape(id)+"/stats?stream=false&one-shot=true", &s)
	if err == nil && s.Read.IsZero() {
		err = fmt.Errorf("Docker stats missing read timestamp")
	}
	return s, err
}
func (d *remoteDocker) Close() error { d.transport.CloseIdleConnections(); return d.ssh.Close() }
