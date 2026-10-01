package config

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

type Env struct {
	ResourceMonitoringEnabled bool
	ResourceSampleInterval    time.Duration
	ResourceSampleTimeout     time.Duration
	ResourceProducerContainer string
	ResourceConsumerContainer string
	ResourceRabbitMQContainer string
	ResourceDockerSocket      string
	BaseURL                   string
	ApiKey                    string
	DatabaseURL               string
	ExperimentOutputDir       string
	BlockchainID              string
	SmartContractID           string
	FabricCACertPath          string
	FabricPrivateKeyPath      string
	FabricSignCertPath        string
	FabricServerIP            string
	RabbitMQServerIP          string
	PostgresServerIP          string
	APIProducerSSHServer      string
	APIConsumerSSHServer      string
	FabricSSHUser             string
	FabricSSHPort             string
	RabbitMQSSHUser           string
	RabbitMQSSHPort           string
	PostgresSSHUser           string
	PostgresSSHPort           string
	APIProducerSSHUser        string
	APIProducerSSHPort        string
	APIConsumerSSHUser        string
	APIConsumerSSHPort        string
	FabricPeerPort            string
	HTTPMaxIdleConns          int
	HTTPMaxIdleConnsPerHost   int
	HTTPIdleConnTimeout       time.Duration
	HTTPResponseHeaderTimeout time.Duration
	HTTPRequestTimeout        time.Duration
}

type Parameters struct {
	WarmupDuration       int     `json:"warmupDuration"`
	Events               []int   `json:"events"`
	IntegrationProcesses []int   `json:"integrationProcesses"`
	Consumers            []int   `json:"consumers"`
	Lambda               float64 `json:"lambda"`
	Duration             int     `json:"duration"`
	MaxStartDelay        int     `json:"maxStartDelay"`
	Repetitions          int     `json:"repetitions"`
}

