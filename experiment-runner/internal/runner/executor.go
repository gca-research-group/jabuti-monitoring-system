package runner

import (
	"fmt"
	"log"
	"math/rand"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

type EventClient interface {
	ExecuteSmartContract(token string, message api.SmartContractMessage) error
}

type Executor struct {
	Client    EventClient
	Env       *config.Env
	Token     string
	Sleep     func(time.Duration)
	Random    *rand.Rand
	Logf      func(string, ...any)
	Now       func() time.Time
	WaitUntil func(time.Time)

	randomMu sync.Mutex
}

type requestCounters struct {
	sent       atomic.Uint64
	successful atomic.Uint64
	failed     atomic.Uint64

	failuresMu sync.Mutex
	failures   map[string]uint64
}

func NewExecutor(client EventClient, env *config.Env, token string, random *rand.Rand) *Executor {
	return &Executor{
		Client: client,
		Env:    env,
		Token:  token,
		Sleep:  time.Sleep,
		Random: random,
		Logf:   log.Printf,
	}
}

func (e *Executor) Run(s Scenario) error {
	if err := e.Prepare(s); err != nil {
		return err
	}
	s.Window = NewRunWindow(e.now(), s.WarmupDuration, s.Duration)
	return e.RunPrepared(s)
}

func (counters *requestCounters) recordFailure(category string) {
	counters.failuresMu.Lock()
	defer counters.failuresMu.Unlock()
	counters.failures[category]++
}

func (counters *requestCounters) failureSnapshot() map[string]uint64 {
	counters.failuresMu.Lock()
	defer counters.failuresMu.Unlock()

	snapshot := make(map[string]uint64, len(counters.failures))
	for category, quantity := range counters.failures {
		snapshot[category] = quantity
	}
	return snapshot
}

func formatRequestSummary(scenario Scenario, counters *requestCounters) string {
	failures := counters.failureSnapshot()
	var builder strings.Builder
	fmt.Fprintf(
		&builder,
		"scenario request summary: scenario_id=%s repetition=%d requests_sent=%d successful=%d failed=%d",
		scenario.ScenarioID,
		scenario.Repetition,
		counters.sent.Load(),
		counters.successful.Load(),
		counters.failed.Load(),
	)
	if len(failures) == 0 {
		return builder.String()
	}

	categories := make([]string, 0, len(failures))
	categoryWidth := len("category")
	quantityWidth := len("quantity")
	for category, quantity := range failures {
		categories = append(categories, category)
		categoryWidth = max(categoryWidth, len(category))
		quantityWidth = max(quantityWidth, len(strconv.FormatUint(quantity, 10)))
	}
	sort.Strings(categories)

	for _, category := range categories {
		fmt.Fprintf(
			&builder,
			" %s=%d",
			category,
			failures[category],
		)
	}
	return builder.String()
}

func (e *Executor) startDelay(maxStartDelay int) time.Duration {
	if maxStartDelay <= 0 {
		return 0
	}

	e.randomMu.Lock()
	defer e.randomMu.Unlock()

	return time.Duration(e.Random.Intn(maxStartDelay+1)) * time.Millisecond
}

func (e *Executor) generateEvents(n int, lambda float64) []time.Duration {
	e.randomMu.Lock()
	defer e.randomMu.Unlock()

	return GenerateExponentialEvents(n, lambda, e.Random)
}

func (e *Executor) Validate() error {
	switch {
	case e.Client == nil:
		return fmt.Errorf("event client is required")
	case e.Env == nil:
		return fmt.Errorf("environment configuration is required")
	case e.Sleep == nil:
		return fmt.Errorf("sleep function is required")
	case e.Random == nil:
		return fmt.Errorf("random source is required")
	case e.Logf == nil:
		return fmt.Errorf("log function is required")
	default:
		return nil
	}
}
