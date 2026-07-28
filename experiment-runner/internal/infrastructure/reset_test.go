package infrastructure

import (
	"errors"
	"reflect"
	"strings"
	"testing"
	"time"
)

type commandCall struct {
	address  string
	commands []string
}

type fakeCommandRunner struct {
	calls  []commandCall
	failAt int
}

func (f *fakeCommandRunner) Run(address string, commands ...string) error {
	f.calls = append(f.calls, commandCall{address: address, commands: commands})
	if len(f.calls) == f.failAt {
		return errors.New("command failed")
	}
	return nil
}

func TestResetRunsServicesInOrderWithReadinessWaits(t *testing.T) {
	ssh := &fakeCommandRunner{}
	var sleeps []time.Duration
	manager := ResetManager{
		SSH:   ssh,
		Sleep: func(duration time.Duration) { sleeps = append(sleeps, duration) },
	}

	if err := manager.Reset(); err != nil {
		t.Fatalf("Reset() error = %v", err)
	}

	addresses := make([]string, 0, len(ssh.calls))
	for _, call := range ssh.calls {
		addresses = append(addresses, call.address)
	}
	wantAddresses := []string{fabricAddress, rabbitMQAddress, postgresAddress, apiAddress}
	if !reflect.DeepEqual(addresses, wantAddresses) {
		t.Fatalf("addresses = %v, want %v", addresses, wantAddresses)
	}
	if !reflect.DeepEqual(sleeps, []time.Duration{20 * time.Second, 30 * time.Second}) {
		t.Fatalf("sleeps = %v, want [20s 30s]", sleeps)
	}

	postgresCommands := ssh.calls[2].commands
	copyCount := 0
	for _, command := range postgresCommands {
		if strings.Contains(command, "cp -a volumes/postgres/baseline") {
			copyCount++
		}
	}
	if copyCount != 1 {
		t.Fatalf("PostgreSQL baseline copy count = %d, want 1", copyCount)
	}
}

func TestResetReturnsContextAndStopsAfterFailure(t *testing.T) {
	ssh := &fakeCommandRunner{failAt: 2}
	manager := ResetManager{SSH: ssh, Sleep: func(time.Duration) {}}

	err := manager.Reset()
	if err == nil || !strings.Contains(err.Error(), "reset RabbitMQ") {
		t.Fatalf("Reset() error = %v", err)
	}
	if len(ssh.calls) != 2 {
		t.Fatalf("command groups = %d, want 2", len(ssh.calls))
	}
}
