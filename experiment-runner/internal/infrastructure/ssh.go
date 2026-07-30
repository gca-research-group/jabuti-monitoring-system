package infrastructure

import (
	"fmt"
	"os"
	"path/filepath"

	"golang.org/x/crypto/ssh"
)

type SSHClient struct {
	User            string
	PrivateKeyPath  string
	HostKeyCallback ssh.HostKeyCallback
}

func NewSSHClient() *SSHClient {
	home, _ := os.UserHomeDir()
	return &SSHClient{
		User:            "root",
		PrivateKeyPath:  filepath.Join(home, ".ssh", "id_ed25519"),
		HostKeyCallback: ssh.InsecureIgnoreHostKey(),
	}
}

func (c *SSHClient) Run(address string, commands ...string) error {
	client, err := c.connect(address)
	if err != nil {
		return err
	}
	defer client.Close()

	for _, command := range commands {
		session, err := client.NewSession()
		if err != nil {
			return fmt.Errorf("create SSH session: %w", err)
		}

		output, runErr := session.CombinedOutput(command)
		_ = session.Close()
		if runErr != nil {
			return fmt.Errorf("run %q: %w (output: %s)", command, runErr, output)
		}
	}

	return nil
}

func (c *SSHClient) RunOutput(address, command string) ([]byte, error) {
	client, err := c.connect(address)
	if err != nil {
		return nil, err
	}
	defer client.Close()

	session, err := client.NewSession()
	if err != nil {
		return nil, fmt.Errorf("create SSH session: %w", err)
	}
	defer session.Close()

	output, runErr := session.CombinedOutput(command)
	if runErr != nil {
		return output, fmt.Errorf("run %q: %w (output: %s)", command, runErr, output)
	}

	return output, nil
}

func (c *SSHClient) connect(address string) (*ssh.Client, error) {
	privateKey, err := os.ReadFile(c.PrivateKeyPath)
	if err != nil {
		return nil, fmt.Errorf("read private key %q: %w", c.PrivateKeyPath, err)
	}

	signer, err := ssh.ParsePrivateKey(privateKey)
	if err != nil {
		return nil, fmt.Errorf("parse private key: %w", err)
	}

	client, err := ssh.Dial("tcp", address, &ssh.ClientConfig{
		User:            c.User,
		Auth:            []ssh.AuthMethod{ssh.PublicKeys(signer)},
		HostKeyCallback: c.HostKeyCallback,
	})
	if err != nil {
		return nil, fmt.Errorf("connect to %s: %w", address, err)
	}

	return client, nil
}
