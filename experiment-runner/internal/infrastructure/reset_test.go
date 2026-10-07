package infrastructure

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

type commandCall struct {
	user     string
	address  string
	commands []string
}

type fakeCommandRunner struct {
	calls           []commandCall
	outputCalls     []string
	outputAddresses []string
	outputUsers     []string
	failAt          int
	outputError     error
}

func (f *fakeCommandRunner) Run(user, address string, commands ...string) error {
	f.calls = append(f.calls, commandCall{user: user, address: address, commands: commands})
	if len(f.calls) == f.failAt {
		return errors.New("command failed")
	}
	return nil
}

func (f *fakeCommandRunner) RunOutput(user, address, command string) ([]byte, error) {
	f.outputCalls = append(f.outputCalls, command)
	f.outputAddresses = append(f.outputAddresses, address)
	f.outputUsers = append(f.outputUsers, user)
	if f.outputError != nil {
		return nil, f.outputError
	}
	return []byte("content:" + command), nil
}

type fakeRegistrationClient struct {
	events               *[]string
	blockchainPayload    api.BlockchainRegistration
	smartContractPayload api.SmartContractRegistration
	blockchainError      error
	smartContractError   error
}

func (f *fakeRegistrationClient) RegisterBlockchain(_ string, payload api.BlockchainRegistration) (string, error) {
	if f.events != nil {
		*f.events = append(*f.events, "blockchain")
	}
	f.blockchainPayload = payload
	return "new-blockchain", f.blockchainError
}

func (f *fakeRegistrationClient) RegisterSmartContract(_ string, payload api.SmartContractRegistration) (string, error) {
	if f.events != nil {
		*f.events = append(*f.events, "smart-contract")
	}
	f.smartContractPayload = payload
	return "new-smart-contract", f.smartContractError
}

func TestResetRunsServicesInOrderWithReadinessWaits(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer server.Close()
	ssh := &fakeCommandRunner{}
	registrar := &fakeRegistrationClient{}
	var sleeps []time.Duration
	manager := ResetManager{
		SSH:       ssh,
		Registrar: registrar,
		Env:       testEnv(),
		Client:    &api.Client{BaseURL: server.URL, HTTPClient: server.Client()},
		Sleep:     func(duration time.Duration) { sleeps = append(sleeps, duration) },
	}

	if err := manager.Reset(5); err != nil {
		t.Fatalf("Reset() error = %v", err)
	}

	addresses := make([]string, 0, len(ssh.calls))
	for _, call := range ssh.calls {
		addresses = append(addresses, call.address)
	}
	wantAddresses := []string{"192.0.2.1:2222", "192.0.2.2:2223", "192.0.2.3:2224", "192.0.2.4:2225", "192.0.2.5:2226"}
	if !reflect.DeepEqual(addresses, wantAddresses) {
		t.Fatalf("addresses = %v, want %v", addresses, wantAddresses)
	}
	users := make([]string, 0, len(ssh.calls))
	for _, call := range ssh.calls {
		users = append(users, call.user)
	}
	if !reflect.DeepEqual(users, []string{"fabric-user", "rabbit-user", "postgres-user", "producer-user", "consumer-user"}) {
		t.Fatalf("users = %v", users)
	}
	if !reflect.DeepEqual(ssh.calls[3].commands, []string{"cd /home/monitor/app && docker compose -f producer.yml up --build --force-recreate -d"}) ||
		!reflect.DeepEqual(ssh.calls[4].commands, []string{"cd /home/monitor/app && RABBITMQ_LISTENER_CONCURRENCY=5 RABBITMQ_LISTENER_MAX_CONCURRENCY=5 RABBITMQ_LISTENER_PREFETCH=5 docker compose -f consumer.yml up --build --force-recreate -d"}) {
		t.Fatalf("API commands = %v / %v", ssh.calls[3].commands, ssh.calls[4].commands)
	}
	if !reflect.DeepEqual(sleeps, []time.Duration{20 * time.Second, 60 * time.Second}) {
		t.Fatalf("sleeps = %v, want [20s 30s]", sleeps)
	}

	postgresCommands := ssh.calls[2].commands
	copyCount := 0
	for _, command := range postgresCommands {
		if strings.Contains(command, "cp -a volumes/postgres/baseline") {
			copyCount++
		}
	}
	if copyCount != 1 {
		t.Fatalf("PostgreSQL baseline copy count = %d, want 1", copyCount)
	}
	if len(ssh.outputCalls) != 3 {
		t.Fatalf("credential reads = %d, want 3", len(ssh.outputCalls))
	}
	if !reflect.DeepEqual(ssh.outputAddresses, []string{wantAddresses[0], wantAddresses[0], wantAddresses[0]}) {
		t.Fatalf("credential read addresses = %v", ssh.outputAddresses)
	}
	if !reflect.DeepEqual(ssh.outputUsers, []string{"fabric-user", "fabric-user", "fabric-user"}) {
		t.Fatalf("credential read users = %v", ssh.outputUsers)
	}
	if manager.Env.BlockchainID != "new-blockchain" || manager.Env.SmartContractID != "new-smart-contract" {
		t.Fatalf("registered IDs = %q/%q", manager.Env.BlockchainID, manager.Env.SmartContractID)
	}
	if registrar.blockchainPayload.Parameters.CACrt != "content:cat -- '/ca.crt'" {
		t.Fatalf("CA certificate = %q", registrar.blockchainPayload.Parameters.CACrt)
	}
	if registrar.blockchainPayload.Parameters.KeyStore != "content:cat -- '/priv_sk'" {
		t.Fatalf("private key = %q", registrar.blockchainPayload.Parameters.KeyStore)
	}
	if registrar.blockchainPayload.Parameters.SignCert != "content:cat -- '/cert.pem'" {
		t.Fatalf("sign certificate = %q", registrar.blockchainPayload.Parameters.SignCert)
	}
	if registrar.blockchainPayload.Parameters.PeerHostAlias != "peer0.org1.network-with-chaincode.com" {
		t.Fatalf("peer host alias = %q", registrar.blockchainPayload.Parameters.PeerHostAlias)
	}
	if registrar.blockchainPayload.Parameters.PeerEndpoint != "192.0.2.1:8051" {
		t.Fatalf("peer endpoint = %q", registrar.blockchainPayload.Parameters.PeerEndpoint)
	}
	if got := registrar.smartContractPayload.Clauses; len(got) != 2 || got[0].Name != "QueryProductByID" || got[1].Name != "CreateProduct" {
		t.Fatalf("smart contract clauses = %#v", got)
	}
}

