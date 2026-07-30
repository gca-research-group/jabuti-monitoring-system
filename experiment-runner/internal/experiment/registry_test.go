package experiment

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
)

func TestJSONSuccessRegistryAcceptsMissingFileAndDeduplicatesWrites(t *testing.T) {
	root := t.TempDir()
	registry := JSONSuccessRegistry{OutputRoot: root}
	if err := registry.Load(); err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	metadata := registryTestMetadata()
	if registry.Contains(metadata) {
		t.Fatal("missing registry unexpectedly contains metadata")
	}
	if err := registry.MarkSuccessful(metadata); err != nil {
		t.Fatalf("MarkSuccessful() error = %v", err)
	}
	if err := registry.MarkSuccessful(metadata); err != nil {
		t.Fatalf("duplicate MarkSuccessful() error = %v", err)
	}

	data, err := os.ReadFile(filepath.Join(root, successfulScenariosFilename))
	if err != nil {
		t.Fatal(err)
	}
	var entries []runner.ScenarioMetadata
	if err := json.Unmarshal(data, &entries); err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0] != metadata {
		t.Fatalf("entries = %#v, want one metadata entry", entries)
	}
}

func TestJSONSuccessRegistryDeduplicatesExistingEntries(t *testing.T) {
	root := t.TempDir()
	metadata := registryTestMetadata()
	data, _ := json.Marshal([]runner.ScenarioMetadata{metadata, metadata})
	if err := os.WriteFile(filepath.Join(root, successfulScenariosFilename), data, 0o644); err != nil {
		t.Fatal(err)
	}

	registry := JSONSuccessRegistry{OutputRoot: root}
	if err := registry.Load(); err != nil {
		t.Fatal(err)
	}
	if !registry.Contains(metadata) || len(registry.entries) != 1 {
		t.Fatalf("loaded registry = %#v", registry.entries)
	}
}

func TestJSONSuccessRegistryRejectsMalformedOrUnreadableFile(t *testing.T) {
	t.Run("malformed", func(t *testing.T) {
		root := t.TempDir()
		if err := os.WriteFile(filepath.Join(root, successfulScenariosFilename), []byte("{"), 0o644); err != nil {
			t.Fatal(err)
		}
		if err := (&JSONSuccessRegistry{OutputRoot: root}).Load(); err == nil {
			t.Fatal("Load() error = nil")
		}
	})

	t.Run("unreadable", func(t *testing.T) {
		root := t.TempDir()
		if err := os.Mkdir(filepath.Join(root, successfulScenariosFilename), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := (&JSONSuccessRegistry{OutputRoot: root}).Load(); err == nil {
			t.Fatal("Load() error = nil")
		}
	})
}

func TestJSONSuccessRegistryDoesNotMutateMemoryAfterWriteFailure(t *testing.T) {
	root := t.TempDir()
	registry := JSONSuccessRegistry{
		OutputRoot: root,
		known:      make(map[runner.ScenarioMetadata]struct{}),
	}
	if err := os.Mkdir(filepath.Join(root, successfulScenariosFilename), 0o755); err != nil {
		t.Fatal(err)
	}

	metadata := registryTestMetadata()
	if err := registry.MarkSuccessful(metadata); err == nil {
		t.Fatal("MarkSuccessful() error = nil")
	}
	if registry.Contains(metadata) || len(registry.entries) != 0 {
		t.Fatalf("registry mutated after failure: %#v", registry.entries)
	}
}

func registryTestMetadata() runner.ScenarioMetadata {
	return runner.ScenarioMetadata{
		Events:               10,
		Lambda:               0.5,
		Duration:             300,
		IntegrationProcesses: 2,
		MaxStartDelay:        1,
		Consumers:            4,
		Repetition:           3,
	}
}
