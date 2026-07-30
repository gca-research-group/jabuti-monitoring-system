package experiment

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func TestDatasetCreatesPartitionedLayoutAndTracksResults(t *testing.T) {
	scenario := runner.Scenario{
		ExecutionID: "execution", ScenarioID: "scenario", Repetition: 2,
		Events: 3, Duration: 4, IntegrationProcesses: 5,
	}
	now := time.Date(2026, 7, 30, 12, 0, 0, 0, time.UTC)
	dataset := Dataset{OutputRoot: t.TempDir(), Now: func() time.Time { return now }}

	if err := dataset.Initialize([]runner.Scenario{scenario}); err != nil {
		t.Fatalf("Initialize() error = %v", err)
	}
	wantSuffix := filepath.Join("execution_id=execution", "scenario_id=scenario", "repetition=0002", "events.parquet")
	if got := dataset.Destination(scenario); filepath.Clean(got) != filepath.Join(dataset.OutputRoot, wantSuffix) {
		t.Fatalf("Destination() = %q", got)
	}
	if err := dataset.Record(scenario, 59, 1, nil); err != nil {
		t.Fatalf("Record() error = %v", err)
	}

	manifest := readManifest(t, filepath.Join(dataset.runDir, "manifest.json"))
	result := manifest.Results[0]
	if result.Status != StatusExportedWithCountMismatch || result.ExpectedRowCount != 60 || result.ExportedRowCount != 59 {
		t.Fatalf("result = %#v", result)
	}
	if _, err := os.Stat(filepath.Join(dataset.runDir, "scenarios.csv")); err != nil {
		t.Fatalf("scenarios.csv: %v", err)
	}
}

func TestDatasetRecordsFailureAndCompletion(t *testing.T) {
	scenario := runner.Scenario{ExecutionID: "e", ScenarioID: "s", Repetition: 1, Events: 1, Duration: 1, IntegrationProcesses: 1}
	dataset := Dataset{OutputRoot: t.TempDir(), Now: time.Now}
	if err := dataset.Initialize([]runner.Scenario{scenario}); err != nil {
		t.Fatal(err)
	}
	if err := dataset.Record(scenario, 0, 0, errors.New("query failed")); err != nil {
		t.Fatal(err)
	}
	if err := dataset.Complete(); err != nil {
		t.Fatal(err)
	}
	manifest := readManifest(t, filepath.Join(dataset.runDir, "manifest.json"))
	if manifest.Results[0].Status != StatusFailed || manifest.Results[0].Error != "query failed" || manifest.FinishedAt == nil {
		t.Fatalf("manifest = %#v", manifest)
	}
	if _, err := os.Stat(filepath.Join(dataset.runDir, "manifest.json.tmp")); !os.IsNotExist(err) {
		t.Fatalf("temporary manifest remains: %v", err)
	}
}

func readManifest(t *testing.T, path string) Manifest {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var manifest Manifest
	if err := json.Unmarshal(data, &manifest); err != nil {
		t.Fatal(err)
	}
	return manifest
}