func TestResetReturnsContextAndStopsAfterFailure(t *testing.T) {
	ssh := &fakeCommandRunner{failAt: 2}
	manager := ResetManager{
		SSH:       ssh,
		Registrar: &fakeRegistrationClient{},
		Env:       testEnv(),
		Sleep:     func(time.Duration) {},
	}

	err := manager.Reset(5)
	if err == nil || !strings.Contains(err.Error(), "reset RabbitMQ") {
		t.Fatalf("Reset() error = %v", err)
	}
	if len(ssh.calls) != 2 {
		t.Fatalf("command groups = %d, want 2", len(ssh.calls))
	}
}

func TestResetStopsWhenAPIStartFails(t *testing.T) {
	for _, tc := range []struct {
		name      string
		failAt    int
		wantCalls int
	}{
		{"producer", 4, 4},
		{"consumer", 5, 5},
	} {
		t.Run(tc.name, func(t *testing.T) {
			ssh := &fakeCommandRunner{failAt: tc.failAt}
			manager := ResetManager{SSH: ssh, Registrar: &fakeRegistrationClient{}, Env: testEnv(), Sleep: func(time.Duration) {}}
			err := manager.Reset(5)
			if err == nil || !strings.Contains(err.Error(), "reset API ("+tc.name+")") {
				t.Fatalf("Reset() error = %v", err)
			}
			if len(ssh.calls) != tc.wantCalls {
				t.Fatalf("SSH calls = %d, want %d", len(ssh.calls), tc.wantCalls)
			}
		})
	}
}

func TestResetRequiresNetworkConfigurationBeforeSSH(t *testing.T) {
	env := testEnv()
	env.FabricPeerPort = ""
	ssh := &fakeCommandRunner{}
	manager := ResetManager{SSH: ssh, Registrar: &fakeRegistrationClient{}, Env: env, Sleep: func(time.Duration) {}}

	err := manager.Reset(5)
	if err == nil || !strings.Contains(err.Error(), "FABRIC_PEER_PORT") {
		t.Fatalf("Reset() error = %v", err)
	}
	if len(ssh.calls) != 0 {
		t.Fatalf("SSH calls = %d, want 0", len(ssh.calls))
	}
}

