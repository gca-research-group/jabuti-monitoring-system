package experiment

import (
	"context"
	"errors"
	"fmt"
	"log"
	"math/rand"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/monitoring"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type APIClient interface {
	SetUpConsumers(token string, quantity int) error
	StopRabbitMQ(token string) error
	ExecuteSmartContract(token string, message api.SmartContractMessage) error
}

type Infrastructure interface {
	Reset() error
}

type ResultExporter interface {
	Validate(ctx context.Context) error
	Export(ctx context.Context, scenario runner.Scenario, destination string) error
}

type ScenarioExecutor interface {
	Prepare(scenario runner.Scenario) error
	RunPrepared(scenario runner.Scenario) error
}

type ExperimentResults interface {
	Initialize(scenarios []runner.Scenario) error
	Destination(scenario runner.Scenario) string
	ResourceDestination(scenario runner.Scenario) string
	QueueDestination(scenario runner.Scenario) string
}

type QueueFinalizer interface {
	StopConsumers() error
	CollectAndSave(context.Context, runner.Scenario, string) error
}

type SuccessRegistry interface {
	Load() error
	Contains(metadata runner.ScenarioMetadata) bool
	MarkSuccessful(metadata runner.ScenarioMetadata) error
}

type ResourceMonitor interface {
	Prepare(context.Context, runner.Scenario, string) (monitoring.PreparedSession, error)
}

type Suite struct {
	Queues         QueueFinalizer
	Monitor        ResourceMonitor
	Client         APIClient
	Infrastructure Infrastructure
	Executor       ScenarioExecutor
	Exporter       ResultExporter
	Results        ExperimentResults
	Registry       SuccessRegistry
	Token          string
	Sleep          func(duration time.Duration)
	Random         *rand.Rand
	Logf           func(string, ...any)
	Now            func() time.Time
}

func (s *Suite) Run(parameters config.Parameters) error {
	if err := s.validate(); err != nil {
		return err
	}
	if err := parameters.ValidateTiming(); err != nil {
		return err
	}
	pending, skipped, err := s.prepare(parameters)
	if err != nil {
		return err
	}
	if len(pending) == 0 {
		s.Logf("experiment suite completed: executed=0 skipped=%d", skipped)
		return nil
	}
	ctx := context.Background()
	for index, scenario := range pending {
		if err := s.runScenario(ctx, scenario, index, len(pending)); err != nil {
			return err
		}
	}
	if err := s.Infrastructure.Reset(); err != nil {
		return fmt.Errorf("final infrastructure reset: %w", err)
	}
	s.Logf("experiment suite completed: executed=%d skipped=%d", len(pending), skipped)
	return nil
}

func (s *Suite) prepare(parameters config.Parameters) ([]runner.Scenario, int, error) {
	scenarios := runner.GenerateScenarios(parameters, s.Random)
	if err := s.Registry.Load(); err != nil {
		return nil, 0, fmt.Errorf("load successful scenarios: %w", err)
	}

	pending := make([]runner.Scenario, 0, len(scenarios))
	for _, scenario := range scenarios {
		if !s.Registry.Contains(scenario.Metadata()) {
			pending = append(pending, scenario)
		}
	}
	skipped := len(scenarios) - len(pending)
	s.Logf("experiment schedule: requested=%d skipped=%d pending=%d", len(scenarios), skipped, len(pending))
	if len(pending) == 0 {
		return pending, skipped, nil
	}
	ctx := context.Background()
	if err := s.Exporter.Validate(ctx); err != nil {
		return nil, skipped, err
	}

	if err := s.Results.Initialize(pending); err != nil {
		return nil, skipped, fmt.Errorf("initialize experiment results: %w", err)
	}

	return pending, skipped, nil
}

