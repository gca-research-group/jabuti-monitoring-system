package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
)

type ClauseArgument struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

type SmartContractMessage struct {
	BlockchainID    string           `json:"blockchainId"`
	SmartContractID string           `json:"smartContractId"`
	ClauseName      string           `json:"clauseName"`
	ClauseArguments []ClauseArgument `json:"clauseArguments"`
	Metadata        map[string]any   `json:"metadata"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type LoginResponse struct {
	AccessToken string `json:"accessToken"`
}

type Client struct {
	BaseURL    string
	HTTPClient *http.Client
}

func NewClient(baseURL string) *Client {
	return &Client{
		BaseURL:    baseURL,
		HTTPClient: &http.Client{},
	}
}

func (c *Client) StartProcessing(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/benchmark/start", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to create start benchmark request: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to start benchmark: %v", err)
	}
	defer resp.Body.Close()

	return nil
}

func (c *Client) StopProcessing(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/benchmark/stop", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to create stop benchmark request: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to stop benchmark: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= http.StatusMultipleChoices {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("stop benchmark returned status %d: %s", resp.StatusCode, string(body))
	}
	return nil
}

func (c *Client) PurgeNotProcessedEvents(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/benchmark/purge-all", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to purge queues: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to purge queues: %v", err)
	}
	defer resp.Body.Close()

	return nil
}

func (c *Client) SetUpConsumers(token string, quantity int) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/consumers/%d", c.BaseURL, quantity), nil)
	if err != nil {
		return fmt.Errorf("failed to set up consumers: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to set up consumers: %v", err)
	}
	defer resp.Body.Close()

	return nil
}

func (c *Client) ExecuteSmartContract(token string, message SmartContractMessage) error {
	endpoint := fmt.Sprintf("%s/smart-contract-execution/execute", c.BaseURL)

	body, err := json.Marshal(message)
	if err != nil {
		log.Printf("[ExecuteSmartContract] failed to marshal message: %v", err)
		return fmt.Errorf("marshal message: %w", err)
	}

	req, err := http.NewRequest("POST", endpoint, bytes.NewBuffer(body))

	if err != nil {
		log.Printf("[ExecuteSmartContract] failed to create request: %v", err)
		return fmt.Errorf("create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		log.Printf("[ExecuteSmartContract] request failed: %v", err)
		return fmt.Errorf("execute smart contract request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		log.Printf("[ExecuteSmartContract] non-success response status=%d body=%s", resp.StatusCode, string(bodyBytes))

		return fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	return nil
}
