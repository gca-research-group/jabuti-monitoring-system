package experiment

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func TestDatasetCreatesFlattenedLayoutWithoutManifest(t *testing.T) {
	scenario := runner.Scenario{
		ExecutionID: "execution", ScenarioID: "scenario", Repetition: 2,
	}
	dataset := Dataset{OutputRoot: t.TempDir()}

	if err := dataset.Initialize([]runner.Scenario{scenario}); err != nil {
		t.Fatalf("Initialize() error = %v", err)
	}

	want := filepath.Join(dataset.OutputRoot, "execution", "scenario", "0002.parquet")
	if got := dataset.Destination(scenario); filepath.Clean(got) != want {
		t.Fatalf("Destination() = %q, want %q", got, want)
	}
	if _, err := os.Stat(filepath.Join(dataset.OutputRoot, "execution", "scenarios.csv")); err != nil {
		t.Fatalf("scenarios.csv: %v", err)
	}
	if _, err := os.Stat(filepath.Join(dataset.OutputRoot, "execution", "manifest.json")); !os.IsNotExist(err) {
		t.Fatalf("manifest.json exists or cannot be checked: %v", err)
	}
}

func TestDatasetFormatsRepetitionsWithAtLeastFourDigits(t *testing.T) {
	dataset := Dataset{OutputRoot: t.TempDir()}
	scenario := runner.Scenario{ExecutionID: "e", ScenarioID: "s", Repetition: 12345}
	if err := dataset.Initialize([]runner.Scenario{scenario}); err != nil {
		t.Fatal(err)
	}
	want := filepath.Join(dataset.OutputRoot, "e", "s", "12345.parquet")
	if got := dataset.Destination(scenario); got != want {
		t.Fatalf("Destination() = %q, want %q", got, want)
	}
}
