package report

import (
	"encoding/csv"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"os"
	"path/filepath"
	"testing"
)

func TestCSVIncludesWarmup(t *testing.T) {
	p := filepath.Join(t.TempDir(), "scenarios.csv")
	if err := SaveScenariosToCSV([]runner.Scenario{{Duration: 120000, WarmupDuration: 30000}}, p); err != nil {
		t.Fatal(err)
	}
	f, err := os.Open(p)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	rows, err := csv.NewReader(f).ReadAll()
	if err != nil {
		t.Fatal(err)
	}
	if rows[0][9] != "WarmupDuration" || rows[1][9] != "30000" {
		t.Fatal(rows)
	}
}
