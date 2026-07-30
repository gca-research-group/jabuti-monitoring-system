package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestRegisterBlockchainSendsPayloadAndReturnsID(t *testing.T) {
	var received BlockchainRegistration
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/blockchain" {
			t.Errorf("path = %q, want /blockchain", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("method = %q, want POST", r.Method)
		}
		if r.Header.Get("X-API-Key") != "token" {
			t.Errorf("API key = %q", r.Header.Get("X-API-Key"))
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("content type = %q", r.Header.Get("Content-Type"))
		}
		if err := json.NewDecoder(r.Body).Decode(&received); err != nil {
			t.Errorf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusCreated)
		_, _ = w.Write([]byte(`{"id":"blockchain-id"}`))
	}))
	defer server.Close()

	client := NewClient(server.URL)
	payload := BlockchainRegistration{
		Name:     "Hyperledger Fabric",
		Platform: "HYPERLEDGER_FABRIC",
		Parameters: BlockchainParameters{
			CACrt:    "ca",
			KeyStore: "key",
			SignCert: "cert",
		},
	}
	id, err := client.RegisterBlockchain("token", payload)
	if err != nil {
		t.Fatalf("RegisterBlockchain() error = %v", err)
	}
	if id != "blockchain-id" {
		t.Fatalf("id = %q, want blockchain-id", id)
	}
	if received.Parameters.CACrt != "ca" || received.Parameters.KeyStore != "key" || received.Parameters.SignCert != "cert" {
		t.Fatalf("received credentials = %#v", received.Parameters)
	}
}

func TestRegisterSmartContractUsesHyphenatedRoute(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/smart-contract" {
			t.Errorf("path = %q, want /smart-contract", r.URL.Path)
		}
		_, _ = w.Write([]byte(`{"id":"contract-id"}`))
	}))
	defer server.Close()

	id, err := NewClient(server.URL).RegisterSmartContract("token", SmartContractRegistration{Name: "Product"})
	if err != nil {
		t.Fatalf("RegisterSmartContract() error = %v", err)
	}
	if id != "contract-id" {
		t.Fatalf("id = %q, want contract-id", id)
	}
}

func TestRegistrationRejectsNonSuccessResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "invalid payload", http.StatusBadRequest)
	}))
	defer server.Close()

	_, err := NewClient(server.URL).RegisterBlockchain("token", BlockchainRegistration{})
	if err == nil || !strings.Contains(err.Error(), "status 400") || !strings.Contains(err.Error(), "invalid payload") {
		t.Fatalf("RegisterBlockchain() error = %v", err)
	}
}

func TestRegistrationRequiresResponseID(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{}`))
	}))
	defer server.Close()

	_, err := NewClient(server.URL).RegisterBlockchain("token", BlockchainRegistration{})
	if err == nil || !strings.Contains(err.Error(), "does not contain an id") {
		t.Fatalf("RegisterBlockchain() error = %v", err)
	}
}
