package infrastructure

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net"
	"sort"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/exporter"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

// QueueFinalizer leaves the broker running while stopping the separate worker.
type QueueFinalizer struct {
	SSH  CommandRunner
	Env  *config.Env
	Now  func() time.Time
	Logf func(string, ...any)
}

func (f *QueueFinalizer) StopConsumers() error {
	if f.SSH == nil || f.Env == nil {
		return fmt.Errorf("queue finalizer requires SSH and environment configuration")
	}
	e := f.Env
	if e.APIConsumerSSHServer == "" || e.APIConsumerSSHUser == "" || e.ResourceConsumerContainer == "" {
		return fmt.Errorf("consumer SSH host, user and container are required")
	}
	if err := f.SSH.Run(e.APIConsumerSSHUser, net.JoinHostPort(e.APIConsumerSSHServer, e.APIConsumerSSHPort),
		"docker stop --time 60 -- "+shellQuote(e.ResourceConsumerContainer)); err != nil {
		return fmt.Errorf("stop consumer worker: %w", err)
	}
	return nil
}

type queueInfo struct {
	Name           string `json:"name"`
	Ready          *int64 `json:"messages_ready"`
	Unacknowledged *int64 `json:"messages_unacknowledged"`
	Total          *int64 `json:"messages"`
}

func (f *QueueFinalizer) CollectAndSave(ctx context.Context, s runner.Scenario, destination string) error {
	rows, err := f.Collect(ctx, s)
	if err != nil {
		return err
	}
	if err := exporter.WriteQueues(destination, rows); err != nil {
		return err
	}
	logf := f.Logf
	if logf == nil {
		logf = log.Printf
	}
	for _, row := range rows {
		logf("queue snapshot: execution_id=%s scenario_id=%s repetition=%d captured_at=%s vhost=%q queue=%q ready=%d unacknowledged=%d total=%d output=%s", row.ExecutionID, row.ScenarioID, row.Repetition, row.CapturedAt.Format(time.RFC3339Nano), row.VirtualHost, row.QueueName, row.MessagesReady, row.MessagesUnacknowledged, row.Messages, destination)
	}
	return nil
}

func (f *QueueFinalizer) Collect(ctx context.Context, s runner.Scenario) ([]exporter.QueueSnapshot, error) {
	if f.SSH == nil || f.Env == nil {
		return nil, fmt.Errorf("queue collector requires SSH and environment configuration")
	}
	e := f.Env
	if e.RabbitMQServerIP == "" || e.RabbitMQSSHUser == "" || e.ResourceRabbitMQContainer == "" {
		return nil, fmt.Errorf("RabbitMQ SSH host, user and container are required")
	}
	query := func(arguments string) ([]byte, error) {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		return f.SSH.RunOutput(e.RabbitMQSSHUser, net.JoinHostPort(e.RabbitMQServerIP, e.RabbitMQSSHPort),
			"docker exec -- "+shellQuote(e.ResourceRabbitMQContainer)+" rabbitmqctl --quiet --timeout 60 --formatter json "+arguments)
	}
	output, err := query("list_vhosts name")
	if err != nil {
		return nil, fmt.Errorf("inspect RabbitMQ virtual hosts: %w", err)
	}
	var hosts []struct {
		Name *string `json:"name"`
	}
	if err := json.Unmarshal(output, &hosts); err != nil || hosts == nil {
		return nil, fmt.Errorf("invalid RabbitMQ virtual host output: %s", output)
	}
	now := time.Now
	if f.Now != nil {
		now = f.Now
	}
	captured := now().UTC()
	rows := make([]exporter.QueueSnapshot, 0)
	seenHosts := map[string]bool{}
	for _, host := range hosts {
		if host.Name == nil || *host.Name == "" || seenHosts[*host.Name] {
			return nil, fmt.Errorf("invalid or duplicate virtual host")
		}
		seenHosts[*host.Name] = true
		output, err := query("list_queues -p " + shellQuote(*host.Name) + " name messages_ready messages_unacknowledged messages")
		if err != nil {
			return nil, fmt.Errorf("inspect RabbitMQ queues in %q: %w", *host.Name, err)
		}
		var queues []queueInfo
		if err := json.Unmarshal(output, &queues); err != nil || queues == nil {
			return nil, fmt.Errorf("invalid queue output for virtual host %q", *host.Name)
		}
		seen := map[string]bool{}
		for _, q := range queues {
			if q.Name == "" || seen[q.Name] || q.Ready == nil || q.Unacknowledged == nil || q.Total == nil {
				return nil, fmt.Errorf("missing fields or duplicate queue in %q", *host.Name)
			}
			if *q.Ready < 0 || *q.Unacknowledged < 0 || *q.Total < 0 || *q.Ready > math.MaxInt64-*q.Unacknowledged || *q.Total != *q.Ready+*q.Unacknowledged {
				return nil, fmt.Errorf("invalid message counts for queue %q", q.Name)
			}
			seen[q.Name] = true
			rows = append(rows, exporter.QueueSnapshot{ExecutionID: s.ExecutionID, ScenarioID: s.ScenarioID, Repetition: int64(s.Repetition), CapturedAt: captured, VirtualHost: *host.Name, QueueName: q.Name, MessagesReady: *q.Ready, MessagesUnacknowledged: *q.Unacknowledged, Messages: *q.Total})
		}
	}
	sort.Slice(rows, func(i, j int) bool {
		if rows[i].VirtualHost != rows[j].VirtualHost {
			return rows[i].VirtualHost < rows[j].VirtualHost
		}
		return rows[i].QueueName < rows[j].QueueName
	})
	return rows, nil
}
