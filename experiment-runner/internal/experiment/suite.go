package experiment

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

type APIClient interface {
	SetUpConsumers(token string, quantity int) error
	StopProcessing(token string) error
}

type Infrastructure interface {
	Reset() error
}

type ResultExporter interface {
	Validate(ctx context.Context) error
	Export(ctx context.Context, scenario runner.Scenario, destination string) (int64, error)
}

type ScenarioExecutor interface {
	Run(scenario runner.Scenario) runner.ExecutionSummary
}

type ExperimentResults interface {
	Initialize(scenarios []runner.Scenario) error
	Destination(scenario runner.Scenario) string
}

type SuccessRegistry interface {
	Load() error
	Contains(metadata runner.ScenarioMetadata) bool
	MarkSuccessful(metadata runner.ScenarioMetadata) error
}

type Suite struct {
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
}

func (s *Suite) Run(parameters config.Parameters) error {
	if err := s.validate(); err != nil {
		return err
	}

	scenarios := runner.GenerateScenarios(parameters, s.Random)
	if err := s.Registry.Load(); err != nil {
		return fmt.Errorf("load successful scenarios: %w", err)
	}

	pending := make([]runner.Scenario, 0, len(scenarios))
	for _, scenario := range scenarios {
		if s.Registry.Contains(scenario.Metadata()) {
			s.Logf("skipping completed scenario repetition: events=%d integration_processes=%d consumers=%d repetition=%d",
				scenario.Events, scenario.IntegrationProcesses, scenario.Consumers, scenario.Repetition)
			continue
		}
		pending = append(pending, scenario)
	}
	if len(pending) == 0 {
		s.Logf("all configured scenario repetitions have already completed successfully")
		return nil
	}

	ctx := context.Background()
	if err := s.Exporter.Validate(ctx); err != nil {
		return err
	}
	if err := s.Results.Initialize(pending); err != nil {
		return fmt.Errorf("initialize experiment results: %w", err)
	}

	for index, scenario := range pending {
		if err := s.Infrastructure.Reset(); err != nil {
			return fmt.Errorf("reset infrastructure before scenario %d: %w", index+1, err)
		}

		if err := s.Client.SetUpConsumers(s.Token, scenario.Consumers); err != nil {
			return fmt.Errorf("set up consumers for scenario %d: %w", index+1, err)
		}

		s.Sleep(10 * time.Second)
		summary := s.Executor.Run(scenario)

		if err := s.Client.StopProcessing(s.Token); err != nil {
			return fmt.Errorf("stop processing after scenario %d: %w", index+1, err)
		}

		rowCount, exportErr := s.Exporter.Export(ctx, scenario, s.Results.Destination(scenario))
		if exportErr != nil {
			s.Logf("failed to export scenario %s repetition %d: %v", scenario.ScenarioID, scenario.Repetition, exportErr)
		}
		if exportErr == nil && summary.FailedEvents == 0 && rowCount == expectedRows(scenario) {
			if err := s.Registry.MarkSuccessful(scenario.Metadata()); err != nil {
				return fmt.Errorf("record successful scenario %s repetition %d: %w", scenario.ScenarioID, scenario.Repetition, err)
			}
		}
	}

	if err := s.Infrastructure.Reset(); err != nil {
		return fmt.Errorf("final infrastructure reset: %w", err)
	}
	return nil
}

func (s *Suite) validate() error {
	switch {
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
