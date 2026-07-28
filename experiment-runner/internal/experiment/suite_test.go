package experiment

import (
	"errors"
	"math/rand"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type fakeAPI struct {
	events     *[]string
	setUpError error
	stopError  error
}

func (f fakeAPI) SetUpConsumers(_ string, quantity int) error {
	*f.events = append(*f.events, "consumers")
	return f.setUpError
}

func (f fakeAPI) StopProcessing(string) error {
	*f.events = append(*f.events, "stop")
	return f.stopError
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

type fakeReport struct {
	events   *[]string
	filename string
}

func (f *fakeReport) Save(_ []runner.Scenario, filename string) error {
	f.filename = filename
	*f.events = append(*f.events, "report")
	return nil
}

type fakeExecutor struct {
	events *[]string
}

func (f fakeExecutor) Run(runner.Scenario) {
	*f.events = append(*f.events, "run")
}

func TestSuiteRunsScenarioLifecycleAndFinalReset(t *testing.T) {
	var events []string
	infrastructure := &fakeInfrastructure{events: &events}
	report := &fakeReport{events: &events}
	suite := Suite{
		Client:         fakeAPI{events: &events},
		Infrastructure: infrastructure,
		Report:         report,
		Executor:       fakeExecutor{events: &events},
		Token:          "token",
		Sleep: func(duration time.Duration) {
			if duration != 10*time.Second {
				t.Errorf("sleep = %v, want 10s", duration)
			}
			events = append(events, "sleep")
		},
		Now:    func() time.Time { return time.Date(2026, 7, 28, 12, 34, 0, 0, time.UTC) },
		Random: rand.New(rand.NewSource(1)),
	}

	err := suite.Run(config.Parameters{
		Events:               []int{1},
		IntegrationProcesses: []int{1},
		Consumers:            []int{1},
		Lambda:               0.5,
		Duration:             1,
		Repetitions:          1,
	})
	if err != nil {
		t.Fatalf("Run() error = %v", err)
	}

	want := []string{"report", "reset", "consumers", "sleep", "run", "stop", "reset"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
	if report.filename != "202607281234.csv" {
		t.Fatalf("filename = %q, want %q", report.filename, "202607281234.csv")
	}
}

func TestSuiteAbortsOnInitialResetFailure(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Infrastructure = &fakeInfrastructure{events: &events, failAt: 1}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "reset infrastructure before scenario 1") {
		t.Fatalf("Run() error = %v", err)
	}
	want := []string{"report", "reset"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteReportsFinalResetFailure(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Infrastructure = &fakeInfrastructure{events: &events, failAt: 2}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "final infrastructure reset") {
		t.Fatalf("Run() error = %v", err)
	}
}

func TestSuiteAbortsWhenConsumerSetupFails(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Client = fakeAPI{events: &events, setUpError: errors.New("setup failed")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "set up consumers for scenario 1") {
		t.Fatalf("Run() error = %v", err)
	}

	want := []string{"report", "reset", "consumers"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func TestSuiteAbortsWhenStopProcessingFails(t *testing.T) {
	var events []string
	suite := validSuite(&events)
	suite.Client = fakeAPI{events: &events, stopError: errors.New("stop failed")}

	err := suite.Run(oneScenarioParameters())
	if err == nil || !strings.Contains(err.Error(), "stop processing after scenario 1") {
		t.Fatalf("Run() error = %v", err)
	}

	want := []string{"report", "reset", "consumers", "run", "stop"}
	if !reflect.DeepEqual(events, want) {
		t.Fatalf("events = %v, want %v", events, want)
	}
}

func validSuite(events *[]string) Suite {
	return Suite{
		Client:         fakeAPI{events: events},
		Infrastructure: &fakeInfrastructure{events: events},
		Report:         &fakeReport{events: events},
		Executor:       fakeExecutor{events: events},
		Sleep:          func(time.Duration) {},
		Now:            func() time.Time { return time.Unix(0, 0) },
		Random:         rand.New(rand.NewSource(1)),
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
