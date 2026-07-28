package runner

import (
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

func BuildMessage(env *config.Env, scenario Scenario) api.SmartContractMessage {
	return api.SmartContractMessage{
		BlockchainID:    env.BlockchainID,
		SmartContractID: env.SmartContractID,
		ClauseName:      "QueryProductByID",
		ClauseArguments: []api.ClauseArgument{
			{Name: "id", Value: "1"},
		},
		Metadata: map[string]any{
			"ExecutionId":          scenario.ExecutionID,
			"ScenarioId":           scenario.ScenarioID,
			"Events":               scenario.Events,
			"Lambda":               scenario.Lambda,
			"Duration":             scenario.Duration,
			"IntegrationProcesses": scenario.IntegrationProcesses,
			"MaxStartDelay":        scenario.MaxStartDelay,
			"Consumers":            scenario.Consumers,
			"Repetition":           scenario.Repetition,
		},
	}
}
