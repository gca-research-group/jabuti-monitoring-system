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
	Random *rand.Rand
	Logf   func(string, ...any)

	randomMu sync.Mutex
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

	for integrationProcess := 1; integrationProcess <= scenario.IntegrationProcesses; integrationProcess++ {
		wg.Add(1)
		go func(process int) {
			defer wg.Done()
			e.runIntegrationProcess(scenario, process)
		}(integrationProcess)
	}

	wg.Wait()
}

func (e *Executor) runIntegrationProcess(scenario Scenario, integrationProcess int) {
	startDelay := e.startDelay(scenario.MaxStartDelay)
	if startDelay > 0 {
		e.Logf("delaying integration process %d by %v", integrationProcess, startDelay)
	}
	e.Sleep(startDelay)

	e.runScenario(scenario, integrationProcess)
}

func (e *Executor) runScenario(scenario Scenario, integrationProcess int) {
	var wg sync.WaitGroup

	for second := 0; second < scenario.Duration; second++ {
		for _, interval := range e.generateEvents(scenario.Events, scenario.Lambda) {
			wg.Add(1)
			go func(delay time.Duration) {
				defer wg.Done()
				e.Sleep(delay)
				if err := e.Client.ExecuteSmartContract(e.Token, BuildMessage(e.Env, scenario)); err != nil {
					e.Logf(
						"event dispatch failed: scenario_id=%s repetition=%d integration_process=%d: %v",
						scenario.ScenarioID,
						scenario.Repetition,
						integrationProcess,
						err,
					)
				}
			}(interval)
		}

		e.Sleep(time.Second)
	}

	wg.Wait()
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
	case e.Random == nil:
		return fmt.Errorf("random source is required")
	case e.Logf == nil:
		return fmt.Errorf("log function is required")
	default:
		return nil
	}
}
