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
		settings := infrastructure.NewSSHClient()
		key, err := os.ReadFile(settings.PrivateKeyPath)
		if err != nil {
			return nil, err
		}
		signer, err := ssh.ParsePrivateKey(key)
		if err != nil {
			return nil, err
		}
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
		cc, ch, rq, err := ssh.NewClientConn(conn, net.JoinHostPort(t.Host, t.Port), &ssh.ClientConfig{User: t.User, Auth: []ssh.AuthMethod{ssh.PublicKeys(signer)}, HostKeyCallback: settings.HostKeyCallback})
		close(finished)
		if err != nil {
			conn.Close()
			return nil, err
		}
		conn.SetDeadline(time.Time{})
		d := &remoteDocker{ssh: ssh.NewClient(cc, ch, rq)}
		forwardingTimeout := 5 * time.Second
		if deadline, ok := ctx.Deadline(); ok {
			forwardingTimeout = time.Until(deadline)
			if forwardingTimeout <= 0 {
				d.ssh.Close()
				return nil, context.DeadlineExceeded
			}
		}
		d.transport = &http.Transport{DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
			ctx, cancel := context.WithTimeout(ctx, forwardingTimeout)
			defer cancel()
			// SSH streamlocal channel requests have no context API. Closing this dedicated
			// connection bounds a blocked forwarding request and permits next-tick recovery.
			done := make(chan struct{})
			go func() {
				select {
				case <-ctx.Done():
					d.ssh.Close()
				case <-done:
				}
			}()
			c, e := d.ssh.Dial("unix", socket)
			close(done)
			return c, e
		}}
		d.http = &http.Client{Transport: d.transport}
		var version struct {
			APIVersion    string `json:"ApiVersion"`
			MinAPIVersion string `json:"MinAPIVersion"`
			OS            string `json:"Os"`
		}
		if err = d.get(ctx, "/version", &version); err != nil {
			d.Close()
			return nil, fmt.Errorf("check Docker socket permissions and SSH Unix forwarding: %w", err)
		}
		minor, e := strconv.Atoi(trimVersion(version.APIVersion))
		if e != nil || minor < 41 || version.OS != "linux" {
			d.Close()
			return nil, fmt.Errorf("Linux Docker API >=1.41 required, got %s on %s", version.APIVersion, version.OS)
		}
		selected := 41
		if version.MinAPIVersion != "" {
			minimum, e := strconv.Atoi(trimVersion(version.MinAPIVersion))
			if e != nil || minimum > minor {
				d.Close()
				return nil, fmt.Errorf("invalid Docker minimum API version %s", version.MinAPIVersion)
			}
			if minimum > selected {
				selected = minimum
			}
		}
		d.version = fmt.Sprintf("/v1.%d", selected)
		return d, nil
	}
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
