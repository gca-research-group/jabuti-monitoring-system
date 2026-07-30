package runner

import (
	"errors"
	"math/rand"
	"sync"
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
	if sleeps[0] < 0 || sleeps[0] > 3*time.Second {
		t.Fatalf("start delay = %v, want between 0 and 3s", sleeps[0])
	}
}
