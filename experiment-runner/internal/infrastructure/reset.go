package infrastructure

import (
	"fmt"
	"net"
	"strings"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

type ResetManager struct {
	SSH       CommandRunner
	Registrar RegistrationClient
	Env       *config.Env
	Sleep     func(time.Duration)
	Client    *api.Client
}

type CommandRunner interface {
	Run(user, address string, commands ...string) error
	RunOutput(user, address, command string) ([]byte, error)
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

func (m *ResetManager) Reset(consumers int) error {
	if consumers <= 0 {
		return fmt.Errorf("consumer count must be positive")
	}
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
	for _, required := range []struct{ name, value string }{
		{"FABRIC_SERVER_IP", m.Env.FabricServerIP},
		{"RABBITMQ_SERVER_IP", m.Env.RabbitMQServerIP},
		{"POSTGRES_SERVER_IP", m.Env.PostgresServerIP},
		{"API_PRODUCER_SSH_SERVER", m.Env.APIProducerSSHServer},
		{"API_CONSUMER_SSH_SERVER", m.Env.APIConsumerSSHServer},
		{"FABRIC_SSH_USER", m.Env.FabricSSHUser},
		{"FABRIC_SSH_PORT", m.Env.FabricSSHPort},
		{"RABBITMQ_SSH_USER", m.Env.RabbitMQSSHUser},
		{"RABBITMQ_SSH_PORT", m.Env.RabbitMQSSHPort},
		{"POSTGRES_SSH_USER", m.Env.PostgresSSHUser},
		{"POSTGRES_SSH_PORT", m.Env.PostgresSSHPort},
		{"API_PRODUCER_SSH_USER", m.Env.APIProducerSSHUser},
		{"API_PRODUCER_SSH_PORT", m.Env.APIProducerSSHPort},
		{"API_CONSUMER_SSH_USER", m.Env.APIConsumerSSHUser},
		{"API_CONSUMER_SSH_PORT", m.Env.APIConsumerSSHPort},
		{"FABRIC_PEER_PORT", m.Env.FabricPeerPort},
	} {
		if strings.TrimSpace(required.value) == "" {
			return fmt.Errorf("%s is required", required.name)
		}
	}

	steps := []struct {
		name     string
		user     string
		address  string
		commands []string
	}{
		{
			name:    "Hyperledger Fabric",
			user:    m.Env.FabricSSHUser,
			address: net.JoinHostPort(m.Env.FabricServerIP, m.Env.FabricSSHPort),
			commands: []string{
				"cd /home/monitor/app && fno --config network-with-chaincode.yml network down",
				"rm -rf /home/monitor/app/output/network-with-chaincode",
				"cp -a /home/monitor/app/baseline/network-with-chaincode /home/monitor/app/output/network-with-chaincode",
				"cd /home/monitor/app && fno --config network-with-chaincode.yml network up",
			},
		},
		{
			name:    "RabbitMQ",
			user:    m.Env.RabbitMQSSHUser,
			address: net.JoinHostPort(m.Env.RabbitMQServerIP, m.Env.RabbitMQSSHPort),
			commands: []string{
				"cd /home/monitor/app && docker compose -f rabbitmq.yml down",
				"cd /home/monitor/app && rm -rf volumes/rabbitmq",
				"cd /home/monitor/app && cp -a volumes/baseline volumes/rabbitmq",
				"cd /home/monitor/app && docker compose -f rabbitmq.yml up --build -d",
			},
		},
		{
			name:    "PostgreSQL",
			user:    m.Env.PostgresSSHUser,
			address: net.JoinHostPort(m.Env.PostgresServerIP, m.Env.PostgresSSHPort),
			commands: []string{
				"cd /home/monitor/app && docker compose -f network.yml -f postgres.yml down",
				"cd /home/monitor/app && rm -rf volumes/postgres/data",
				"cd /home/monitor/app && cp -a volumes/postgres/baseline volumes/postgres/data",
				"cd /home/monitor/app && docker compose -f network.yml -f postgres.yml up --build -d",
			},
		},
	}

	for _, step := range steps {
		if err := m.SSH.Run(step.user, step.address, step.commands...); err != nil {
			return fmt.Errorf("reset %s: %w", step.name, err)
		}
	}

	m.Sleep(20 * time.Second)

	if err := m.SSH.Run(m.Env.APIProducerSSHUser, net.JoinHostPort(m.Env.APIProducerSSHServer, m.Env.APIProducerSSHPort),
		"cd /home/monitor/app && docker compose -f producer.yml up --build --force-recreate -d",
	); err != nil {
		return fmt.Errorf("reset API (producer): %w", err)
	}
	if err := m.SSH.Run(m.Env.APIConsumerSSHUser, net.JoinHostPort(m.Env.APIConsumerSSHServer, m.Env.APIConsumerSSHPort),
		fmt.Sprintf("cd /home/monitor/app && RABBITMQ_LISTENER_CONCURRENCY=%d RABBITMQ_LISTENER_MAX_CONCURRENCY=%d RABBITMQ_LISTENER_PREFETCH=%d RABBITMQ_CACHE_CHANNEL_SIZE=%d docker compose -f consumer.yml up --build --force-recreate -d",
			consumers, consumers, consumers, consumers*4),
	); err != nil {
		return fmt.Errorf("reset API (consumer): %w", err)
	}

	m.Sleep(60 * time.Second)
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
			PeerEndpoint:  net.JoinHostPort(m.Env.FabricServerIP, m.Env.FabricPeerPort),
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

	output, err := m.SSH.RunOutput(m.Env.FabricSSHUser, net.JoinHostPort(m.Env.FabricServerIP, m.Env.FabricSSHPort), "cat -- "+shellQuote(path))
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
