package api

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"syscall"
)

type executionFailureStage string

const (
	failureStageRequestEncoding executionFailureStage = "request_encoding"
	failureStageRequestCreation executionFailureStage = "request_creation"
	failureStageTransport       executionFailureStage = "transport"
	failureStageResponseBody    executionFailureStage = "response_body"
)

type executionError struct {
	stage executionFailureStage
	err   error
}

func (err *executionError) Error() string {
	return err.err.Error()
}

func (err *executionError) Unwrap() error {
	return err.err
}

type httpStatusError struct {
	statusCode int
	body       []byte
}

func (err *httpStatusError) Error() string {
	return fmt.Sprintf("execute smart contract returned status %d: %s", err.statusCode, err.body)
}

func wrapExecutionError(stage executionFailureStage, err error) error {
	return &executionError{stage: stage, err: err}
}

func ClassifyExecutionFailure(err error) string {
	var statusErr *httpStatusError
	if errors.As(err, &statusErr) {
		return fmt.Sprintf("http_%d", statusErr.statusCode)
	}

	var executionErr *executionError
	if errors.As(err, &executionErr) {
		switch executionErr.stage {
		case failureStageRequestEncoding:
			return "request_encoding_failure"
		case failureStageRequestCreation:
			return "request_creation_failure"
		case failureStageResponseBody:
			if category := classifyNetworkFailure(executionErr.err); category != "" {
				return category
			}
			return "response_body_failure"
		case failureStageTransport:
			if category := classifyNetworkFailure(executionErr.err); category != "" {
				return category
			}
			return "network_failure"
		}
	}

	if category := classifyNetworkFailure(err); category != "" {
		return category
	}
	return "unknown_failure"
}

func classifyNetworkFailure(err error) string {
	var dnsErr *net.DNSError
	if errors.As(err, &dnsErr) {
		return "dns_failure"
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return "timeout"
	}
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return "timeout"
	}

	switch {
	case errors.Is(err, syscall.ECONNREFUSED):
		return "connection_refused"
	case errors.Is(err, syscall.ECONNRESET):
		return "connection_reset"
	case errors.Is(err, syscall.ECONNABORTED):
		return "connection_aborted"
	case errors.Is(err, syscall.EPIPE):
		return "broken_pipe"
	case errors.Is(err, io.ErrUnexpectedEOF), errors.Is(err, io.EOF):
		return "unexpected_eof"
	}

	var recordHeaderErr tls.RecordHeaderError
	var verificationErr *tls.CertificateVerificationError
	var unknownAuthorityErr x509.UnknownAuthorityError
	var certificateInvalidErr x509.CertificateInvalidError
	var hostnameErr x509.HostnameError
	if errors.As(err, &recordHeaderErr) ||
		errors.As(err, &verificationErr) ||
		errors.As(err, &unknownAuthorityErr) ||
		errors.As(err, &certificateInvalidErr) ||
		errors.As(err, &hostnameErr) {
		return "tls_failure"
	}

	var operationErr *net.OpError
	var urlErr *url.Error
	if errors.As(err, &operationErr) || errors.As(err, &urlErr) {
		return "network_failure"
	}
	return ""
}
