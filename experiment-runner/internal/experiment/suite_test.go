package experiment

import (
	"context"
	"errors"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/monitoring"
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

func (f *fakeInfrastructure) Reset(consumers int) error {
	if consumers <= 0 {
		return errors.New("consumer count must come from the scenario")
	}
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

func (f fakeExecutor) RunPrepared(runner.Scenario) error {
	*f.events = append(*f.events, "run")
	return nil
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

func (f *fakeResults) ResourceDestination(runner.Scenario) string { return "resources.parquet" }

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

func TestSuiteExportsBeforeNextReset(t *testing.T) {
	var events []string
	suite := validSuite(&events)

	err := suite.Run(oneScenarioParameters())
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}

	want := []string{"initialize", "reset", "consumers", "run", "stop", "worker-stop", "queues", "export", "reset"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteExportFailureAbortsWithoutRegistration(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	exportErr := errors.New("write failed")
	suite.Exporter = &fakeExporter{events: &events, exportErr: exportErr}
	if err := suite.Run(oneScenarioParameters()); !errors.Is(err, exportErr) {
		t.Fatalf("error = %v", err)
	}
	if len(suite.Registry.(*fakeRegistry).marks) != 0 {
		t.Fatal("failed export registered")
	}
	if suite.Infrastructure.(*fakeInfrastructure).calls != 1 {
		t.Fatal("reset after failed export")
	}
}
func TestSuiteStopFailurePreservesInfrastructure(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	failure := errors.New("stop failed")
	suite.Client = fakeAPI{events: &events, stopError: failure}
	if err := suite.Run(oneScenarioParameters()); !errors.Is(err, failure) {
		t.Fatal(err)
	}
	want := []string{"initialize", "reset", "consumers", "run", "stop", "worker-stop", "export"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
	if len(suite.Registry.(*fakeRegistry).marks) != 0 {
		t.Fatal("failed stop registered")
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

func TestSuiteSkipsCompletedRepetitions(t *testing.T) {
	var events []string
	parameters := oneScenarioParameters()
	parameters.Repetitions = 5
	results := &fakeResults{events: &events}
	registry := &fakeRegistry{completed: make(map[runner.ScenarioMetadata]struct{})}
	for repetition := 1; repetition <= 3; repetition++ {
		registry.completed[runner.ScenarioMetadata{
			TimingProtocolVersion: 3, Events: 1, Lambda: 0.5, Duration: 1000, IntegrationProcesses: 1, Consumers: 1, Repetition: repetition,
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
		if scenario.Repetition < 4 || scenario.Repetition > 5 {
			t.Fatalf("executed completed repetition %d", scenario.Repetition)
		}
	}
}

func TestSuiteCompletedRegistryAvoidsDatabaseAndInfrastructure(t *testing.T) {
	var events []string
	exporter := &fakeExporter{events: &events}
	registry := &fakeRegistry{completed: map[runner.ScenarioMetadata]struct{}{
		{TimingProtocolVersion: 3, Events: 1, Lambda: 0.5, Duration: 1000, IntegrationProcesses: 1, Consumers: 1, Repetition: 1}: {},
	}}
	suite := validSuite(&events)
	suite.Exporter = exporter
	suite.Registry = registry
	suite.Monitor = fakeMonitor{events: &events}

	if err := suite.Run(oneScenarioParameters()); err != nil {
		t.Fatalf("Run() error = %v", err)
	}
	if exporter.validations != 0 {
		t.Fatalf("database validations = %d, want 0", exporter.validations)
	}
	if len(events) != 0 || len(registry.marks) != 0 {
		t.Fatalf("lifecycle events/marks = %v/%v, want none", events, registry.marks)
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

			if err := suite.Run(oneScenarioParameters()); !errors.Is(err, test.exportErr) {
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
	want := []string{"initialize", "reset", "consumers", "run", "stop", "worker-stop", "queues", "export"}
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
		Queues:         fakeQueues{events: events},
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
		Duration:             1000,
		Repetitions:          1,
	}
}

type fakeMonitor struct {
	events            *[]string
	startErr, stopErr error
	window            *runner.RunWindow
}

type panicExecutor struct{ events *[]string }

func (e panicExecutor) RunPrepared(runner.Scenario) error {
	*e.events = append(*e.events, "run")
	panic("executor panic")
}

func TestSuiteStopsMonitoringDuringExecutorPanic(t *testing.T) {
	var events []string
	s := validSuite(&events)
	s.Monitor = fakeMonitor{events: &events}
	s.Executor = panicExecutor{events: &events}
	func() {
		defer func() {
			if got := recover(); got != "executor panic" {
				t.Fatalf("panic = %v", got)
			}
		}()
		_ = s.Run(oneScenarioParameters())
	}()
	want := []string{"initialize", "reset", "consumers", "monitor-start", "run", "monitor-stop"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
	if len(s.Registry.(*fakeRegistry).marks) != 0 {
		t.Fatal("panicked scenario registered")
	}
}

func TestSuiteJoinsMonitoringAndExportFailures(t *testing.T) {
	var events []string
	s := validSuite(&events)
	stopErr, exportErr := errors.New("stop failed"), errors.New("export failed")
	s.Monitor = fakeMonitor{events: &events, stopErr: stopErr}
	s.Exporter = &fakeExporter{events: &events, exportErr: exportErr}
	err := s.Run(oneScenarioParameters())
	if !errors.Is(err, stopErr) || !errors.Is(err, exportErr) {
		t.Fatalf("error = %v, want both failures", err)
	}
	want := []string{"initialize", "reset", "consumers", "monitor-start", "run", "monitor-stop", "stop", "worker-stop", "queues", "export"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
	if len(s.Registry.(*fakeRegistry).marks) != 0 {
		t.Fatal("failed scenario registered")
	}
}

func (m fakeMonitor) Prepare(context.Context, runner.Scenario, string) (monitoring.PreparedSession, error) {
	*m.events = append(*m.events, "monitor-start")
	if m.startErr != nil {
		return nil, m.startErr
	}
	return m, nil
}
func (m fakeMonitor) Stop(context.Context) (monitoring.Summary, error) {
	*m.events = append(*m.events, "monitor-stop")
	return monitoring.Summary{}, m.stopErr
}
func TestSuiteMonitoringLifecycle(t *testing.T) {
	for _, mode := range []string{"ok", "start failure", "stop failure", "export failure"} {
		t.Run(mode, func(t *testing.T) {
			var events []string
			s := validSuite(&events)
			m := fakeMonitor{events: &events}
			registry := &fakeRegistry{}
			s.Registry = registry
			if mode == "start failure" {
				m.startErr = errors.New("preflight")
			}
			if mode == "stop failure" {
				m.stopErr = errors.New("publication")
			}
			if mode == "export failure" {
				s.Exporter = &fakeExporter{events: &events, exportErr: errors.New("export")}
			}
			s.Monitor = m
			err := s.Run(oneScenarioParameters())
			if (err == nil) != (mode == "ok") {
				t.Fatalf("error: %v", err)
			}
			want := []string{"initialize", "reset", "consumers", "monitor-start"}
			if mode != "start failure" {
				want = append(want, "run", "monitor-stop", "stop", "worker-stop", "queues", "export")
			}
			if mode == "ok" {
				want = append(want, "reset")
			}
			if !reflect.DeepEqual(events, want) {
				t.Fatalf("events %v want %v", events, want)
			}
			if (len(registry.marks) == 1) != (mode == "ok") {
				t.Fatal("incorrect completion")
			}
		})
	}
}

func (f fakeExecutor) Prepare(runner.Scenario) error  { return nil }
func (e panicExecutor) Prepare(runner.Scenario) error { return nil }
func (m fakeMonitor) Begin(w runner.RunWindow) monitoring.Session {
	if m.window != nil {
		*m.window = w
	}
	return m
}

type errorExecutor struct {
	fakeExecutor
	prepareErr, runErr error
}

func (e errorExecutor) Prepare(runner.Scenario) error { return e.prepareErr }
func (e errorExecutor) RunPrepared(s runner.Scenario) error {
	_ = e.fakeExecutor.RunPrepared(s)
	return e.runErr
}
func TestSuiteTimingFailuresPreventRegistration(t *testing.T) {
	for _, phase := range []string{"prepare", "run"} {
		t.Run(phase, func(t *testing.T) {
			var events []string
			suite := validSuite(&events)
			failure := errors.New("timing failure")
			e := errorExecutor{fakeExecutor: fakeExecutor{events: &events}}
			if phase == "prepare" {
				e.prepareErr = failure
			} else {
				e.runErr = failure
			}
			suite.Executor = e
			suite.Monitor = fakeMonitor{events: &events}
			if err := suite.Run(oneScenarioParameters()); !errors.Is(err, failure) {
				t.Fatal(err)
			}
			if len(suite.Registry.(*fakeRegistry).marks) != 0 {
				t.Fatal("failed timing registered")
			}
			joined := strings.Join(events, ",")
			if phase == "run" && !strings.Contains(joined, "monitor-stop,stop,worker-stop,queues,export") {
				t.Fatalf("failed run did not stop/export: %v", events)
			}
			if phase == "prepare" && strings.Contains(joined, "monitor-start") {
				t.Fatal("monitor started after failed preparation")
			}
		})
	}
}

func (f *fakeResults) QueueDestination(runner.Scenario) string { return "queues.parquet" }

type fakeQueues struct {
	events              *[]string
	stopErr, collectErr error
}

func (f fakeQueues) StopConsumers() error {
	*f.events = append(*f.events, "worker-stop")
	return f.stopErr
}
func (f fakeQueues) CollectAndSave(context.Context, runner.Scenario, string) error {
	*f.events = append(*f.events, "queues")
	return f.collectErr
}
func TestSuiteQueueFailuresStillExportAndPreventReset(t *testing.T) {
	for _, phase := range []string{"worker", "collect"} {
		t.Run(phase, func(t *testing.T) {
			var events []string
			suite := validSuite(&events)
			failure, exportErr := errors.New("queue failure"), errors.New("export failure")
			q := fakeQueues{events: &events}
			if phase == "worker" {
				q.stopErr = failure
			} else {
				q.collectErr = failure
			}
			suite.Queues = q
			suite.Exporter = &fakeExporter{events: &events, exportErr: exportErr}
			err := suite.Run(oneScenarioParameters())
			if !errors.Is(err, failure) || !errors.Is(err, exportErr) {
				t.Fatal(err)
			}
			if suite.Infrastructure.(*fakeInfrastructure).calls != 1 || len(suite.Registry.(*fakeRegistry).marks) != 0 {
				t.Fatal("failure reset or registered")
			}
			if events[len(events)-1] != "export" {
				t.Fatal(events)
			}
			if phase == "worker" && strings.Contains(strings.Join(events, ","), "queues") {
				t.Fatal("collected after shutdown failure")
			}
		})
	}
}
func TestSuiteFinalizesEveryRepetition(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	parameters := oneScenarioParameters()
	parameters.Repetitions = 2
	if err := suite.Run(parameters); err != nil {
		t.Fatal(err)
	}
	want := []string{"initialize", "reset", "consumers", "run", "stop", "worker-stop", "queues", "export", "reset", "consumers", "run", "stop", "worker-stop", "queues", "export", "reset"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("%v", events)
	}
}
