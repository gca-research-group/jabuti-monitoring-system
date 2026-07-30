package experiment

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/report"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type Dataset struct {
	OutputRoot string
	runDir     string
}

func (d *Dataset) Initialize(scenarios []runner.Scenario) error {
	if len(scenarios) == 0 {
		return fmt.Errorf("at least one scenario is required")
	}
	if d.OutputRoot == "" {
		return fmt.Errorf("experiment output directory is required")
	}

	d.runDir = filepath.Join(d.OutputRoot, scenarios[0].ExecutionID)
	if err := os.MkdirAll(d.runDir, 0o755); err != nil {
		return fmt.Errorf("create execution output directory: %w", err)
	}
	if err := report.SaveScenariosToCSV(scenarios, filepath.Join(d.runDir, "scenarios.csv")); err != nil {
		return err
	}
	return nil
}

func (d *Dataset) Destination(scenario runner.Scenario) string {
	return filepath.Join(
		d.runDir,
		scenario.ScenarioID,
		fmt.Sprintf("%04d.parquet", scenario.Repetition),
	)
}

func expectedRows(scenario runner.Scenario) int64 {
	return int64(scenario.Events) * int64(scenario.Duration) * int64(scenario.IntegrationProcesses)
}
