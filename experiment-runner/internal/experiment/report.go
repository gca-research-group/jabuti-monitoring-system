package experiment

import (
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/report"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type CSVReport struct{}

func (CSVReport) Save(scenarios []runner.Scenario, filename string) error {
	return report.SaveScenariosToCSV(scenarios, filename)
}
