package main

import (
	"fmt"
	"log"
	"log/slog"
	"math/rand"
	"os"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/experiment"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/exporter"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/infrastructure"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/logger"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func main() {
	if err := logger.Setup(); err != nil {
		log.Fatalf("configure logging: %v", err)
	}

	if err := run(); err != nil {
		slog.Error("experiment runner failed", "error", err)
		os.Exit(1)
	}
}

func run() error {
	env, err := config.LoadEnv()
	if err != nil {
		return fmt.Errorf("load environment: %w", err)
	}
	client := api.NewClient(env.BaseURL, api.HTTPConfig{
		MaxIdleConns:          env.HTTPMaxIdleConns,
		MaxIdleConnsPerHost:   env.HTTPMaxIdleConnsPerHost,
		IdleConnTimeout:       env.HTTPIdleConnTimeout,
		ResponseHeaderTimeout: env.HTTPResponseHeaderTimeout,
		RequestTimeout:        env.HTTPRequestTimeout,
	})
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
		Infrastructure: infrastructure.NewResetManager(client, env, client),
		Executor:       executor,
		Exporter:       resultExporter,
		Results:        &experiment.Dataset{OutputRoot: env.ExperimentOutputDir},
		Registry:       &experiment.JSONSuccessRegistry{OutputRoot: env.ExperimentOutputDir},
		Token:          env.ApiKey,
		Sleep:          time.Sleep,
		Random:         random,
	}

	return suite.Run(*parameters)
}
