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
	BaseURL                   string
	ApiKey                    string
	DatabaseURL               string
	ExperimentOutputDir       string
	BlockchainID              string
	SmartContractID           string
	FabricCACertPath          string
	FabricPrivateKeyPath      string
	FabricSignCertPath        string
	HTTPMaxIdleConns          int
	HTTPMaxIdleConnsPerHost   int
	HTTPIdleConnTimeout       time.Duration
	HTTPResponseHeaderTimeout time.Duration
	HTTPRequestTimeout        time.Duration
}

type Parameters struct {
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

	return &Env{
		BaseURL:             getEnv("API_BASE_URL", "http://localhost:8080"),
		ApiKey:              getEnv("API_KEY", ""),
		DatabaseURL:         getEnv("DATABASE_URL", ""),
		ExperimentOutputDir: getEnv("EXPERIMENT_OUTPUT_DIR", "output/experiments"),
		BlockchainID:        getEnv("BLOCKCHAIN_ID", ""),
		SmartContractID:     getEnv("SMART_CONTRACT_ID", ""),
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

	return &parameters, err
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
