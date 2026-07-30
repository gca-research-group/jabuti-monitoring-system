package main

import (
	"fmt"
	"log"
	"math/rand"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/experiment"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/exporter"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/infrastructure"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func main() {
	if err := run(); err != nil {
		log.Fatal(err)
	}
}

func run() error {
	env := config.LoadEnv()
	client := api.NewClient(env.BaseURL)
	parameters, err := config.LoadParameters()
	if err != nil {
		return fmt.Errorf("load parameters: %w", err)
	}
	random := rand.New(rand.NewSource(time.Now().UnixNano()))
	executor := runner.NewExecutor(client, env, env.ApiKey, random)
	if err := executor.Validate(); err != nil {
		return fmt.Errorf("configure scenario executor: %w", err)
	}

	resultExporter := &exporter.ParquetExporter{DatabaseURL: env.DatabaseURL}
	defer resultExporter.Close()
	suite := experiment.Suite{
		Client:         client,
		Infrastructure: infrastructure.NewResetManager(client, env),
		Executor:       executor,
		Exporter:       resultExporter,
		Results:        &experiment.Dataset{OutputRoot: env.ExperimentOutputDir, Now: time.Now},
		Registry:       &experiment.JSONSuccessRegistry{OutputRoot: env.ExperimentOutputDir},
		Token:          env.ApiKey,
		Sleep:          time.Sleep,
		Now:            time.Now,
		Random:         random,
	}

	return suite.Run(*parameters)
}
