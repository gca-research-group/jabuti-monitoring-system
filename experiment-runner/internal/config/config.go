package config

import (
	"bufio"
	"encoding/json"
	"flag"
	"os"
	"strings"
)

type Env struct {
	BaseURL              string
	ApiKey               string
	DatabaseURL          string
	ExperimentOutputDir  string
	BlockchainID         string
	SmartContractID      string
	FabricCACertPath     string
	FabricPrivateKeyPath string
	FabricSignCertPath   string
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

func LoadEnv() *Env {
	loadDotEnv(".env")

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
	}
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
