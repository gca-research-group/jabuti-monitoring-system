package experiment

import (
	"fmt"
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

type ScenarioReport interface {
	Save(scenarios []runner.Scenario, filename string) error
}

type ScenarioExecutor interface {
	Run(scenario runner.Scenario)
}

type Suite struct {
	Client         APIClient
	Infrastructure Infrastructure
	Report         ScenarioReport
	Executor       ScenarioExecutor
	Token          string
	Sleep          func(time.Duration)
	Now            func() time.Time
	Random         *rand.Rand
}

func (s *Suite) Run(parameters config.Parameters) error {
	if err := s.validate(); err != nil {
		return err
	}

	scenarios := runner.GenerateScenarios(parameters, s.Random)
	filename := s.Now().Format("200601021504.csv")
	if err := s.Report.Save(scenarios, filename); err != nil {
		return fmt.Errorf("save scenarios to CSV: %w", err)
	}

	for index, scenario := range scenarios {
		if err := s.Infrastructure.Reset(); err != nil {
			return fmt.Errorf("reset infrastructure before scenario %d: %w", index+1, err)
		}

		if err := s.Client.SetUpConsumers(s.Token, scenario.Consumers); err != nil {
			return fmt.Errorf("set up consumers for scenario %d: %w", index+1, err)
		}

		s.Sleep(10 * time.Second)
		s.Executor.Run(scenario)

		if err := s.Client.StopProcessing(s.Token); err != nil {
			return fmt.Errorf("stop processing after scenario %d: %w", index+1, err)
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
	case s.Report == nil:
		return fmt.Errorf("scenario report is required")
	case s.Executor == nil:
		return fmt.Errorf("scenario executor is required")
	case s.Sleep == nil:
		return fmt.Errorf("sleep function is required")
	case s.Now == nil:
		return fmt.Errorf("clock is required")
	case s.Random == nil:
		return fmt.Errorf("random source is required")
	default:
		return nil
	}
}
