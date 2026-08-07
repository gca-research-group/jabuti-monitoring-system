package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"
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

type HTTPConfig struct {
	MaxIdleConns          int
	MaxIdleConnsPerHost   int
	IdleConnTimeout       time.Duration
	ResponseHeaderTimeout time.Duration
	RequestTimeout        time.Duration
}

func NewClient(baseURL string, config HTTPConfig) *Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.MaxIdleConns = config.MaxIdleConns
	transport.MaxIdleConnsPerHost = config.MaxIdleConnsPerHost
	transport.IdleConnTimeout = config.IdleConnTimeout
	transport.ResponseHeaderTimeout = config.ResponseHeaderTimeout

	return &Client{
		BaseURL: baseURL,
		HTTPClient: &http.Client{
			Transport: transport,
			Timeout:   config.RequestTimeout,
		},
	}
}

func (c *Client) StartRabbitMQ(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/rabbitmq/start", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to create start rabbitmq request: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to start rabbitmq: %v", err)
	}

	if err := drainAndClose(resp.Body); err != nil {
		return fmt.Errorf("read start rabbitmq response: %w", err)
	}
	return nil
}

func (c *Client) StopRabbitMQ(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/rabbitmq/stop", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to create stop rabbitmq request: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to stop rabbitmq: %v", err)
	}

	if resp.StatusCode >= http.StatusMultipleChoices {
		body, err := readAndClose(resp.Body)
		if err != nil {
			return fmt.Errorf("read stop rabbitmq error response: %w", err)
		}
		return fmt.Errorf("stop rabbitmq returned status %d: %s", resp.StatusCode, string(body))
	}
	if err := drainAndClose(resp.Body); err != nil {
		return fmt.Errorf("read stop rabbitmq response: %w", err)
	}
	return nil
}

func (c *Client) PurgeNotProcessedEvents(token string) error {
	req, err := http.NewRequest("POST", fmt.Sprintf("%s/rabbitmq/purge-all", c.BaseURL), nil)
	if err != nil {
		return fmt.Errorf("failed to purge queues: %v", err)
	}

	req.Header.Set("X-API-Key", token)
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to purge queues: %v", err)
	}

	if err := drainAndClose(resp.Body); err != nil {
		return fmt.Errorf("read purge queues response: %w", err)
	}
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

	if err := drainAndClose(resp.Body); err != nil {
		return fmt.Errorf("read set up consumers response: %w", err)
	}
	return nil
}

func (c *Client) ExecuteSmartContract(token string, message SmartContractMessage) error {
	endpoint := fmt.Sprintf("%s/smart-contract-execution/execute", c.BaseURL)

	body, err := json.Marshal(message)
	if err != nil {
		return wrapExecutionError(failureStageRequestEncoding, fmt.Errorf("marshal message: %w", err))
	}

	req, err := http.NewRequest("POST", endpoint, bytes.NewBuffer(body))

	if err != nil {
		return wrapExecutionError(failureStageRequestCreation, fmt.Errorf("create request: %w", err))
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return wrapExecutionError(failureStageTransport, fmt.Errorf("execute smart contract request: %w", err))
	}

	if resp.StatusCode >= 300 {
		bodyBytes, err := readAndClose(resp.Body)
		if err != nil {
			return wrapExecutionError(
				failureStageResponseBody,
				fmt.Errorf("read execute smart contract error response: %w", err),
			)
		}
		return &httpStatusError{statusCode: resp.StatusCode, body: bodyBytes}
	}

	if err := drainAndClose(resp.Body); err != nil {
		return wrapExecutionError(
			failureStageResponseBody,
			fmt.Errorf("read execute smart contract response: %w", err),
		)
	}
	return nil
}

func drainAndClose(body io.ReadCloser) error {
	_, readErr := io.Copy(io.Discard, body)
	closeErr := body.Close()
	return errors.Join(readErr, closeErr)
}

func readAndClose(body io.ReadCloser) ([]byte, error) {
	content, readErr := io.ReadAll(body)
	closeErr := body.Close()
	return content, errors.Join(readErr, closeErr)
}
