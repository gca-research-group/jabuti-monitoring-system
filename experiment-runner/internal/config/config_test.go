package config

import (
	"strings"
	"testing"
	"time"
)

func TestLoadEnvLoadsFabricCredentialPaths(t *testing.T) {
	t.Setenv("FABRIC_CA_CERT_PATH", "/configured/ca.crt")
	t.Setenv("FABRIC_PRIVATE_KEY_PATH", "/configured/priv_sk")
	t.Setenv("FABRIC_SIGN_CERT_PATH", "/configured/cert.pem")

	env, err := LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() error = %v", err)
	}

	if env.FabricCACertPath != "/configured/ca.crt" {
		t.Fatalf("FabricCACertPath = %q", env.FabricCACertPath)
	}
	if env.FabricPrivateKeyPath != "/configured/priv_sk" {
		t.Fatalf("FabricPrivateKeyPath = %q", env.FabricPrivateKeyPath)
	}
	if env.FabricSignCertPath != "/configured/cert.pem" {
		t.Fatalf("FabricSignCertPath = %q", env.FabricSignCertPath)
	}
}

func TestLoadEnvLoadsExperimentStorageConfiguration(t *testing.T) {
	t.Setenv("DATABASE_URL", "postgres://user:secret@db/experiments")
	t.Setenv("EXPERIMENT_OUTPUT_DIR", "custom/results")

	env, err := LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() error = %v", err)
	}

	if env.DatabaseURL != "postgres://user:secret@db/experiments" {
		t.Fatalf("DatabaseURL = %q", env.DatabaseURL)
	}
	if env.ExperimentOutputDir != "custom/results" {
		t.Fatalf("ExperimentOutputDir = %q", env.ExperimentOutputDir)
	}
}

func TestLoadEnvLoadsHTTPDefaults(t *testing.T) {
	env, err := LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() error = %v", err)
	}

	if env.HTTPMaxIdleConns != 3000 || env.HTTPMaxIdleConnsPerHost != 3000 {
		t.Fatalf("idle connection settings = %d/%d", env.HTTPMaxIdleConns, env.HTTPMaxIdleConnsPerHost)
	}
	if env.HTTPIdleConnTimeout != 90*time.Second ||
		env.HTTPResponseHeaderTimeout != 15*time.Second ||
		env.HTTPRequestTimeout != 60*time.Second {
		t.Fatalf(
			"timeout settings = %v/%v/%v",
			env.HTTPIdleConnTimeout,
			env.HTTPResponseHeaderTimeout,
			env.HTTPRequestTimeout,
		)
	}
}

func TestLoadEnvLoadsExplicitHTTPConfiguration(t *testing.T) {
	t.Setenv("HTTP_MAX_IDLE_CONNS", "2000")
	t.Setenv("HTTP_MAX_IDLE_CONNS_PER_HOST", "1500")
	t.Setenv("HTTP_IDLE_CONN_TIMEOUT", "2m")
	t.Setenv("HTTP_RESPONSE_HEADER_TIMEOUT", "12s")
	t.Setenv("HTTP_REQUEST_TIMEOUT", "75s")

	env, err := LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() error = %v", err)
	}

	if env.HTTPMaxIdleConns != 2000 || env.HTTPMaxIdleConnsPerHost != 1500 {
		t.Fatalf("idle connection settings = %d/%d", env.HTTPMaxIdleConns, env.HTTPMaxIdleConnsPerHost)
	}
	if env.HTTPIdleConnTimeout != 2*time.Minute ||
		env.HTTPResponseHeaderTimeout != 12*time.Second ||
		env.HTTPRequestTimeout != 75*time.Second {
		t.Fatalf(
			"timeout settings = %v/%v/%v",
			env.HTTPIdleConnTimeout,
			env.HTTPResponseHeaderTimeout,
			env.HTTPRequestTimeout,
		)
	}
}

func TestLoadEnvRejectsInvalidHTTPConfiguration(t *testing.T) {
	tests := []struct {
		name  string
		key   string
		value string
	}{
		{name: "non-numeric connections", key: "HTTP_MAX_IDLE_CONNS", value: "many"},
		{name: "zero connections", key: "HTTP_MAX_IDLE_CONNS_PER_HOST", value: "0"},
		{name: "negative connections", key: "HTTP_MAX_IDLE_CONNS", value: "-1"},
		{name: "invalid duration", key: "HTTP_IDLE_CONN_TIMEOUT", value: "soon"},
		{name: "zero duration", key: "HTTP_RESPONSE_HEADER_TIMEOUT", value: "0s"},
		{name: "negative duration", key: "HTTP_REQUEST_TIMEOUT", value: "-1s"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Setenv(test.key, test.value)

			_, err := LoadEnv()
			if err == nil || !strings.Contains(err.Error(), test.key) {
				t.Fatalf("LoadEnv() error = %v, want error containing %s", err, test.key)
			}
		})
	}
}
