package config

import "testing"

func TestLoadEnvLoadsFabricCredentialPaths(t *testing.T) {
	t.Setenv("FABRIC_CA_CERT_PATH", "/configured/ca.crt")
	t.Setenv("FABRIC_PRIVATE_KEY_PATH", "/configured/priv_sk")
	t.Setenv("FABRIC_SIGN_CERT_PATH", "/configured/cert.pem")

	env := LoadEnv()

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
