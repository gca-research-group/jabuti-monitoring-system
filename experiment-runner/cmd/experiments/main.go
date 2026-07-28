package main

import (
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/report"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func main() {
	env := config.LoadEnv()
	client := api.NewClient(env.BaseURL)
	token := env.ApiKey
	parameters, err := config.LoadParameters()

	if err != nil {
		log.Fatalf("failed to load parameters: %v", err)
	}

	scenarios := runner.GenerateScenarios(*parameters)

	now := time.Now()

	if err := report.SaveScenariosToCSV(scenarios, now.Format("200601021504.csv")); err != nil {
		log.Fatalf("failed to save scenarios to CSV: %v", err)
	}

	var wg sync.WaitGroup

	for index, scenario := range scenarios {

		runner.HyperledgerFabric()
		runner.RabbitMQ()
		runner.Postgres()
		time.Sleep(20 * time.Second)
		runner.Api()
		time.Sleep(30 * time.Second)

		if err := client.SetUpConsumers(token, scenario.Consumers); err != nil {
			log.Printf("failed to set up consumers for execution %d: %v", index, err)
			return
		}

		time.Sleep(10 * time.Second)

		for i := 0; i < scenario.IntegrationProcesses; i++ {
			wg.Add(1)
			go runner.RunParallelScenario(&wg, client, env, scenario, token, i+1)
		}

		wg.Wait()
		fmt.Println("all scenarios completed")

		if err := client.StopProcessing(token); err != nil {
			log.Printf("failed to stop benchmark for execution %d: %v", index, err)
			return
		}
	}

	if err := client.StartProcessing(token); err != nil {
		log.Printf("failed to start queues %v", err)
		return
	}
}
