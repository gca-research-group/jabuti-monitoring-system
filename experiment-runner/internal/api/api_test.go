package api

import (
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func testHTTPConfig() HTTPConfig {
	return HTTPConfig{
		MaxIdleConns:          3000,
		MaxIdleConnsPerHost:   3000,
		IdleConnTimeout:       90 * time.Second,
		ResponseHeaderTimeout: 15 * time.Second,
		RequestTimeout:        60 * time.Second,
	}
}

func TestStopRabbitMQRejectsNonSuccessResponse(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Header.Get("X-API-Key") != "token" {
			t.Errorf("X-API-Key = %q", request.Header.Get("X-API-Key"))
		}
		http.Error(writer, "could not stop", http.StatusInternalServerError)
	}))
	defer server.Close()

	err := NewClient(server.URL, testHTTPConfig()).StopRabbitMQ("token")
	if err == nil || !strings.Contains(err.Error(), "status 500") {
		t.Fatalf("StopRabbitMQ() error = %v", err)
	}
}

func TestNewClientConfiguresPrivateTransport(t *testing.T) {
	config := testHTTPConfig()
	client := NewClient("http://example.test", config)

	transport, ok := client.HTTPClient.Transport.(*http.Transport)
	if !ok {
		t.Fatalf("Transport type = %T", client.HTTPClient.Transport)
	}
	if transport == http.DefaultTransport {
		t.Fatal("NewClient() reused the global default transport")
	}
	if transport.MaxIdleConns != config.MaxIdleConns ||
		transport.MaxIdleConnsPerHost != config.MaxIdleConnsPerHost ||
		transport.IdleConnTimeout != config.IdleConnTimeout ||
		transport.ResponseHeaderTimeout != config.ResponseHeaderTimeout {
		t.Fatalf("transport settings do not match config: %#v", transport)
	}
	if client.HTTPClient.Timeout != config.RequestTimeout {
		t.Fatalf("client timeout = %v, want %v", client.HTTPClient.Timeout, config.RequestTimeout)
	}
}

func TestExecuteSmartContractDrainsResponseAndReusesConnection(t *testing.T) {
	var newConnections atomic.Int32
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(writer, strings.Repeat("response", 128))
	}))
	server.Config.ConnState = func(_ net.Conn, state http.ConnState) {
		if state == http.StateNew {
			newConnections.Add(1)
		}
	}
	server.Start()
	defer server.Close()

	client := NewClient(server.URL, testHTTPConfig())
	for range 5 {
		if err := client.ExecuteSmartContract("token", SmartContractMessage{}); err != nil {
			t.Fatalf("ExecuteSmartContract() error = %v", err)
		}
	}
	client.HTTPClient.CloseIdleConnections()

	if got := newConnections.Load(); got != 1 {
		t.Fatalf("new TCP connections = %d, want 1", got)
	}
}

type trackingBody struct {
	reader io.Reader
	closed bool
	eof    bool
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (function roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return function(request)
}

func (body *trackingBody) Read(buffer []byte) (int, error) {
	n, err := body.reader.Read(buffer)
	if errors.Is(err, io.EOF) {
		body.eof = true
	}
	return n, err
}

func (body *trackingBody) Close() error {
	body.closed = true
	return nil
}

func TestDrainAndCloseReadsToEOFAndCloses(t *testing.T) {
	body := &trackingBody{reader: strings.NewReader("response body")}

	if err := drainAndClose(body); err != nil {
		t.Fatalf("drainAndClose() error = %v", err)
	}
	if !body.eof || !body.closed {
		t.Fatalf("body state: eof=%t closed=%t", body.eof, body.closed)
	}
}

func TestEveryAPIOperationConsumesAndClosesResponseBody(t *testing.T) {
	operations := []struct {
		name    string
		content string
		call    func(*Client) error
	}{
		{name: "start", call: func(client *Client) error { return client.StartRabbitMQ("token") }},
		{name: "stop", call: func(client *Client) error { return client.StopRabbitMQ("token") }},
		{name: "purge", call: func(client *Client) error { return client.PurgeNotProcessedEvents("token") }},
		{name: "consumers", call: func(client *Client) error { return client.SetUpConsumers("token", 1) }},
		{
			name: "execute",
			call: func(client *Client) error {
				return client.ExecuteSmartContract("token", SmartContractMessage{})
			},
		},
		{
			name:    "registration",
			content: `{"id":"registered"}`,
			call: func(client *Client) error {
				_, err := client.RegisterBlockchain("token", BlockchainRegistration{})
				return err
			},
		},
	}

	for _, operation := range operations {
		t.Run(operation.name, func(t *testing.T) {
			body := &trackingBody{reader: strings.NewReader(operation.content)}
			client := NewClient("http://example.test", testHTTPConfig())
			client.HTTPClient.Transport = roundTripFunc(func(*http.Request) (*http.Response, error) {
				return &http.Response{
					StatusCode: http.StatusOK,
					Header:     make(http.Header),
					Body:       body,
				}, nil
			})

			if err := operation.call(client); err != nil {
				t.Fatalf("operation error = %v", err)
			}
			if !body.eof || !body.closed {
				t.Fatalf("body state: eof=%t closed=%t", body.eof, body.closed)
			}
		})
	}
}

func TestClientResponseHeaderTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {
		time.Sleep(100 * time.Millisecond)
	}))
	defer server.Close()

	config := testHTTPConfig()
	config.ResponseHeaderTimeout = 20 * time.Millisecond
	config.RequestTimeout = time.Second
	err := NewClient(server.URL, config).ExecuteSmartContract("token", SmartContractMessage{})
	if err == nil || !strings.Contains(err.Error(), "timeout awaiting response headers") {
		t.Fatalf("ExecuteSmartContract() error = %v", err)
	}
}

func TestClientOverallRequestTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusOK)
		if flusher, ok := writer.(http.Flusher); ok {
			flusher.Flush()
		}
		time.Sleep(100 * time.Millisecond)
	}))
	defer server.Close()

	config := testHTTPConfig()
	config.ResponseHeaderTimeout = time.Second
	config.RequestTimeout = 20 * time.Millisecond
	err := NewClient(server.URL, config).ExecuteSmartContract("token", SmartContractMessage{})
	if err == nil || !strings.Contains(err.Error(), "Client.Timeout") {
		t.Fatalf("ExecuteSmartContract() error = %v", err)
	}
}
