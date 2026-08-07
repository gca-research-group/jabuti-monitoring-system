package infrastructure

import (
	"fmt"
	"strings"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

const (
	fabricAddress   = "200.17.87.154:22"
	rabbitMQAddress = "200.17.87.130:22"
	postgresAddress = "200.17.87.134:22"
	apiAddress      = "200.17.87.137:22"
)

type ResetManager struct {
	SSH       CommandRunner
	Registrar RegistrationClient
	Env       *config.Env
	Sleep     func(time.Duration)
	Client    *api.Client
}

type CommandRunner interface {
	Run(address string, commands ...string) error
	RunOutput(address, command string) ([]byte, error)
}

type RegistrationClient interface {
	RegisterBlockchain(token string, payload api.BlockchainRegistration) (string, error)
	RegisterSmartContract(token string, payload api.SmartContractRegistration) (string, error)
}

func NewResetManager(registrar RegistrationClient, env *config.Env, client *api.Client) *ResetManager {
	return &ResetManager{
		SSH:       NewSSHClient(),
		Registrar: registrar,
		Env:       env,
		Sleep:     time.Sleep,
		Client:    client,
	}
}

func (m *ResetManager) Reset() error {
	if m.SSH == nil {
		return fmt.Errorf("SSH command runner is required")
	}
	if m.Registrar == nil {
		return fmt.Errorf("registration client is required")
	}
	if m.Env == nil {
		return fmt.Errorf("environment configuration is required")
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
				"cd /home/monitor/app && docker compose -f rabbitmq.yml down",
				"cd /home/monitor/app && rm -rf volumes/rabbitmq",
				"cd /home/monitor/app && cp -a volumes/baseline volumes/rabbitmq",
				"cd /home/monitor/app && docker compose -f rabbitmq.yml up --build -d",
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
	return m.registerFabricResources()
}

func (m *ResetManager) registerFabricResources() error {
	caCrt, err := m.readFabricFile("CA certificate", m.Env.FabricCACertPath)
	if err != nil {
		return err
	}
	privateKey, err := m.readFabricFile("private key", m.Env.FabricPrivateKeyPath)
	if err != nil {
		return err
	}
	signCert, err := m.readFabricFile("signing certificate", m.Env.FabricSignCertPath)
	if err != nil {
		return err
	}

	blockchainID, err := m.Registrar.RegisterBlockchain(m.Env.ApiKey, api.BlockchainRegistration{
		Name:     "Hyperledger Fabric",
		Platform: "HYPERLEDGER_FABRIC",
		Parameters: api.BlockchainParameters{
			MSPID:         "Org1MSP",
			PeerEndpoint:  "200.17.87.154:7051",
			PeerHostAlias: "peer0.org1.network-with-chaincode.com",
			ChannelName:   "defaultchannel",
			SignCert:      signCert,
			KeyStore:      privateKey,
			CACrt:         caCrt,
		},
	})
	if err != nil {
		return fmt.Errorf("register blockchain: %w", err)
	}

	smartContractID, err := m.Registrar.RegisterSmartContract(m.Env.ApiKey, productSmartContractRegistration())
	if err != nil {
		return fmt.Errorf("register smart contract: %w", err)
	}

	m.Env.BlockchainID = blockchainID
	m.Env.SmartContractID = smartContractID

	return m.createProduct()
}

func (m *ResetManager) readFabricFile(name, path string) (string, error) {
	if strings.TrimSpace(path) == "" {
		return "", fmt.Errorf("read Fabric %s: path is required", name)
	}

	output, err := m.SSH.RunOutput(fabricAddress, "cat -- "+shellQuote(path))
	if err != nil {
		return "", fmt.Errorf("read Fabric %s: %w", name, err)
	}
	return string(output), nil
}

func shellQuote(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}

func productSmartContractRegistration() api.SmartContractRegistration {
	return api.SmartContractRegistration{
		Name:               "Product",
		BlockchainPlatform: "HYPERLEDGER_FABRIC",
		Clauses: []api.SmartContractClause{
			{
				Name: "QueryProductByID",
				ClauseArguments: []api.SmartContractClauseArgument{
					{Name: "id"},
				},
			},
			{
				Name: "CreateProduct",
				ClauseArguments: []api.SmartContractClauseArgument{
					{Name: "id"},
					{Name: "name"},
					{Name: "description"},
					{Name: "price"},
				},
			},
		},
		Status: true,
	}
}

func (m *ResetManager) createProduct() error {
	return m.Client.ExecuteSmartContract(m.Env.ApiKey, api.SmartContractMessage{
		BlockchainID:    m.Env.BlockchainID,
		SmartContractID: m.Env.SmartContractID,
		ClauseName:      "CreateProduct",
		ClauseArguments: []api.ClauseArgument{
			{Name: "id", Value: "1"},
			{Name: "name", Value: "test"},
			{Name: "description", Value: "test"},
			{Name: "price", Value: "1"},
		},
	})
}
