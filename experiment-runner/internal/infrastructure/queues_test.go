package infrastructure

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type queueCommands struct {
	outputs             []string
	commands            []string
	runErr, errorOutput error
}

func (q *queueCommands) Run(user, address string, commands ...string) error {
	q.commands = append(q.commands, commands...)
	return q.runErr
}
func (q *queueCommands) RunOutput(user, address, command string) ([]byte, error) {
	q.commands = append(q.commands, command)
	if q.errorOutput != nil {
		return nil, q.errorOutput
	}
	if len(q.outputs) == 0 {
		return nil, errors.New("unexpected command")
	}
	output := q.outputs[0]
	q.outputs = q.outputs[1:]
	return []byte(output), nil
}
func queueFinalizer(commands *queueCommands) *QueueFinalizer {
	return &QueueFinalizer{SSH: commands, Env: &config.Env{APIConsumerSSHServer: "worker", APIConsumerSSHUser: "user", APIConsumerSSHPort: "22", ResourceConsumerContainer: "api-consumer", RabbitMQServerIP: "broker", RabbitMQSSHUser: "user", RabbitMQSSHPort: "22", ResourceRabbitMQContainer: "rabbitmq"}, Now: func() time.Time { return time.Date(2026, 10, 3, 9, 0, 0, 0, time.FixedZone("local", -3*3600)) }, Logf: func(string, ...any) {}}
}
func TestCollectAllQueuesAndVirtualHosts(t *testing.T) {
	commands := &queueCommands{outputs: []string{`[{"name":"/"},{"name":"tenant's host"}]`, `[{"name":"inbound","messages_ready":3,"messages_unacknowledged":1,"messages":4}]`, `[{"name":"dead-letter","messages_ready":0,"messages_unacknowledged":0,"messages":0}]`}}
	f := queueFinalizer(commands)
	if err := f.StopConsumers(); err != nil {
		t.Fatal(err)
	}
	rows, err := f.Collect(context.Background(), runner.Scenario{ExecutionID: "execution", ScenarioID: "scenario", Repetition: 2})
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) != 2 || rows[0].Messages != 4 || rows[1].QueueName != "dead-letter" || rows[1].Messages != 0 || rows[1].Repetition != 2 || rows[0].CapturedAt.Location() != time.UTC || rows[0].CapturedAt.Hour() != 12 {
		t.Fatalf("%+v", rows)
	}
	if !strings.Contains(commands.commands[0], "docker stop --time 60") || !strings.Contains(commands.commands[3], shellQuote("tenant's host")) {
		t.Fatal(commands.commands)
	}
}
func TestMalformedQueueInspection(t *testing.T) {
	for _, output := range []string{"", "null", "warning\n[]", `{}`, `[{"name":"q"}]`, `[{"name":"q","messages_ready":-1,"messages_unacknowledged":0,"messages":-1}]`, `[{"name":"q","messages_ready":1,"messages_unacknowledged":0,"messages":2}]`, `[{"name":"q","messages_ready":1.5,"messages_unacknowledged":0,"messages":1}]`} {
		t.Run(output, func(t *testing.T) {
			f := queueFinalizer(&queueCommands{outputs: []string{`[{"name":"/"}]`, output}})
			if _, err := f.Collect(context.Background(), runner.Scenario{}); err == nil {
				t.Fatal("accepted malformed output")
			}
		})
	}
}
func TestInspectionAndShutdownFailures(t *testing.T) {
	failure := errors.New("SSH failed")
	f := queueFinalizer(&queueCommands{runErr: failure, errorOutput: failure})
	if !errors.Is(f.StopConsumers(), failure) {
		t.Fatal("lost worker failure")
	}
	if _, err := f.Collect(context.Background(), runner.Scenario{}); !errors.Is(err, failure) {
		t.Fatal(err)
	}
	f = queueFinalizer(&queueCommands{outputs: []string{`[{"name":"/"}]`}})
	if err := f.CollectAndSave(context.Background(), runner.Scenario{}, filepath.Join(t.TempDir(), "queues.parquet")); err == nil {
		t.Fatal("saved partial inspection")
	}
}

func TestEmptyVirtualHostProducesValidEmptySnapshot(t *testing.T) {
	f := queueFinalizer(&queueCommands{outputs: []string{`[{"name":"/"}]`, `[]`}})
	if err := f.CollectAndSave(context.Background(), runner.Scenario{}, filepath.Join(t.TempDir(), "queues.parquet")); err != nil {
		t.Fatal(err)
	}
}

func TestSnapshotWriteFailureAndQueueLogging(t *testing.T) {
	outputs := []string{`[{"name":"/"}]`, `[{"name":"q","messages_ready":0,"messages_unacknowledged":0,"messages":0}]`}
	f := queueFinalizer(&queueCommands{outputs: append([]string(nil), outputs...)})
	var logs []string
	f.Logf = func(format string, args ...any) { logs = append(logs, format) }
	root := t.TempDir()
	blocked := filepath.Join(root, "file")
	if err := os.WriteFile(blocked, []byte("keep"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := f.CollectAndSave(context.Background(), runner.Scenario{}, filepath.Join(blocked, "queues.parquet")); err == nil {
		t.Fatal("ignored persistence failure")
	}
	if len(logs) != 0 {
		t.Fatal("logged unpublished snapshot")
	}
	f.SSH = &queueCommands{outputs: append([]string(nil), outputs...)}
	if err := f.CollectAndSave(context.Background(), runner.Scenario{}, filepath.Join(root, "queues.parquet")); err != nil {
		t.Fatal(err)
	}
	if len(logs) != 1 {
		t.Fatal("zero-count queue not logged")
	}
}