func LoadEnv() (*Env, error) {
	loadDotEnv(".env")

	enabled, err := strconv.ParseBool(getEnv("RESOURCE_MONITORING_ENABLED", "false"))
	if err != nil {
		return nil, fmt.Errorf("RESOURCE_MONITORING_ENABLED must be a boolean")
	}
	interval, err := getPositiveDurationEnv("RESOURCE_SAMPLE_INTERVAL", time.Second)
	if err != nil {
		return nil, err
	}
	timeout, err := getPositiveDurationEnv("RESOURCE_SAMPLE_TIMEOUT", 5*time.Second)
	if err != nil {
		return nil, err
	}
	producer := getEnv("RESOURCE_PRODUCER_CONTAINER", "api-producer")
	consumer := getEnv("RESOURCE_CONSUMER_CONTAINER", "api-consumer")
	rabbit := getEnv("RESOURCE_RABBITMQ_CONTAINER", "rabbitmq")
	socket := getEnv("RESOURCE_DOCKER_SOCKET", "/var/run/docker.sock")
	if enabled {
		for name, value := range map[string]string{"RESOURCE_PRODUCER_CONTAINER": producer, "RESOURCE_CONSUMER_CONTAINER": consumer, "RESOURCE_RABBITMQ_CONTAINER": rabbit, "RESOURCE_DOCKER_SOCKET": socket, "RABBITMQ_SERVER_IP": getEnv("RABBITMQ_SERVER_IP", "")} {
			if strings.TrimSpace(value) == "" {
				return nil, fmt.Errorf("%s is required for resource monitoring", name)
			}
		}
	}
	httpMaxIdleConns, err := getPositiveIntEnv("HTTP_MAX_IDLE_CONNS", 3000)
	if err != nil {
		return nil, err
	}
	httpMaxIdleConnsPerHost, err := getPositiveIntEnv("HTTP_MAX_IDLE_CONNS_PER_HOST", 3000)
	if err != nil {
		return nil, err
	}
	httpIdleConnTimeout, err := getPositiveDurationEnv("HTTP_IDLE_CONN_TIMEOUT", 90*time.Second)
	if err != nil {
		return nil, err
	}
	httpResponseHeaderTimeout, err := getPositiveDurationEnv("HTTP_RESPONSE_HEADER_TIMEOUT", 15*time.Second)
	if err != nil {
		return nil, err
	}
	httpRequestTimeout, err := getPositiveDurationEnv("HTTP_REQUEST_TIMEOUT", 60*time.Second)
	if err != nil {
		return nil, err
	}
	for _, name := range []string{"FABRIC_SSH_USER", "RABBITMQ_SSH_USER", "POSTGRES_SSH_USER", "API_PRODUCER_SSH_SERVER", "API_PRODUCER_SSH_USER", "API_CONSUMER_SSH_SERVER", "API_CONSUMER_SSH_USER"} {
		if strings.TrimSpace(getEnv(name, "")) == "" {
			return nil, fmt.Errorf("%s is required", name)
		}
	}
	for _, name := range []string{"FABRIC_SSH_PORT", "RABBITMQ_SSH_PORT", "POSTGRES_SSH_PORT", "API_PRODUCER_SSH_PORT", "API_CONSUMER_SSH_PORT"} {
		port, err := strconv.Atoi(getEnv(name, "22"))
		if err != nil || port < 1 || port > 65535 {
			return nil, fmt.Errorf("%s must be a port between 1 and 65535", name)
		}
	}

	return &Env{
		ResourceMonitoringEnabled: enabled, ResourceSampleInterval: interval, ResourceSampleTimeout: timeout, ResourceProducerContainer: producer, ResourceConsumerContainer: consumer, ResourceRabbitMQContainer: rabbit, ResourceDockerSocket: socket,
		BaseURL:              getEnv("API_BASE_URL", "http://localhost:8080"),
		ApiKey:               getEnv("API_KEY", ""),
		DatabaseURL:          getEnv("DATABASE_URL", ""),
		ExperimentOutputDir:  getEnv("EXPERIMENT_OUTPUT_DIR", "output/experiments"),
		BlockchainID:         getEnv("BLOCKCHAIN_ID", ""),
		SmartContractID:      getEnv("SMART_CONTRACT_ID", ""),
		FabricServerIP:       getEnv("FABRIC_SERVER_IP", ""),
		RabbitMQServerIP:     getEnv("RABBITMQ_SERVER_IP", ""),
		PostgresServerIP:     getEnv("POSTGRES_SERVER_IP", ""),
		APIProducerSSHServer: getEnv("API_PRODUCER_SSH_SERVER", ""),
		APIConsumerSSHServer: getEnv("API_CONSUMER_SSH_SERVER", ""),
		FabricSSHUser:        getEnv("FABRIC_SSH_USER", ""),
		FabricSSHPort:        getEnv("FABRIC_SSH_PORT", "22"),
		RabbitMQSSHUser:      getEnv("RABBITMQ_SSH_USER", ""),
		RabbitMQSSHPort:      getEnv("RABBITMQ_SSH_PORT", "22"),
		PostgresSSHUser:      getEnv("POSTGRES_SSH_USER", ""),
		PostgresSSHPort:      getEnv("POSTGRES_SSH_PORT", "22"),
		APIProducerSSHUser:   getEnv("API_PRODUCER_SSH_USER", ""),
		APIProducerSSHPort:   getEnv("API_PRODUCER_SSH_PORT", "22"),
		APIConsumerSSHUser:   getEnv("API_CONSUMER_SSH_USER", ""),
		APIConsumerSSHPort:   getEnv("API_CONSUMER_SSH_PORT", "22"),
		FabricPeerPort:       getEnv("FABRIC_PEER_PORT", ""),
		FabricCACertPath: getEnv(
			"FABRIC_CA_CERT_PATH",
			"/home/monitor/app/output/network-with-chaincode/org1.network-with-chaincode.com/data/certificate-authority/organizations/peerOrganizations/org1.network-with-chaincode.com/peers/peer0.org1.network-with-chaincode.com/tls/ca.crt",
		),
		FabricPrivateKeyPath: getEnv(
			"FABRIC_PRIVATE_KEY_PATH",
			"/home/monitor/app/output/network-with-chaincode/org1.network-with-chaincode.com/data/certificate-authority/organizations/peerOrganizations/org1.network-with-chaincode.com/users/User1@org1.network-with-chaincode.com/msp/keystore/priv_sk",
		),
		FabricSignCertPath: getEnv(
			"FABRIC_SIGN_CERT_PATH",
			"/home/monitor/app/output/network-with-chaincode/org1.network-with-chaincode.com/data/certificate-authority/organizations/peerOrganizations/org1.network-with-chaincode.com/users/User1@org1.network-with-chaincode.com/msp/signcerts/cert.pem",
		),
		HTTPMaxIdleConns:          httpMaxIdleConns,
		HTTPMaxIdleConnsPerHost:   httpMaxIdleConnsPerHost,
		HTTPIdleConnTimeout:       httpIdleConnTimeout,
		HTTPResponseHeaderTimeout: httpResponseHeaderTimeout,
		HTTPRequestTimeout:        httpRequestTimeout,
	}, nil
}

func LoadParameters() (*Parameters, error) {
	configPath := flag.String("config", "c", "path to config file")
	flag.Parse()

	data, err := os.ReadFile(*configPath)

	if err != nil {
		return nil, err
	}

	var parameters Parameters

	err = json.Unmarshal(data, &parameters)

	if err != nil {
		return nil, err
	}
	if err := parameters.ValidateTiming(); err != nil {
		return nil, err
	}
	return &parameters, nil
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func getPositiveIntEnv(key string, fallback int) (int, error) {
	value := getEnv(key, strconv.Itoa(fallback))
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return 0, fmt.Errorf("%s must be a positive integer, got %q", key, value)
	}
	return parsed, nil
}

func getPositiveDurationEnv(key string, fallback time.Duration) (time.Duration, error) {
	value := getEnv(key, fallback.String())
	parsed, err := time.ParseDuration(value)
	if err != nil || parsed <= 0 {
		return 0, fmt.Errorf("%s must be a positive Go duration, got %q", key, value)
	}
	return parsed, nil
}

func loadDotEnv(filename string) {
	file, err := os.Open(filename)
	if err != nil {
		return
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "#") || strings.TrimSpace(line) == "" {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}
		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])
		os.Setenv(key, value)
	}
}
