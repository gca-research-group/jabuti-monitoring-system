package runner

import (
	"fmt"
	"log"
	"math/rand"
	"sync"
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
	Now    func() time.Time
	Random *rand.Rand
	Logf   func(string, ...any)

	randomMu sync.Mutex
}

type ExecutionSummary struct {
	FailedEvents int
}

func NewExecutor(client EventClient, env *config.Env, token string, random *rand.Rand) *Executor {
	return &Executor{
		Client: client,
		Env:    env,
		Token:  token,
		Sleep:  time.Sleep,
		Now:    time.Now,
		Random: random,
		Logf:   log.Printf,
	}
}

func (e *Executor) Run(scenario Scenario) ExecutionSummary {
	var wg sync.WaitGroup
	failures := make(chan int, scenario.IntegrationProcesses)

	for integrationProcess := 1; integrationProcess <= scenario.IntegrationProcesses; integrationProcess++ {
		wg.Add(1)
		go func(process int) {
			defer wg.Done()
			failures <- e.runIntegrationProcess(scenario, process)
		}(integrationProcess)
	}

	wg.Wait()
	close(failures)

	summary := ExecutionSummary{}
	for count := range failures {
		summary.FailedEvents += count
	}
	return summary
}

func (e *Executor) runIntegrationProcess(scenario Scenario, integrationProcess int) int {
	startDelay := e.startDelay(scenario.MaxStartDelay)
	e.Logf("integration process %d will start in %v at %v", integrationProcess, startDelay, e.Now())
	e.Sleep(startDelay)

	e.Logf("starting integration process %d for scenario %s at %v", integrationProcess, scenario.ScenarioID, e.Now())
	failures := e.runScenario(scenario)
	e.Logf("completed integration process %d for scenario %s at %v", integrationProcess, scenario.ScenarioID, e.Now())
	return failures
}

func (e *Executor) runScenario(scenario Scenario) int {
	var wg sync.WaitGroup
	failures := make(chan struct{}, scenario.Events*scenario.Duration)

	for second := 0; second < scenario.Duration; second++ {
		e.Logf("dispatching events %d at %v", second, e.Now())

		for _, interval := range e.generateEvents(scenario.Events, scenario.Lambda) {
			wg.Add(1)
			go func(delay time.Duration) {
				defer wg.Done()
				e.Sleep(delay)
				if err := e.Client.ExecuteSmartContract(e.Token, BuildMessage(e.Env, scenario)); err != nil {
					e.Logf("failed to execute smart contract: %v", err)
					failures <- struct{}{}
				}
			}(interval)
		}

		e.Sleep(time.Second)
	}

	wg.Wait()
	close(failures)
	e.Logf("all events completed")
	return len(failures)
}

func (e *Executor) startDelay(maxStartDelay int) time.Duration {
	if maxStartDelay <= 0 {
		return 0
	}

	e.randomMu.Lock()
	defer e.randomMu.Unlock()

	return time.Duration(e.Random.Intn(maxStartDelay+1)) * time.Second
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
	case e.Now == nil:
		return fmt.Errorf("clock is required")
	case e.Random == nil:
		return fmt.Errorf("random source is required")
	case e.Logf == nil:
		return fmt.Errorf("log function is required")
	default:
		return nil
	}
}
