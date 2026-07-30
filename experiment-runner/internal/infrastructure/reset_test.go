package infrastructure

import (
	"errors"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
)

type commandCall struct {
	address  string
	commands []string
}

type fakeCommandRunner struct {
	calls       []commandCall
	outputCalls []string
	failAt      int
	outputError error
}

func (f *fakeCommandRunner) Run(address string, commands ...string) error {
	f.calls = append(f.calls, commandCall{address: address, commands: commands})
	if len(f.calls) == f.failAt {
		return errors.New("command failed")
	}
	return nil
}

func (f *fakeCommandRunner) RunOutput(_ string, command string) ([]byte, error) {
	f.outputCalls = append(f.outputCalls, command)
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
	ssh := &fakeCommandRunner{}
	registrar := &fakeRegistrationClient{}
	var sleeps []time.Duration
	manager := ResetManager{
		SSH:       ssh,
		Registrar: registrar,
		Env:       testEnv(),
		Sleep:     func(duration time.Duration) { sleeps = append(sleeps, duration) },
	}

	if err := manager.Reset(); err != nil {
		t.Fatalf("Reset() error = %v", err)
	}

	addresses := make([]string, 0, len(ssh.calls))
	for _, call := range ssh.calls {
		addresses = append(addresses, call.address)
	}
	wantAddresses := []string{fabricAddress, rabbitMQAddress, postgresAddress, apiAddress}
	if !reflect.DeepEqual(addresses, wantAddresses) {
		t.Fatalf("addresses = %v, want %v", addresses, wantAddresses)
	}
	if !reflect.DeepEqual(sleeps, []time.Duration{20 * time.Second, 30 * time.Second}) {
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

	err := manager.Reset()
	if err == nil || !strings.Contains(err.Error(), "reset RabbitMQ") {
		t.Fatalf("Reset() error = %v", err)
	}
	if len(ssh.calls) != 2 {
		t.Fatalf("command groups = %d, want 2", len(ssh.calls))
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

	err := manager.Reset()
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

	err := manager.Reset()
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
		FabricCACertPath:     "/ca.crt",
		FabricPrivateKeyPath: "/priv_sk",
		FabricSignCertPath:   "/cert.pem",
	}
}
