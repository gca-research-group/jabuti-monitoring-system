package runner

import (
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

func TestBuildMessagePreservesScenarioMetadata(t *testing.T) {
	env := &config.Env{BlockchainID: "blockchain", SmartContractID: "contract"}
	scenario := Scenario{
		ExecutionID:          "execution",
		ScenarioID:           "scenario",
		Events:               5,
		Lambda:               0.5,
		Duration:             10,
		IntegrationProcesses: 2,
		MaxStartDelay:        3,
		Consumers:            4,
		Repetition:           1,
	}

	message := BuildMessage(env, scenario)

	if message.BlockchainID != env.BlockchainID || message.SmartContractID != env.SmartContractID {
		t.Fatalf("message target = %q/%q, want %q/%q",
			message.BlockchainID, message.SmartContractID, env.BlockchainID, env.SmartContractID)
	}
	if message.ClauseName != "QueryProductByID" {
		t.Fatalf("clause name = %q", message.ClauseName)
	}
	if got := message.Metadata["ExecutionId"]; got != scenario.ExecutionID {
		t.Fatalf("ExecutionId = %v, want %q", got, scenario.ExecutionID)
	}
	if got := message.Metadata["ScenarioId"]; got != scenario.ScenarioID {
		t.Fatalf("ScenarioId = %v, want %q", got, scenario.ScenarioID)
	}
	if got := message.Metadata["Consumers"]; got != scenario.Consumers {
		t.Fatalf("Consumers = %v, want %d", got, scenario.Consumers)
	}
}