func (s *Suite) runScenario(ctx context.Context, scenario runner.Scenario, index, total int) error {
	s.Logf(
		"preparing scenario %d/%d: scenario_id=%s repetition=%d events_per_second=%d duration=%dms integration_processes=%d consumers=%d",
		index+1,
		total,
		scenario.ScenarioID,
		scenario.Repetition,
		scenario.Events,
		scenario.Duration,
		scenario.IntegrationProcesses,
		scenario.Consumers,
	)

	if err := s.Infrastructure.Reset(); err != nil {
		return fmt.Errorf("reset infrastructure before scenario %d: %w", index+1, err)
	}

	if err := s.Client.SetUpConsumers(s.Token, scenario.Consumers); err != nil {
		return fmt.Errorf("set up consumers for scenario %d: %w", index+1, err)
	}

	s.Sleep(10 * time.Second)
	if err := s.Executor.Prepare(scenario); err != nil {
		return fmt.Errorf("prepare workload: %w", err)
	}
	prepared, err := s.startMonitoring(ctx, scenario)
	if err != nil {
		return err
	}

	now := time.Now()
	if s.Now != nil {
		now = s.Now()
	}
	scenario.Window = runner.NewRunWindow(now, scenario.WarmupDuration, scenario.Duration)
	var session monitoring.Session
	if prepared != nil {
		session = prepared.Begin(scenario.Window)
	}
	monitoringErr := s.executeScenario(scenario, session)
	stopErr := s.Client.StopRabbitMQ(s.Token)
	if stopErr != nil {
		stopErr = fmt.Errorf("stop RabbitMQ listeners: %w", stopErr)
	}
	workerErr := s.Queues.StopConsumers()
	finalizationErr := errors.Join(stopErr, workerErr)
	if finalizationErr == nil {
		finalizationErr = s.Queues.CollectAndSave(ctx, scenario, s.Results.QueueDestination(scenario))
		if finalizationErr != nil {
			finalizationErr = fmt.Errorf("collect and save queue snapshot: %w", finalizationErr)
		}
	}
	monitoringErr = errors.Join(monitoringErr, finalizationErr)
	return s.exportScenario(ctx, scenario, index, total, monitoringErr)
}

func (s *Suite) startMonitoring(ctx context.Context, scenario runner.Scenario) (monitoring.PreparedSession, error) {
	if s.Monitor == nil {
		return nil, nil
	}

	session, err := s.Monitor.Prepare(ctx, scenario, s.Results.ResourceDestination(scenario))

	if err != nil {
		return nil, fmt.Errorf("start resource monitoring: %w", err)
	}

	return session, nil
}

func (s *Suite) executeScenario(scenario runner.Scenario, session monitoring.Session) (monitoringErr error) {
	if session != nil {
		defer func() {
			summary, err := session.Stop(context.Background())
			monitoringErr = errors.Join(monitoringErr, err)
			s.logMonitoringSummary(summary)
		}()
	}

	monitoringErr = s.Executor.RunPrepared(scenario)

	return monitoringErr
}

func (s *Suite) logMonitoringSummary(summary monitoring.Summary) {
	for component, counts := range summary.Components {
		s.Logf("resource monitoring component=%s samples=%d errors=%d partial=%d output=%s", component, counts.Samples, counts.Errors, counts.Partial, summary.Destination)
		if counts.Errors > 0 {
			s.Logf("warning: incomplete resource coverage for %s", component)
		}
	}
}

func (s *Suite) exportScenario(ctx context.Context, scenario runner.Scenario, index, total int, monitoringErr error) error {
	destination := s.Results.Destination(scenario)
	exportErr := s.Exporter.Export(ctx, scenario, destination)

	if exportErr != nil {
		s.Logf("failed to export scenario %s repetition %d: %v", scenario.ScenarioID, scenario.Repetition, exportErr)
	}

	if monitoringErr != nil {
		return fmt.Errorf("execute or finalize scenario: %w", errors.Join(monitoringErr, exportErr))
	}

	if exportErr != nil {
		return exportErr
	}

	if exportErr == nil {
		if err := s.Registry.MarkSuccessful(scenario.Metadata()); err != nil {
			return fmt.Errorf("record successful scenario %s repetition %d: %w", scenario.ScenarioID, scenario.Repetition, err)
		}

		s.Logf(
			"completed scenario %d/%d: scenario_id=%s repetition=%d results=%s",
			index+1,
			total,
			scenario.ScenarioID,
			scenario.Repetition,
			destination,
		)
	}

	return nil
}

func (s *Suite) validate() error {
	switch {
	case s.Queues == nil:
		return fmt.Errorf("queue finalizer is required")
	case s.Client == nil:
		return fmt.Errorf("API client is required")
	case s.Infrastructure == nil:
		return fmt.Errorf("infrastructure resetter is required")
	case s.Executor == nil:
		return fmt.Errorf("scenario executor is required")
	case s.Exporter == nil:
		return fmt.Errorf("result exporter is required")
	case s.Results == nil:
		return fmt.Errorf("experiment results store is required")
	case s.Registry == nil:
		return fmt.Errorf("successful scenario registry is required")
	case s.Sleep == nil:
		return fmt.Errorf("sleep function is required")
	case s.Random == nil:
		return fmt.Errorf("random source is required")
	}
	if s.Logf == nil {
		s.Logf = log.Printf
	}
	return nil
}
