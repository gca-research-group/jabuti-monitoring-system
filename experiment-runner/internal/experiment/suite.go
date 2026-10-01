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
	Run(scenario runner.Scenario)
}

type ExperimentResults interface {
	Initialize(scenarios []runner.Scenario) error
	Destination(scenario runner.Scenario) string
	ResourceDestination(scenario runner.Scenario) string
}

type SuccessRegistry interface {
	Load() error
	Contains(metadata runner.ScenarioMetadata) bool
	MarkSuccessful(metadata runner.ScenarioMetadata) error
}

type ResourceMonitor interface {
	Start(context.Context, runner.Scenario, string) (monitoring.Session, error)
}

type Suite struct {
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
		// if s.Registry.Contains(scenario.Metadata()) {
		// 	continue
		// }
		pending = append(pending, scenario)
	}
	skipped := 0
	//skipped := len(scenarios) - len(pending)
	// if skipped > 0 {
	// 	s.Logf("skipping %d previously completed scenario repetitions", skipped)
	// }
	// if len(pending) == 0 {
	// 	s.Logf("all configured scenario repetitions have already completed successfully")
	// 	return nil
	// }

	ctx := context.Background()
	if err := s.Exporter.Validate(ctx); err != nil {
		return err
	}
	if err := s.Results.Initialize(pending); err != nil {
		return fmt.Errorf("initialize experiment results: %w", err)
	}

	for index, scenario := range pending {
		s.Logf(
			"preparing scenario %d/%d: scenario_id=%s repetition=%d events_per_second=%d duration=%ds integration_processes=%d consumers=%d",
			index+1,
			len(pending),
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
		var session monitoring.Session
		if s.Monitor != nil {
			var err error
			session, err = s.Monitor.Start(ctx, scenario, s.Results.ResourceDestination(scenario))
			if err != nil {
				return fmt.Errorf("start resource monitoring: %w", err)
			}
		}
		var monitoringErr error
		func() {
			if session != nil {
				defer func() {
					summary, err := session.Stop(context.Background())
					monitoringErr = err
					for component, counts := range summary.Components {
						s.Logf("resource monitoring component=%s samples=%d errors=%d partial=%d output=%s", component, counts.Samples, counts.Errors, counts.Partial, summary.Destination)
						if counts.Errors > 0 {
							s.Logf("warning: incomplete resource coverage for %s", component)
						}
					}
				}()
			}
			s.Executor.Run(scenario)
		}()

		destination := s.Results.Destination(scenario)
		exportErr := s.Exporter.Export(ctx, scenario, destination)

		if exportErr != nil {
			s.Logf("failed to export scenario %s repetition %d: %v", scenario.ScenarioID, scenario.Repetition, exportErr)
		}

		if monitoringErr != nil {
			return fmt.Errorf("finalize resource monitoring: %w", errors.Join(monitoringErr, exportErr))
		}

		if s.Monitor != nil && exportErr != nil {
			return exportErr
		}

		if exportErr == nil {
			if err := s.Registry.MarkSuccessful(scenario.Metadata()); err != nil {
				return fmt.Errorf("record successful scenario %s repetition %d: %w", scenario.ScenarioID, scenario.Repetition, err)
			}

			s.Logf(
				"completed scenario %d/%d: scenario_id=%s repetition=%d results=%s",
				index+1,
				len(pending),
				scenario.ScenarioID,
				scenario.Repetition,
				destination,
			)
		}
	}

	if err := s.Infrastructure.Reset(); err != nil {
		return fmt.Errorf("final infrastructure reset: %w", err)
	}
	s.Logf("experiment suite completed: executed=%d skipped=%d", len(pending), skipped)
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
