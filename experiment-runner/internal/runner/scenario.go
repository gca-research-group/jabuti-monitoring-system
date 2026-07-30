package runner

import (
	"math/rand"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/google/uuid"
)

type Scenario struct {
	ScenarioID           string
	ExecutionID          string
	Events               int
	Lambda               float64
	Duration             int
	IntegrationProcesses int
	MaxStartDelay        int
	Consumers            int
	Repetition           int
}

type ScenarioMetadata struct {
	Events               int     `json:"events"`
	Lambda               float64 `json:"lambda"`
	Duration             int     `json:"duration"`
	IntegrationProcesses int     `json:"integrationProcesses"`
	MaxStartDelay        int     `json:"maxStartDelay"`
	Consumers            int     `json:"consumers"`
	Repetition           int     `json:"repetition"`
}

func (s Scenario) Metadata() ScenarioMetadata {
	return ScenarioMetadata{
		Events:               s.Events,
		Lambda:               s.Lambda,
		Duration:             s.Duration,
		IntegrationProcesses: s.IntegrationProcesses,
		MaxStartDelay:        s.MaxStartDelay,
		Consumers:            s.Consumers,
		Repetition:           s.Repetition,
	}
}

func GenerateScenarios(parameters config.Parameters, random *rand.Rand) []Scenario {
	scenarios := make([]Scenario, 0,
		len(parameters.Events)*len(parameters.IntegrationProcesses)*len(parameters.Consumers)*parameters.Repetitions)
	executionID := uuid.NewString()

	for _, event := range parameters.Events {
		for _, integrationProcess := range parameters.IntegrationProcesses {
			for _, consumer := range parameters.Consumers {
				scenarioID := uuid.NewString()
				for repetition := 1; repetition <= parameters.Repetitions; repetition++ {
					scenarios = append(scenarios, Scenario{
						ScenarioID:           scenarioID,
						ExecutionID:          executionID,
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

	random.Shuffle(len(scenarios), func(i, j int) {
		scenarios[i], scenarios[j] = scenarios[j], scenarios[i]
	})

	return scenarios
}
