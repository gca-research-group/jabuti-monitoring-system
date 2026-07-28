package runner

import (
	"math/rand"
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

func TestGenerateScenariosBuildsAllCombinations(t *testing.T) {
	parameters := config.Parameters{
		Events:               []int{1, 2},
		IntegrationProcesses: []int{1, 3},
		Consumers:            []int{1, 5},
		Lambda:               0.5,
		Duration:             10,
		MaxStartDelay:        2,
		Repetitions:          2,
	}

	scenarios := GenerateScenarios(parameters, rand.New(rand.NewSource(1)))
	if got, want := len(scenarios), 16; got != want {
		t.Fatalf("scenario count = %d, want %d", got, want)
	}

	executionID := scenarios[0].ExecutionID
	scenarioRepetitions := make(map[string]int)
	for _, scenario := range scenarios {
		if scenario.ExecutionID != executionID {
			t.Fatalf("execution ID = %q, want %q", scenario.ExecutionID, executionID)
		}
		scenarioRepetitions[scenario.ScenarioID]++
	}

	if got, want := len(scenarioRepetitions), 8; got != want {
		t.Fatalf("unique scenario IDs = %d, want %d", got, want)
	}
	for scenarioID, repetitions := range scenarioRepetitions {
		if repetitions != parameters.Repetitions {
			t.Fatalf("scenario %s has %d repetitions, want %d", scenarioID, repetitions, parameters.Repetitions)
		}
	}
}
