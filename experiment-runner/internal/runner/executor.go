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
	Client EventClient
	Env    *config.Env
	Token  string
	Sleep  func(time.Duration)
	Random *rand.Rand
	Logf   func(string, ...any)

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

func (e *Executor) Run(scenario Scenario) {
	var wg sync.WaitGroup
	counters := &requestCounters{failures: make(map[string]uint64)}

	for integrationProcess := 1; integrationProcess <= scenario.IntegrationProcesses; integrationProcess++ {
		wg.Add(1)
		go func(process int) {
			defer wg.Done()
			e.runIntegrationProcess(scenario, process, counters)
		}(integrationProcess)
	}

	wg.Wait()
	e.Logf("%s", formatRequestSummary(scenario, counters))
}

func (e *Executor) runIntegrationProcess(scenario Scenario, integrationProcess int, counters *requestCounters) {
	startDelay := e.startDelay(scenario.MaxStartDelay)
	if startDelay > 0 {
		e.Logf("delaying integration process %d by %v", integrationProcess, startDelay)
	}
	e.Sleep(startDelay)

	e.runScenario(scenario, integrationProcess, counters)
}

func (e *Executor) runScenario(scenario Scenario, integrationProcess int, counters *requestCounters) {
	var wg sync.WaitGroup

	for second := 0; second < scenario.Duration; second++ {
		for _, interval := range e.generateEvents(scenario.Events, scenario.Lambda) {
			wg.Add(1)
			go func(delay time.Duration) {
				defer wg.Done()
				e.Sleep(delay)
				counters.sent.Add(1)
				if err := e.Client.ExecuteSmartContract(e.Token, BuildMessage(e.Env, scenario)); err != nil {
					counters.failed.Add(1)
					counters.recordFailure(api.ClassifyExecutionFailure(err))
					return
				}
				counters.successful.Add(1)
			}(interval)
		}

		e.Sleep(time.Second)
	}

	wg.Wait()
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
