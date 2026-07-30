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

func TestScenarioMetadataIgnoresGeneratedIDsAndUsesEveryStableField(t *testing.T) {
	first := Scenario{
		ExecutionID: "execution-1", ScenarioID: "scenario-1",
		Events: 10, Lambda: 0.5, Duration: 300, IntegrationProcesses: 2,
		MaxStartDelay: 1, Consumers: 4, Repetition: 3,
	}
	second := first
	second.ExecutionID = "execution-2"
	second.ScenarioID = "scenario-2"
	if first.Metadata() != second.Metadata() {
		t.Fatal("metadata differs only because generated IDs differ")
	}

	tests := []Scenario{
		func() Scenario { value := first; value.Events++; return value }(),
		func() Scenario { value := first; value.Lambda++; return value }(),
		func() Scenario { value := first; value.Duration++; return value }(),
		func() Scenario { value := first; value.IntegrationProcesses++; return value }(),
		func() Scenario { value := first; value.MaxStartDelay++; return value }(),
		func() Scenario { value := first; value.Consumers++; return value }(),
		func() Scenario { value := first; value.Repetition++; return value }(),
	}
	for _, changed := range tests {
		if first.Metadata() == changed.Metadata() {
			t.Fatalf("metadata did not distinguish changed scenario: %#v", changed)
		}
	}
}
