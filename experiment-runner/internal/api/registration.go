package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
)

type BlockchainParameters struct {
	MSPID         string `json:"mspId"`
	PeerEndpoint  string `json:"peerEndpoint"`
	PeerHostAlias string `json:"peerHostAlias"`
	ChannelName   string `json:"channelName"`
	SignCert      string `json:"signCert"`
	KeyStore      string `json:"keyStore"`
	CACrt         string `json:"caCrt"`
}

type BlockchainRegistration struct {
	Name       string               `json:"name"`
	Platform   string               `json:"platform"`
	Parameters BlockchainParameters `json:"parameters"`
}

type SmartContractClauseArgument struct {
	Name string `json:"name"`
}

type SmartContractClause struct {
	Name            string                        `json:"name"`
	ClauseArguments []SmartContractClauseArgument `json:"clauseArguments"`
}

type SmartContractRegistration struct {
	Name               string                `json:"name"`
	BlockchainPlatform string                `json:"blockchainPlatform"`
	Clauses            []SmartContractClause `json:"clauses"`
	Status             bool                  `json:"status"`
}

type registrationResponse struct {
	ID string `json:"id"`
}

func (c *Client) RegisterBlockchain(token string, payload BlockchainRegistration) (string, error) {
	return c.register(token, "/blockchain", payload)
}

func (c *Client) RegisterSmartContract(token string, payload SmartContractRegistration) (string, error) {
	return c.register(token, "/smart-contract", payload)
}

func (c *Client) register(token, path string, payload any) (string, error) {
	body, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("marshal registration payload: %w", err)
	}

	req, err := http.NewRequest(http.MethodPost, c.BaseURL+path, bytes.NewReader(body))

	if err != nil {
		return "", fmt.Errorf("create registration request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", token)

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("send registration request: %w", err)
	}

	responseBody, err := readAndClose(resp.Body)
	if err != nil {
		return "", fmt.Errorf("read registration response: %w", err)
	}
	if resp.StatusCode < http.StatusOK || resp.StatusCode >= http.StatusMultipleChoices {
		return "", fmt.Errorf("registration returned status %d: %s", resp.StatusCode, responseBody)
	}

	var response registrationResponse
	if err := json.Unmarshal(responseBody, &response); err != nil {
		return "", fmt.Errorf("decode registration response: %w", err)
	}
	if response.ID == "" {
		return "", fmt.Errorf("registration response does not contain an id")
	}

	return response.ID, nil
}