func TestResetRequiresBothAPIRolesBeforeSSH(t *testing.T) {
	for _, tc := range []struct {
		name  string
		clear func(*config.Env)
	}{
		{"API_PRODUCER_SSH_SERVER", func(env *config.Env) { env.APIProducerSSHServer = "" }},
		{"API_PRODUCER_SSH_USER", func(env *config.Env) { env.APIProducerSSHUser = "" }},
		{"API_PRODUCER_SSH_PORT", func(env *config.Env) { env.APIProducerSSHPort = "" }},
		{"API_CONSUMER_SSH_SERVER", func(env *config.Env) { env.APIConsumerSSHServer = "" }},
		{"API_CONSUMER_SSH_USER", func(env *config.Env) { env.APIConsumerSSHUser = "" }},
		{"API_CONSUMER_SSH_PORT", func(env *config.Env) { env.APIConsumerSSHPort = "" }},
	} {
		t.Run(tc.name, func(t *testing.T) {
			env := testEnv()
			tc.clear(env)
			ssh := &fakeCommandRunner{}
			manager := ResetManager{SSH: ssh, Registrar: &fakeRegistrationClient{}, Env: env, Sleep: func(time.Duration) {}}
			err := manager.Reset(5)
			if err == nil || !strings.Contains(err.Error(), tc.name) {
				t.Fatalf("Reset() error = %v, want %s", err, tc.name)
			}
			if len(ssh.calls) != 0 {
				t.Fatalf("SSH calls = %d, want 0", len(ssh.calls))
			}
		})
	}
}

func TestResetStopsWhenCredentialReadFails(t *testing.T) {
	ssh := &fakeCommandRunner{outputError: errors.New("read failed")}
	manager := ResetManager{
		SSH:       ssh,
		Registrar: &fakeRegistrationClient{},
		Env:       testEnv(),
		Sleep:     func(time.Duration) {},
	}

	err := manager.Reset(5)
	if err == nil || !strings.Contains(err.Error(), "read Fabric CA certificate") {
		t.Fatalf("Reset() error = %v", err)
	}
}

func TestResetKeepsExistingIDsWhenSmartContractRegistrationFails(t *testing.T) {
	env := testEnv()
	env.BlockchainID = "old-blockchain"
	env.SmartContractID = "old-smart-contract"
	manager := ResetManager{
		SSH: &fakeCommandRunner{},
		Registrar: &fakeRegistrationClient{
			smartContractError: errors.New("create failed"),
		},
		Env:   env,
		Sleep: func(time.Duration) {},
	}

	err := manager.Reset(5)
	if err == nil || !strings.Contains(err.Error(), "register smart contract") {
		t.Fatalf("Reset() error = %v", err)
	}
	if env.BlockchainID != "old-blockchain" || env.SmartContractID != "old-smart-contract" {
		t.Fatalf("IDs changed after failed registration: %q/%q", env.BlockchainID, env.SmartContractID)
	}
}

func testEnv() *config.Env {
	return &config.Env{
		ApiKey:               "token",
		FabricServerIP:       "192.0.2.1",
		RabbitMQServerIP:     "192.0.2.2",
		PostgresServerIP:     "192.0.2.3",
		APIProducerSSHServer: "192.0.2.4",
		APIConsumerSSHServer: "192.0.2.5",
		FabricSSHUser:        "fabric-user",
		FabricSSHPort:        "2222",
		RabbitMQSSHUser:      "rabbit-user",
		RabbitMQSSHPort:      "2223",
		PostgresSSHUser:      "postgres-user",
		PostgresSSHPort:      "2224",
		APIProducerSSHUser:   "producer-user",
		APIProducerSSHPort:   "2225",
		APIConsumerSSHUser:   "consumer-user",
		APIConsumerSSHPort:   "2226",
		FabricPeerPort:       "8051",
		FabricCACertPath:     "/ca.crt",
		FabricPrivateKeyPath: "/priv_sk",
		FabricSignCertPath:   "/cert.pem",
	}
}
