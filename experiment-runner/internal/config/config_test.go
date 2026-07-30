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

func TestLoadEnvLoadsExperimentStorageConfiguration(t *testing.T) {
	t.Setenv("DATABASE_URL", "postgres://user:secret@db/experiments")
	t.Setenv("EXPERIMENT_OUTPUT_DIR", "custom/results")

	env := LoadEnv()

	if env.DatabaseURL != "postgres://user:secret@db/experiments" {
		t.Fatalf("DatabaseURL = %q", env.DatabaseURL)
	}
	if env.ExperimentOutputDir != "custom/results" {
		t.Fatalf("ExperimentOutputDir = %q", env.ExperimentOutputDir)
	}
}
