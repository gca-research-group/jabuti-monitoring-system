package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
)

type ClauseArgument struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

type SmartContractMessage struct {
	ExecutionId     string           `json:"executionId"`
	GroupId         string           `json:"groupId"`
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

	req.Header.Set("Authorization", fmt.Sprintf("Basic %s", token))
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

	req.Header.Set("Authorization", fmt.Sprintf("Basic %s", token))
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to stop benchmark: %v", err)
	}
	defer resp.Body.Close()

	return nil
}

func (c *Client) PurgeNotProcessedEvents(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/benchmark/purge-all", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to purge queues: %v", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Basic %s", token))
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

	req.Header.Set("Authorization", fmt.Sprintf("Basic %s", token))
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to set up consumers: %v", err)
	}
	defer resp.Body.Close()

	return nil
}

func (c *Client) ExecuteSmartContract(token string, message SmartContractMessage) error {
	body, err := json.Marshal(message)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %v", err)
	}

	req, err := http.NewRequest("POST", fmt.Sprintf("%s/smart-contract-execution/execute", c.BaseURL), bytes.NewBuffer(body))
	if err != nil {
		return fmt.Errorf("failed to create execute request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", fmt.Sprintf("Basic %s", token))

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to execute smart contract: %v", err)
	}
	defer resp.Body.Close()

	return nil
}
