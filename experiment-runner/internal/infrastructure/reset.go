package infrastructure

import (
	"fmt"
	"time"
)

const (
	fabricAddress   = "200.17.87.154:22"
	rabbitMQAddress = "200.17.87.130:22"
	postgresAddress = "200.17.87.134:22"
	apiAddress      = "200.17.87.137:22"
)

type ResetManager struct {
	SSH   CommandRunner
	Sleep func(time.Duration)
}

type CommandRunner interface {
	Run(address string, commands ...string) error
}

func NewResetManager() *ResetManager {
	return &ResetManager{
		SSH:   NewSSHClient(),
		Sleep: time.Sleep,
	}
}

func (m *ResetManager) Reset() error {
	if m.SSH == nil {
		return fmt.Errorf("SSH command runner is required")
	}
	if m.Sleep == nil {
		return fmt.Errorf("sleep function is required")
	}

	steps := []struct {
		name     string
		address  string
		commands []string
	}{
		{
			name:    "Hyperledger Fabric",
			address: fabricAddress,
			commands: []string{
				"cd /home/monitor/app && fno --config network-with-chaincode.yml network down",
				"rm -rf /home/monitor/app/output/network-with-chaincode",
				"cp -a /home/monitor/app/baseline/network-with-chaincode /home/monitor/app/output/network-with-chaincode",
				"cd /home/monitor/app && fno --config network-with-chaincode.yml network up",
			},
		},
		{
			name:    "RabbitMQ",
			address: rabbitMQAddress,
			commands: []string{
				"cd /home/monitor/app && docker compose down",
				"cd /home/monitor/app && rm -rf volumes/rabbitmq",
				"cd /home/monitor/app && cp -a volumes/baseline volumes/rabbitmq",
				"cd /home/monitor/app && docker compose up --build -d",
			},
		},
		{
			name:    "PostgreSQL",
			address: postgresAddress,
			commands: []string{
				"cd /home/monitor/app && docker compose -f network.yml -f postgres.yml down",
				"cd /home/monitor/app && rm -rf volumes/postgres/data",
				"cd /home/monitor/app && cp -a volumes/postgres/baseline volumes/postgres/data",
				"cd /home/monitor/app && docker compose -f network.yml -f postgres.yml up --build -d",
			},
		},
	}

	for _, step := range steps {
		if err := m.SSH.Run(step.address, step.commands...); err != nil {
			return fmt.Errorf("reset %s: %w", step.name, err)
		}
	}

	m.Sleep(20 * time.Second)

	if err := m.SSH.Run(apiAddress,
		"cd /var/www/app/api && docker compose -f api.yml -f node-exporter.yml -f nginx-exporter.yml down",
		"cd /var/www/app/api && docker compose -f api.yml -f node-exporter.yml -f nginx-exporter.yml up --build -d",
	); err != nil {
		return fmt.Errorf("reset API: %w", err)
	}

	m.Sleep(30 * time.Second)
	return nil
}
