package experiment

import (
	"context"
	"errors"
	"math/rand"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type fakeAPI struct {
	events     *[]string
	setUpError error
	stopError  error
}

func (f fakeAPI) SetUpConsumers(string, int) error {
	*f.events = append(*f.events, "consumers")
	return f.setUpError
}
func (f fakeAPI) StopRabbitMQ(string) error {
	*f.events = append(*f.events, "stop")
	return f.stopError
}

func (f fakeAPI) ExecuteSmartContract(token string, message api.SmartContractMessage) error {
	return nil
}

type fakeInfrastructure struct {
	events *[]string
	calls  int
	failAt int
}

func (f *fakeInfrastructure) Reset() error {
	f.calls++
	*f.events = append(*f.events, "reset")
	if f.calls == f.failAt {
		return errors.New("reset failed")
	}
	return nil
}

type fakeExecutor struct {
	events *[]string
}

func (f fakeExecutor) Run(runner.Scenario) {
	*f.events = append(*f.events, "run")
}

type fakeExporter struct {
	events      *[]string
	validateErr error
	exportErr   error
	validations int
}

func (f *fakeExporter) Validate(context.Context) error {
	f.validations++
	return f.validateErr
}
func (f *fakeExporter) Export(context.Context, runner.Scenario, string) error {
	*f.events = append(*f.events, "export")
	return f.exportErr
}

type fakeResults struct {
	events      *[]string
	initialized []runner.Scenario
}

func (f *fakeResults) Initialize(scenarios []runner.Scenario) error {
	*f.events = append(*f.events, "initialize")
	f.initialized = append([]runner.Scenario(nil), scenarios...)
	return nil
}
func (f *fakeResults) Destination(runner.Scenario) string { return "events.parquet" }

type fakeRegistry struct {
	completed map[runner.ScenarioMetadata]struct{}
	loadErr   error
	markErr   error
	marks     []runner.ScenarioMetadata
}

func (f *fakeRegistry) Load() error { return f.loadErr }
func (f *fakeRegistry) Contains(metadata runner.ScenarioMetadata) bool {
	_, exists := f.completed[metadata]
	return exists
}
func (f *fakeRegistry) MarkSuccessful(metadata runner.ScenarioMetadata) error {
	if f.markErr != nil {
		return f.markErr
	}
	f.marks = append(f.marks, metadata)
	if f.completed == nil {
		f.completed = make(map[runner.ScenarioMetadata]struct{})
	}
	f.completed[metadata] = struct{}{}
	return nil
}

func TestSuiteExportsAfterStopAndBeforeNextReset(t *testing.T) {
	var events []string
	suite := validSuite(&events)

	err := suite.Run(oneScenarioParameters())
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}

	want := []string{"initialize", "reset", "consumers", "run", "stop", "export", "reset"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteLogsExportFailureAndContinuesToFinalReset(t *testing.T) {
	var events []string
	exportErr := errors.New("write failed")
	suite := validSuite(&events)
	suite.Exporter = &fakeExporter{events: &events, exportErr: exportErr}

	if err := suite.Run(oneScenarioParameters()); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
}

func TestSuiteAbortsWhenStopRabbitMQFailsWithoutExport(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Client = fakeAPI{events: &events, stopError: errors.New("stop failed")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "stop processing") {
		t.Fatalf("Run() error = %v", err)
	}
	want := []string{"initialize", "reset", "consumers", "run", "stop"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteValidatesDatabaseBeforeInitializeOrReset(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Exporter = &fakeExporter{events: &events, validateErr: errors.New("unreachable")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "unreachable") {
		t.Fatalf("Run() error = %v", err)
	}
	if len(events) != 0 {
		t.Fatalf("events = %v, want none", events)
	}
}

func TestSuiteSkipsCompletedRepetitionsIndividually(t *testing.T) {
	var events []string
	parameters := oneScenarioParameters()
	parameters.Repetitions = 5
	results := &fakeResults{events: &events}
	registry := &fakeRegistry{completed: make(map[runner.ScenarioMetadata]struct{})}
	for repetition := 1; repetition <= 3; repetition++ {
		registry.completed[runner.ScenarioMetadata{
			Events: 1, Lambda: 0.5, Duration: 1, IntegrationProcesses: 1, Consumers: 1, Repetition: repetition,
		}] = struct{}{}
	}
	suite := validSuite(&events)
	suite.Results = results
	suite.Registry = registry

	if err := suite.Run(parameters); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(results.initialized) != 2 {
		t.Fatalf("initialized scenarios = %d, want 2", len(results.initialized))
	}
	for _, scenario := range results.initialized {
		if scenario.Repetition != 4 && scenario.Repetition != 5 {
			t.Fatalf("executed completed repetition %d", scenario.Repetition)
		}
	}
}

func TestSuiteAllCompletedSkipsDatabaseAndInfrastructure(t *testing.T) {
	var events []string
	exporter := &fakeExporter{events: &events}
	registry := &fakeRegistry{completed: map[runner.ScenarioMetadata]struct{}{
		{Events: 1, Lambda: 0.5, Duration: 1, IntegrationProcesses: 1, Consumers: 1, Repetition: 1}: {},
	}}
	suite := validSuite(&events)
	suite.Exporter = exporter
	suite.Registry = registry

	if err := suite.Run(oneScenarioParameters()); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if exporter.validations != 0 {
		t.Fatalf("database validations = %d, want 0", exporter.validations)
	}
	if len(events) != 0 {
		t.Fatalf("lifecycle events = %v, want none", events)
	}
}

func TestSuiteRegistersEverySuccessfulExport(t *testing.T) {
	tests := []struct {
		name      string
		exportErr error
		wantMarks int
	}{
		{name: "successful export", wantMarks: 1},
		{name: "export failure", exportErr: errors.New("export failed")},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			var events []string
			registry := &fakeRegistry{}
			suite := validSuite(&events)
			suite.Exporter = &fakeExporter{events: &events, exportErr: test.exportErr}
			suite.Results = &fakeResults{events: &events}
			suite.Registry = registry

			if err := suite.Run(oneScenarioParameters()); err != nil {
				t.Fatalf("Run() error = %v", err)
			}
			if len(registry.marks) != test.wantMarks {
				t.Fatalf("registry marks = %d, want %d", len(registry.marks), test.wantMarks)
			}
		})
	}
}

func TestSuiteRegistryWriteFailureAbortsBeforeFinalReset(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Registry = &fakeRegistry{markErr: errors.New("disk full")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "record successful scenario") {
		t.Fatalf("Run() error = %v", err)
	}
	want := []string{"initialize", "reset", "consumers", "run", "stop", "export"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteMalformedRegistryAbortsBeforeDatabaseAndReset(t *testing.T) {
	var events []string
	exporter := &fakeExporter{events: &events}
	suite := validSuite(&events)
	suite.Exporter = exporter
	suite.Registry = &fakeRegistry{loadErr: errors.New("invalid JSON")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "load successful scenarios") {
		t.Fatalf("Run() error = %v", err)
	}
	if exporter.validations != 0 || len(events) != 0 {
		t.Fatalf("validation/events = %d/%v, want 0/none", exporter.validations, events)
	}
}

func TestSuiteSuccessfulExportRegistersScenario(t *testing.T) {
	var events []string
	registry := &fakeRegistry{}
	suite := validSuite(&events)
	suite.Registry = registry

	if err := suite.Run(oneScenarioParameters()); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if len(registry.marks) != 1 {
		t.Fatalf("registry marks = %d, want 1", len(registry.marks))
	}
}

func validSuite(events *[]string) Suite {
	return Suite{
		Client:         fakeAPI{events: events},
		Infrastructure: &fakeInfrastructure{events: events},
		Executor:       fakeExecutor{events: events},
		Exporter:       &fakeExporter{events: events},
		Results:        &fakeResults{events: events},
		Registry:       &fakeRegistry{},
		Sleep:          func(time.Duration) {},
		Random:         rand.New(rand.NewSource(1)),
		Logf:           func(string, ...any) {},
	}
}

func oneScenarioParameters() config.Parameters {
	return config.Parameters{
		Events:               []int{1},
		IntegrationProcesses: []int{1},
		Consumers:            []int{1},
		Lambda:               0.5,
		Duration:             1,
		Repetitions:          1,
	}
}
