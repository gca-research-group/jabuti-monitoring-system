package experiment

import (
	"context"
	"encoding/csv"
	"errors"
	"fmt"
	"math/rand"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type recordingExecutor struct{ scenarios []runner.Scenario }

func (e *recordingExecutor) Run(s runner.Scenario) { e.scenarios = append(e.scenarios, s) }

type recordingExporter struct {
	fakeExporter
	scenarios []runner.Scenario
}

func (e *recordingExporter) Export(ctx context.Context, s runner.Scenario, destination string) error {
	e.scenarios = append(e.scenarios, s)
	return e.fakeExporter.Export(ctx, s, destination)
}

func TestSuiteResumeSchedule(t *testing.T) {
	for _, test := range []struct {
		name      string
		target    int
		completed []int
		multiple  bool
	}{
		{name: "ten to twenty-five", target: 25, completed: []int{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}},
		{name: "gaps", target: 5, completed: []int{2, 4}},
		{name: "empty registry", target: 5},
		{name: "lower target", target: 3, completed: []int{1, 2, 3, 4, 5}},
		{name: "separate configurations", target: 5, completed: []int{1, 2, 3, 4, 5}, multiple: true},
	} {
		t.Run(test.name, func(t *testing.T) {
			var events, logs []string
			parameters := oneScenarioParameters()
			parameters.Repetitions = test.target
			registry := &fakeRegistry{completed: make(map[runner.ScenarioMetadata]struct{})}
			metadata := runner.GenerateScenarios(oneScenarioParameters(), rand.New(rand.NewSource(1)))[0].Metadata()
			for _, repetition := range test.completed {
				metadata.Repetition = repetition
				registry.completed[metadata] = struct{}{}
			}
			if test.multiple {
				parameters.Events = []int{1, 2}
			}
			candidates := runner.GenerateScenarios(parameters, rand.New(rand.NewSource(1)))
			var want []runner.ScenarioMetadata
			for _, s := range candidates {
				if !registry.Contains(s.Metadata()) {
					want = append(want, s.Metadata())
				}
			}
			executor := &recordingExecutor{}
			exporter := &recordingExporter{fakeExporter: fakeExporter{events: &events}}
			results := &fakeResults{events: &events}
			suite := validSuite(&events)
			suite.Executor, suite.Exporter, suite.Results, suite.Registry = executor, exporter, results, registry
			suite.Logf = func(format string, args ...any) { logs = append(logs, fmt.Sprintf(format, args...)) }
			if err := suite.Run(parameters); err != nil {
				t.Fatal(err)
			}
			for name, scenarios := range map[string][]runner.Scenario{"initialized": results.initialized, "executed": executor.scenarios, "exported": exporter.scenarios} {
				var got []runner.ScenarioMetadata
				for _, s := range scenarios {
					got = append(got, s.Metadata())
				}
				if !reflect.DeepEqual(got, want) {
					t.Fatalf("%s = %v, want %v", name, got, want)
				}
			}
			if !reflect.DeepEqual(registry.marks, want) {
				t.Fatalf("marks = %v, want %v", registry.marks, want)
			}
			skipped := len(candidates) - len(want)
			if logs[0] != fmt.Sprintf("experiment schedule: requested=%d skipped=%d pending=%d", len(candidates), skipped, len(want)) {
				t.Fatalf("schedule log = %s", logs[0])
			}
			if logs[len(logs)-1] != fmt.Sprintf("experiment suite completed: executed=%d skipped=%d", len(want), skipped) {
				t.Fatalf("completion log = %s", logs[len(logs)-1])
			}
		})
	}
}

func TestSuitePersistentResumeAndArtifacts(t *testing.T) {
	root := t.TempDir()
	var runDirs []string
	for index, target := range []int{10, 25, 25} {
		var events []string
		parameters := oneScenarioParameters()
		parameters.Repetitions = target
		dataset := &Dataset{OutputRoot: root}
		executor := &recordingExecutor{}
		exporter := &recordingExporter{fakeExporter: fakeExporter{events: &events}}
		suite := validSuite(&events)
		suite.Results, suite.Registry = dataset, &JSONSuccessRegistry{OutputRoot: root}
		suite.Executor, suite.Exporter = executor, exporter
		if err := suite.Run(parameters); err != nil {
			t.Fatal(err)
		}
		wantCount := []int{10, 15, 0}[index]
		if len(executor.scenarios) != wantCount || len(exporter.scenarios) != wantCount {
			t.Fatalf("run %d executed/exported = %d/%d, want %d", index, len(executor.scenarios), len(exporter.scenarios), wantCount)
		}
		if wantCount == 0 {
			if dataset.runDir != "" || exporter.validations != 0 || len(events) != 0 {
				t.Fatal("completed run performed work")
			}
			continue
		}
		runDirs = append(runDirs, dataset.runDir)
		file, err := os.Open(filepath.Join(dataset.runDir, "scenarios.csv"))
		if err != nil {
			t.Fatal(err)
		}
		rows, err := csv.NewReader(file).ReadAll()
		file.Close()
		if err != nil {
			t.Fatal(err)
		}
		if len(rows) != wantCount+1 {
			t.Fatalf("CSV rows = %d", len(rows))
		}
		for i, s := range executor.scenarios {
			if index == 1 && (s.Repetition < 11 || s.Repetition > 25) {
				t.Fatalf("resumed repetition = %d", s.Repetition)
			}
			if rows[i+1][8] != fmt.Sprint(s.Repetition) {
				t.Fatal("CSV repetition changed")
			}
			if filepath.Base(dataset.Destination(s)) != fmt.Sprintf("%04d.parquet", s.Repetition) || filepath.Base(dataset.ResourceDestination(s)) != fmt.Sprintf("%04d.resources.parquet", s.Repetition) {
				t.Fatal("artifact repetition changed")
			}
		}
	}
	if runDirs[0] == runDirs[1] {
		t.Fatal("resume reused execution directory")
	}
	for _, dir := range runDirs {
		if _, err := os.Stat(filepath.Join(dir, "scenarios.csv")); err != nil {
			t.Fatal(err)
		}
	}
}

func TestSuiteChangedMetadataRemainsPending(t *testing.T) {
	base := oneScenarioParameters()
	metadata := runner.GenerateScenarios(base, rand.New(rand.NewSource(1)))[0].Metadata()
	for _, field := range []string{"events", "lambda", "duration", "integrationProcesses", "maxStartDelay", "consumers"} {
		t.Run(field, func(t *testing.T) {
			parameters := base
			switch field {
			case "events":
				parameters.Events = []int{2}
			case "lambda":
				parameters.Lambda++
			case "duration":
				parameters.Duration++
			case "integrationProcesses":
				parameters.IntegrationProcesses = []int{2}
			case "maxStartDelay":
				parameters.MaxStartDelay++
			case "consumers":
				parameters.Consumers = []int{2}
			}
			var events []string
			suite := validSuite(&events)
			suite.Registry = &fakeRegistry{completed: map[runner.ScenarioMetadata]struct{}{metadata: {}}}
			if err := suite.Run(parameters); err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(strings.Join(events, ","), "run") {
				t.Fatal("changed metadata was skipped")
			}
		})
	}
}

func TestSuiteRetriesUnregisteredFailures(t *testing.T) {
	for _, mode := range []string{"export", "monitoring"} {
		t.Run(mode, func(t *testing.T) {
			root := t.TempDir()
			for attempt := 0; attempt < 2; attempt++ {
				var events []string
				suite := validSuite(&events)
				registry := &JSONSuccessRegistry{OutputRoot: root}
				executor := &recordingExecutor{}
				suite.Registry, suite.Executor = registry, executor
				if attempt == 0 {
					if mode == "export" {
						suite.Exporter = &fakeExporter{events: &events, exportErr: errors.New("export failed")}
					} else {
						suite.Monitor = fakeMonitor{events: &events, stopErr: errors.New("monitoring failed")}
					}
				}
				err := suite.Run(oneScenarioParameters())
				if (err != nil) != (attempt == 0 && mode == "monitoring") {
					t.Fatalf("Run() error = %v", err)
				}
				if len(executor.scenarios) != 1 {
					t.Fatal("unregistered repetition was not executed")
				}
				if registry.Contains(executor.scenarios[0].Metadata()) != (attempt == 1) {
					t.Fatal("incorrect completion state")
				}
			}
		})
	}
}
