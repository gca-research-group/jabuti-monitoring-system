package runner

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"sync"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

type failingEventClient struct {
	mu    sync.Mutex
	calls int
}

func (c *failingEventClient) ExecuteSmartContract(string, api.SmartContractMessage) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.calls++
	return errors.New("request failed")
}

func TestExecutorCompletesWhenEventDispatchFails(t *testing.T) {
	client := &failingEventClient{}
	var (
		sleepMu sync.Mutex
		sleeps  []time.Duration
	)
	executor := NewExecutor(client, &config.Env{}, "token", rand.New(rand.NewSource(4)))
	executor.Sleep = func(duration time.Duration) {
		sleepMu.Lock()
		defer sleepMu.Unlock()
		sleeps = append(sleeps, duration)
	}
	executor.Logf = func(string, ...any) {}

	executor.Run(Scenario{
		Events:               1,
		Lambda:               0.5,
		Duration:             1,
		IntegrationProcesses: 1,
		MaxStartDelay:        3,
	})

	client.mu.Lock()
	calls := client.calls
	client.mu.Unlock()
	if calls != 1 {
		t.Fatalf("dispatch calls = %d, want 1", calls)
	}
	sleepMu.Lock()
	defer sleepMu.Unlock()
	if len(sleeps) != 3 {
		t.Fatalf("sleep calls = %v, want start delay, event delay, and one-second interval", sleeps)
	}
	if sleeps[0] < 0 || sleeps[0] > 3*time.Millisecond {
		t.Fatalf("start delay = %v, want between 0 and 3ms", sleeps[0])
	}
}

type categorizedEventClient struct {
	calls atomic.Uint64
}

func (client *categorizedEventClient) ExecuteSmartContract(string, api.SmartContractMessage) error {
	switch client.calls.Add(1) % 4 {
	case 1:
		return nil
	case 2:
		return context.DeadlineExceeded
	case 3:
		return syscall.ECONNRESET
	default:
		return errors.New("request failed")
	}
}

func TestExecutorLogsScenarioRequestSummary(t *testing.T) {
	client := &categorizedEventClient{}
	executor := NewExecutor(client, &config.Env{}, "token", rand.New(rand.NewSource(4)))
	executor.Sleep = func(time.Duration) {}

	var (
		logMu sync.Mutex
		logs  []string
	)
	executor.Logf = func(format string, arguments ...any) {
		logMu.Lock()
		defer logMu.Unlock()
		logs = append(logs, fmt.Sprintf(format, arguments...))
	}

	executor.Run(Scenario{
		ScenarioID:           "scenario-id",
		Repetition:           2,
		Events:               2,
		Lambda:               0.5,
		Duration:             2,
		IntegrationProcesses: 2,
	})

	logMu.Lock()
	defer logMu.Unlock()
	if len(logs) != 1 {
		t.Fatalf("log calls = %d, want one summary log: %v", len(logs), logs)
	}
	want := "scenario request summary: scenario_id=scenario-id repetition=2 requests_sent=8 successful=2 failed=6 connection_reset=2 timeout=2 unknown_failure=2"

	if logs[0] != want {
		t.Fatalf("summary log =\n%s\nwant:\n%s", logs[0], want)
	}
}

func TestExecutorOmitsFailureTableWhenEveryRequestSucceeds(t *testing.T) {
	client := &categorizedEventClient{}
	executor := NewExecutor(client, &config.Env{}, "token", rand.New(rand.NewSource(4)))
	executor.Sleep = func(time.Duration) {}

	var logs []string
	executor.Logf = func(format string, arguments ...any) {
		logs = append(logs, fmt.Sprintf(format, arguments...))
	}
	executor.Run(Scenario{
		ScenarioID:           "successful-scenario",
		Repetition:           1,
		Events:               1,
		Lambda:               0.5,
		Duration:             1,
		IntegrationProcesses: 1,
	})

	want := "scenario request summary: scenario_id=successful-scenario repetition=1 requests_sent=1 successful=1 failed=0"
	if len(logs) != 1 || logs[0] != want {
		t.Fatalf("logs = %q, want [%q]", logs, want)
	}
}
