package experiment

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/report"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"github.com/natefinch/atomic"
)

const (
	StatusPending                   = "pending"
	StatusExported                  = "exported"
	StatusExportedWithCountMismatch = "exported_with_count_mismatch"
	StatusFailed                    = "failed"
)

type RepetitionResult struct {
	ScenarioID       string `json:"scenarioId"`
	Repetition       int    `json:"repetition"`
	ExpectedRowCount int64  `json:"expectedRowCount"`
	ExportedRowCount int64  `json:"exportedRowCount"`
	ParquetPath      string `json:"parquetPath"`
	Status           string `json:"status"`
	Error            string `json:"error,omitempty"`
	FailedEvents     int    `json:"failedEvents,omitempty"`
}

type Manifest struct {
	ExecutionID string             `json:"executionId"`
	StartedAt   time.Time          `json:"startedAt"`
	FinishedAt  *time.Time         `json:"finishedAt,omitempty"`
	Scenarios   []runner.Scenario  `json:"scenarios"`
	Results     []RepetitionResult `json:"results"`
}

type Dataset struct {
	OutputRoot string
	Now        func() time.Time

	runDir   string
	manifest Manifest
}

func (d *Dataset) Initialize(scenarios []runner.Scenario) error {
	if len(scenarios) == 0 {
		return fmt.Errorf("at least one scenario is required")
	}
	if d.OutputRoot == "" {
		return fmt.Errorf("experiment output directory is required")
	}
	if d.Now == nil {
		d.Now = time.Now
	}

	d.runDir = filepath.Join(d.OutputRoot, "execution_id="+scenarios[0].ExecutionID)
	if err := os.MkdirAll(d.runDir, 0o755); err != nil {
		return fmt.Errorf("create execution output directory: %w", err)
	}
	if err := report.SaveScenariosToCSV(scenarios, filepath.Join(d.runDir, "scenarios.csv")); err != nil {
		return err
	}

	d.manifest = Manifest{
		ExecutionID: scenarios[0].ExecutionID,
		StartedAt:   d.Now().UTC(),
		Scenarios:   scenarios,
		Results:     make([]RepetitionResult, 0, len(scenarios)),
	}
	for _, scenario := range scenarios {
		d.manifest.Results = append(d.manifest.Results, RepetitionResult{
			ScenarioID:       scenario.ScenarioID,
			Repetition:       scenario.Repetition,
			ExpectedRowCount: expectedRows(scenario),
			ParquetPath:      d.relativePath(scenario),
			Status:           StatusPending,
		})
	}
	return d.saveManifest()
}

func (d *Dataset) Destination(scenario runner.Scenario) string {
	return filepath.Join(d.runDir, filepath.FromSlash(d.relativePath(scenario)))
}

func (d *Dataset) Record(scenario runner.Scenario, rowCount int64, failedEvents int, exportErr error) error {
	for index := range d.manifest.Results {
		result := &d.manifest.Results[index]
		if result.ScenarioID != scenario.ScenarioID || result.Repetition != scenario.Repetition {
			continue
		}
		result.ExportedRowCount = rowCount
		result.FailedEvents = failedEvents
		switch {
		case exportErr != nil:
			result.Status = StatusFailed
			result.Error = exportErr.Error()
		case rowCount != result.ExpectedRowCount:
			result.Status = StatusExportedWithCountMismatch
		default:
			result.Status = StatusExported
		}
		return d.saveManifest()
	}
	return fmt.Errorf("scenario %s repetition %d is not in manifest", scenario.ScenarioID, scenario.Repetition)
}

func (d *Dataset) Complete() error {
	finished := d.Now().UTC()
	d.manifest.FinishedAt = &finished
	return d.saveManifest()
}

func (d *Dataset) relativePath(scenario runner.Scenario) string {
	return filepath.ToSlash(filepath.Join(
		"scenario_id="+scenario.ScenarioID,
		fmt.Sprintf("repetition=%04d", scenario.Repetition),
		"events.parquet",
	))
}

func (d *Dataset) saveManifest() error {
	data, err := json.MarshalIndent(d.manifest, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal manifest: %w", err)
	}
	finalPath := filepath.Join(d.runDir, "manifest.json")
	if err := atomic.WriteFile(finalPath, bytes.NewReader(append(data, '\n'))); err != nil {
		return fmt.Errorf("atomically replace manifest: %w", err)
	}
	return nil
}

func expectedRows(scenario runner.Scenario) int64 {
	return int64(scenario.Events) * int64(scenario.Duration) * int64(scenario.IntegrationProcesses)
}
