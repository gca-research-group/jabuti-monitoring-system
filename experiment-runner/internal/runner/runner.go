package runner

import (
	"fmt"
	"log"
	"math"
	"math/rand"
	"sort"
	"sync"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/google/uuid"
)

type Scenario struct {
	ScenarioId           string
	ExecutionId          string
	Events               int
	Lambda               float64
	Duration             int
	IntegrationProcesses int
	MaxStartDelay        int
	Consumers            int
	Repetition           int
}

func GenerateScenarios(parameters config.Parameters) []Scenario {
	scenarios := []Scenario{}

	executionId := uuid.New()

	for _, event := range parameters.Events {
		for _, integrationProcess := range parameters.IntegrationProcesses {
			for _, consumer := range parameters.Consumers {
				scenarioId := uuid.New()
				for repetition := 1; repetition <= parameters.Repetitions; repetition++ {
					scenarios = append(scenarios, Scenario{
						ScenarioId:           scenarioId.String(),
						ExecutionId:          executionId.String(),
						Events:               event,
						Lambda:               parameters.Lambda,
						Duration:             parameters.Duration,
						IntegrationProcesses: integrationProcess,
						MaxStartDelay:        parameters.MaxStartDelay,
						Consumers:            consumer,
						Repetition:           repetition,
					})
				}
			}
		}
	}

	r := rand.New(rand.NewSource(time.Now().UnixNano()))

	r.Shuffle(len(scenarios), func(i, j int) {
		scenarios[i], scenarios[j] = scenarios[j], scenarios[i]
	})

	return scenarios
}

func GenerateExponentialEvents(n int, lambda float64) []time.Duration {
	if n <= 0 {
		return nil
	}

	const min = 0.0
	const max = 1.0

	events := make([]time.Duration, 0, n)

	r := rand.New(rand.NewSource(time.Now().UnixNano()))

	a := math.Exp(-lambda * min)
	b := math.Exp(-lambda * max)

	for i := 0; i < n; i++ {
		u := r.Float64()

		x := -math.Log(a-u*(a-b)) / lambda

		d := time.Duration(x * float64(time.Second))

		events = append(events, d)
	}

	sort.Slice(events, func(i, j int) bool {
		return events[i] < events[j]
	})

	return events
}

func DispatchEvent(client *api.Client, env *config.Env, wg *sync.WaitGroup, interval time.Duration, accessToken string, scenario Scenario) {
	defer wg.Done()

	time.Sleep(interval)

	message := api.SmartContractMessage{
		BlockchainID:    env.BlockchainID,
		SmartContractID: env.SmartContractID,
		ClauseName:      "QueryProductByID",
		ClauseArguments: []api.ClauseArgument{
			{
				Name:  "id",
				Value: "1",
			},
		},
		Metadata: map[string]any{
			"ExecutionId":          scenario.ExecutionId,
			"ScenarioId":           scenario.ScenarioId,
			"Events":               scenario.Events,
			"Lambda":               scenario.Lambda,
			"Duration":             scenario.Duration,
			"IntegrationProcesses": scenario.IntegrationProcesses,
			"MaxStartDelay":        scenario.MaxStartDelay,
			"Consumers":            scenario.Consumers,
			"Repetition":           scenario.Repetition,
		},
	}

	if err := client.ExecuteSmartContract(accessToken, message); err != nil {
		log.Printf("failed to execute smart contract: %v", err)
	}
}

func RunParallelScenario(wg *sync.WaitGroup, client *api.Client, env *config.Env, scenario Scenario, accessToken string, integrationProcess int) {
	defer wg.Done()

	randomDelay := time.Duration(rand.Intn(scenario.MaxStartDelay+1)) * time.Second
	fmt.Printf("integration process %d will start in %v at %v\n", integrationProcess, randomDelay, time.Now())
	time.Sleep(randomDelay)

	fmt.Printf("starting integration process %d for scenario %s at %v\n", integrationProcess, scenario.ScenarioId, time.Now())
	RunScenario(client, env, scenario, accessToken)
	fmt.Printf("completed integration process %d for scenario %s at %v\n", integrationProcess, scenario.ScenarioId, time.Now())
}

func RunScenario(client *api.Client, env *config.Env, scenario Scenario, accessToken string) {
	var wg sync.WaitGroup

	events := make(map[int][]time.Duration)

	for i := range scenario.Duration {
		events[i] = GenerateExponentialEvents(scenario.Events, scenario.Lambda)
	}

	for i := 0; i < len(events); i++ {
		fmt.Printf("dispatching events %d at %v\n", i, time.Now())

		for _, interval := range events[i] {
			wg.Add(1)
			go DispatchEvent(client, env, &wg, interval, accessToken, scenario)
		}

		time.Sleep(1 * time.Second)
	}

	wg.Wait()

	fmt.Println("all events completed")
}
