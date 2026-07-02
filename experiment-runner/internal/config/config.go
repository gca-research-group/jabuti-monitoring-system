package config

import (
	"bufio"
	"encoding/json"
	"flag"
	"os"
	"strings"
)

type Env struct {
	BaseURL         string
	AdminEmail      string
	AdminPassword   string
	BlockchainID    string
	SmartContractID string
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
		BaseURL:         getEnv("API_BASE_URL", "http://localhost:8080"),
		AdminEmail:      getEnv("ADMIN_EMAIL", "admin@admin.com"),
		AdminPassword:   getEnv("ADMIN_PASSWORD", "admin"),
		BlockchainID:    getEnv("BLOCKCHAIN_ID", ""),
		SmartContractID: getEnv("SMART_CONTRACT_ID", ""),
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
