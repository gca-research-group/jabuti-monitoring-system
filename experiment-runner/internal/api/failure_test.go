package api

import (
	"context"
	"crypto/tls"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"syscall"
	"testing"
)

func TestClassifyExecutionFailure(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want string
	}{
		{name: "HTTP status", err: &httpStatusError{statusCode: 503}, want: "http_503"},
		{
			name: "request encoding",
			err:  wrapExecutionError(failureStageRequestEncoding, errors.New("encode")),
			want: "request_encoding_failure",
		},
		{
			name: "request creation",
			err:  wrapExecutionError(failureStageRequestCreation, errors.New("create")),
			want: "request_creation_failure",
		},
		{
			name: "response body",
			err:  wrapExecutionError(failureStageResponseBody, errors.New("read")),
			want: "response_body_failure",
		},
		{name: "timeout", err: context.DeadlineExceeded, want: "timeout"},
		{name: "DNS", err: &net.DNSError{Name: "api.example"}, want: "dns_failure"},
		{name: "connection refused", err: syscall.ECONNREFUSED, want: "connection_refused"},
		{name: "connection reset", err: syscall.ECONNRESET, want: "connection_reset"},
		{name: "connection aborted", err: syscall.ECONNABORTED, want: "connection_aborted"},
		{name: "broken pipe", err: syscall.EPIPE, want: "broken_pipe"},
		{name: "unexpected EOF", err: io.ErrUnexpectedEOF, want: "unexpected_eof"},
		{name: "EOF", err: io.EOF, want: "unexpected_eof"},
		{name: "TLS", err: tls.RecordHeaderError{Msg: "bad TLS record"}, want: "tls_failure"},
		{
			name: "other network",
			err:  &net.OpError{Op: "read", Net: "tcp", Err: errors.New("network error")},
			want: "network_failure",
		},
		{name: "unknown", err: errors.New("other error"), want: "unknown_failure"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			wrapped := errors.Join(errors.New("outer context"), test.err)
			if got := ClassifyExecutionFailure(wrapped); got != test.want {
				t.Fatalf("ClassifyExecutionFailure() = %q, want %q", got, test.want)
			}
		})
	}
}

func TestExecuteSmartContractHTTPFailurePreservesStatusAndBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		http.Error(writer, "service overloaded", http.StatusServiceUnavailable)
	}))
	defer server.Close()

	err := NewClient(server.URL, testHTTPConfig()).ExecuteSmartContract("token", SmartContractMessage{})
	if got := ClassifyExecutionFailure(err); got != "http_503" {
		t.Fatalf("category = %q, want http_503", got)
	}
	if err == nil || !strings.Contains(err.Error(), "service overloaded") {
		t.Fatalf("ExecuteSmartContract() error = %v, want response body", err)
	}
}

func TestExecuteSmartContractClassifiesEncodingAndCreationFailures(t *testing.T) {
	client := NewClient("://invalid", testHTTPConfig())

	encodingErr := client.ExecuteSmartContract("token", SmartContractMessage{
		Metadata: map[string]any{"unsupported": func() {}},
	})
	if got := ClassifyExecutionFailure(encodingErr); got != "request_encoding_failure" {
		t.Fatalf("encoding category = %q", got)
	}

	creationErr := client.ExecuteSmartContract("token", SmartContractMessage{})
	if got := ClassifyExecutionFailure(creationErr); got != "request_creation_failure" {
		t.Fatalf("creation category = %q", got)
	}
}
