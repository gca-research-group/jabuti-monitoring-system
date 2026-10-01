package config

import (
	"os"
	"strings"
	"testing"
	"time"
)

func TestMain(m *testing.M) {
	for _, name := range []string{"FABRIC_SSH_USER", "RABBITMQ_SSH_USER", "POSTGRES_SSH_USER", "API_PRODUCER_SSH_SERVER", "API_PRODUCER_SSH_USER", "API_CONSUMER_SSH_SERVER", "API_CONSUMER_SSH_USER"} {
		os.Setenv(name, "test-user")
	}
	os.Exit(m.Run())
}

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

func TestLoadEnvLoadsResetNetworkConfiguration(t *testing.T) {
	t.Setenv("FABRIC_SERVER_IP", "192.0.2.1")
	t.Setenv("RABBITMQ_SERVER_IP", "192.0.2.2")
	t.Setenv("POSTGRES_SERVER_IP", "192.0.2.3")
	t.Setenv("API_PRODUCER_SSH_SERVER", "192.0.2.4")
	t.Setenv("API_CONSUMER_SSH_SERVER", "192.0.2.5")
	t.Setenv("FABRIC_PEER_PORT", "8051")

	env, err := LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() error = %v", err)
	}
	if env.FabricServerIP != "192.0.2.1" || env.RabbitMQServerIP != "192.0.2.2" ||
		env.PostgresServerIP != "192.0.2.3" || env.APIProducerSSHServer != "192.0.2.4" || env.APIConsumerSSHServer != "192.0.2.5" ||
		env.FabricPeerPort != "8051" || env.FabricSSHPort != "22" || env.RabbitMQSSHPort != "22" ||
		env.PostgresSSHPort != "22" || env.APIProducerSSHPort != "22" || env.APIConsumerSSHPort != "22" {
		t.Fatalf("reset network configuration = %+v", env)
	}

	t.Setenv("FABRIC_SSH_USER", "fabric-user")
	t.Setenv("FABRIC_SSH_PORT", "2222")
	t.Setenv("RABBITMQ_SSH_USER", "rabbit-user")
	t.Setenv("RABBITMQ_SSH_PORT", "2223")
	t.Setenv("POSTGRES_SSH_USER", "postgres-user")
	t.Setenv("POSTGRES_SSH_PORT", "2224")
	t.Setenv("API_PRODUCER_SSH_USER", "producer-user")
	t.Setenv("API_PRODUCER_SSH_PORT", "2225")
	t.Setenv("API_CONSUMER_SSH_USER", "consumer-user")
	t.Setenv("API_CONSUMER_SSH_PORT", "2226")
	env, err = LoadEnv()
	if err != nil {
		t.Fatalf("LoadEnv() with SSH overrides error = %v", err)
	}
	if env.FabricSSHUser != "fabric-user" || env.FabricSSHPort != "2222" ||
		env.RabbitMQSSHUser != "rabbit-user" || env.RabbitMQSSHPort != "2223" ||
		env.PostgresSSHUser != "postgres-user" || env.PostgresSSHPort != "2224" ||
		env.APIProducerSSHUser != "producer-user" || env.APIProducerSSHPort != "2225" ||
		env.APIConsumerSSHUser != "consumer-user" || env.APIConsumerSSHPort != "2226" {
		t.Fatalf("SSH settings = %+v", env)
	}
}

func TestLoadEnvRejectsMissingSSHUserAndInvalidPort(t *testing.T) {
	for _, tc := range []struct{ key, value string }{
		{"FABRIC_SSH_USER", ""},
		{"RABBITMQ_SSH_USER", " "},
		{"POSTGRES_SSH_USER", ""},
		{"API_PRODUCER_SSH_SERVER", ""},
		{"API_PRODUCER_SSH_USER", ""},
		{"API_CONSUMER_SSH_SERVER", " "},
		{"API_CONSUMER_SSH_USER", ""},
		{"FABRIC_SSH_PORT", "0"},
		{"RABBITMQ_SSH_PORT", "65536"},
		{"POSTGRES_SSH_PORT", "abc"},
		{"API_PRODUCER_SSH_PORT", "-1"},
		{"API_CONSUMER_SSH_PORT", "65536"},
	} {
		t.Run(tc.key+tc.value, func(t *testing.T) {
			t.Setenv(tc.key, tc.value)
			_, err := LoadEnv()
			if err == nil || !strings.Contains(err.Error(), tc.key) {
				t.Fatalf("LoadEnv() error = %v, want %s", err, tc.key)
			}
		})
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

func TestResourceConfiguration(t *testing.T) {
	t.Setenv("RESOURCE_MONITORING_ENABLED", "false")
	e, err := LoadEnv()
	if err != nil || e.ResourceMonitoringEnabled || e.ResourceSampleInterval != time.Second || e.ResourceSampleTimeout != 5*time.Second {
		t.Fatalf("defaults: %+v %v", e, err)
	}
	for name, value := range map[string]string{"RESOURCE_MONITORING_ENABLED": "invalid", "RESOURCE_SAMPLE_INTERVAL": "0s", "RESOURCE_SAMPLE_TIMEOUT": "-1s"} {
		t.Run(name, func(t *testing.T) {
			t.Setenv(name, value)
			if _, err := LoadEnv(); err == nil {
				t.Fatal("expected validation error")
			}
		})
	}
	t.Setenv("RESOURCE_MONITORING_ENABLED", "true")
	t.Setenv("RABBITMQ_SERVER_IP", "host")
	t.Setenv("RESOURCE_PRODUCER_CONTAINER", "")
	if _, err := LoadEnv(); err == nil {
		t.Fatal("empty enabled target accepted")
	}
}
