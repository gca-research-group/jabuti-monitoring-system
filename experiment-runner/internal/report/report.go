package report

import (
	"encoding/csv"
	"fmt"
	"os"
	"strconv"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func SaveScenariosToCSV(scenarios []runner.Scenario, filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("failed to create CSV file: %v", err)
	}
	defer file.Close()

	writer := csv.NewWriter(file)
	defer writer.Flush()

	header := []string{"ExecutionId", "ScenarioId", "Events", "Lambda", "Duration", "IntegrationProcesses", "MaxStartDelay", "Consumers", "Repetition"}
	if err := writer.Write(header); err != nil {
		return fmt.Errorf("failed to write CSV header: %v", err)
	}

	for _, scenario := range scenarios {
		row := []string{
			scenario.ExecutionID,
			scenario.ScenarioID,
			strconv.Itoa(scenario.Events),
			fmt.Sprintf("%f", scenario.Lambda),
			strconv.Itoa(scenario.Duration),
			strconv.Itoa(scenario.IntegrationProcesses),
			strconv.Itoa(scenario.MaxStartDelay),
			strconv.Itoa(scenario.Consumers),
			strconv.Itoa(scenario.Repetition),
		}

		if err := writer.Write(row); err != nil {
			return fmt.Errorf("failed to write CSV row: %v", err)
		}
	}

	return nil
}
