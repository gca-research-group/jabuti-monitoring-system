package experiment

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"

	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"github.com/natefinch/atomic"
)

const successfulScenariosFilename = "successful-scenarios.json"

type JSONSuccessRegistry struct {
	OutputRoot string

	entries []runner.ScenarioMetadata
	known   map[runner.ScenarioMetadata]struct{}
}

func (r *JSONSuccessRegistry) Load() error {
	if r.OutputRoot == "" {
		return fmt.Errorf("experiment output directory is required")
	}

	r.entries = nil
	r.known = make(map[runner.ScenarioMetadata]struct{})
	path := filepath.Join(r.OutputRoot, successfulScenariosFilename)
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return fmt.Errorf("read successful scenario registry: %w", err)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&r.entries); err != nil {
		return fmt.Errorf("decode successful scenario registry: %w", err)
	}
	if r.entries == nil {
		return fmt.Errorf("decode successful scenario registry: expected a JSON array")
	}
	var trailing any
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		return fmt.Errorf("decode successful scenario registry: unexpected trailing content")
	}

	deduplicated := r.entries[:0]
	for _, metadata := range r.entries {
		if _, exists := r.known[metadata]; exists {
			continue
		}
		r.known[metadata] = struct{}{}
		deduplicated = append(deduplicated, metadata)
	}
	r.entries = deduplicated
	return nil
}

func (r *JSONSuccessRegistry) Contains(metadata runner.ScenarioMetadata) bool {
	_, exists := r.known[metadata]
	return exists
}

func (r *JSONSuccessRegistry) MarkSuccessful(metadata runner.ScenarioMetadata) error {
	if r.known == nil {
		return fmt.Errorf("successful scenario registry is not loaded")
	}
	if r.Contains(metadata) {
		return nil
	}

	updated := append(append([]runner.ScenarioMetadata(nil), r.entries...), metadata)
	data, err := json.MarshalIndent(updated, "", "  ")
	if err != nil {
		return fmt.Errorf("encode successful scenario registry: %w", err)
	}
	if err := os.MkdirAll(r.OutputRoot, 0o755); err != nil {
		return fmt.Errorf("create registry directory: %w", err)
	}
	path := filepath.Join(r.OutputRoot, successfulScenariosFilename)
	if err := atomic.WriteFile(path, bytes.NewReader(append(data, '\n'))); err != nil {
		return fmt.Errorf("atomically replace successful scenario registry: %w", err)
	}

	r.entries = updated
	r.known[metadata] = struct{}{}
	return nil
}
