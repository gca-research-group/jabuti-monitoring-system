package monitoring

import (
	"context"
	"errors"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"github.com/parquet-go/parquet-go"
	"github.com/parquet-go/parquet-go/format"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"
)

func TestMeasurements(t *testing.T) {
	base := Stats{}
	base.Read = time.Now().UTC()
	base.CPU.System = 100
	base.CPU.Usage.Total = 10
	current := base
	current.Read = base.Read.Add(time.Second)
	current.CPU.System = 200
	current.CPU.Usage.Total = 60
	current.CPU.Online = 4
	current.Memory.Usage = 1000
	current.Memory.Limit = 2000
	for _, key := range []string{"inactive_file", "total_inactive_file"} {
		current.Memory.Stats = map[string]uint64{key: 200}
		var row Sample
		measurements(&row, current, &base)
		if row.CPUPercent == nil || *row.CPUPercent != 200 || *row.MemoryWorkingSetBytes != 800 || row.SampleStatus != "ok" {
			t.Fatalf("bad measurement: %+v", row)
		}
	}
	for _, change := range []func(*Stats){func(s *Stats) { s.CPU.System = 100 }, func(s *Stats) { s.CPU.Usage.Total = 1 }, func(s *Stats) { s.CPU.Online = 0 }} {
		c := current
		change(&c)
		var row Sample
		measurements(&row, c, &base)
		if row.CPUPercent != nil {
			t.Fatal("invalid CPU should be null")
		}
	}
	current.Memory.Stats = map[string]uint64{"inactive_file": 1001}
	var row Sample
	measurements(&row, current, nil)
	if row.MemoryWorkingSetBytes != nil || row.CPUPercent != nil || row.SampleStatus != "partial" {
		t.Fatal(row)
	}
}

type fakeDocker struct {
	mu     sync.Mutex
	calls  int
	closed bool
}

func (d *fakeDocker) Resolve(context.Context, string) (string, error) { return "id", nil }
func (d *fakeDocker) Stats(ctx context.Context, _ string) (Stats, error) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.calls++
	if d.calls > 1 {
		return Stats{}, errors.New("unavailable")
	}
	s := Stats{}
	s.Read = time.Now().UTC()
	s.Memory.Usage = 123
	s.Memory.Stats = map[string]uint64{"inactive_file": 23}
	return s, nil
}
func (d *fakeDocker) Close() error { d.mu.Lock(); d.closed = true; d.mu.Unlock(); return nil }
func TestSessionRoundTripAndStop(t *testing.T) {
	var clients []*fakeDocker
	var mu sync.Mutex
	m := Monitor{Targets: []Target{{Component: "producer", Container: "api"}}, Interval: time.Millisecond, Timeout: time.Second, Factory: func(context.Context, Target) (Docker, error) {
		d := &fakeDocker{}
		mu.Lock()
		clients = append(clients, d)
		mu.Unlock()
		return d, nil
	}}
	path := filepath.Join(t.TempDir(), "resources.parquet")
	s, err := m.Start(context.Background(), runner.Scenario{ExecutionID: "execution", ScenarioID: "scenario", Repetition: 1}, path)
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(10 * time.Millisecond)
	summary, err := s.Stop(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	again, err := s.Stop(context.Background())
	if err != nil || again.Components["producer"] != summary.Components["producer"] {
		t.Fatal("non-idempotent Stop")
	}
	file, e := os.Open(path)
	if e != nil {
		t.Fatal(e)
	}
	defer file.Close()
	info, _ := file.Stat()
	pf, e := parquet.OpenFile(file, info.Size())
	if e != nil {
		t.Fatal(e)
	}
	for _, key := range []string{"schema_version", "sample_interval", "window_start", "window_end"} {
		if value, ok := pf.Lookup(key); !ok || value == "" {
			t.Fatalf("missing metadata %s", key)
		}
	}
	for _, group := range pf.Metadata().RowGroups {
		for _, column := range group.Columns {
			if column.MetaData.Codec != format.Zstd {
				t.Fatal("not Zstandard")
			}
		}
	}
	rows, err := parquet.ReadFile[Sample](path)
	if err != nil {
		t.Fatal(err)
	}
	if len(rows) < 2 || rows[0].CPUPercent != nil || *rows[0].MemoryUsageBytes != 123 {
		t.Fatal(rows)
	}
	found := false
	for _, row := range rows {
		if row.SampleStatus == "error" {
			found = true
			if row.MemoryUsageBytes != nil {
				t.Fatal("fabricated error memory")
			}
		}
	}
	if !found {
		t.Fatal("missing error row")
	}
	mu.Lock()
	defer mu.Unlock()
	for _, d := range clients {
		d.mu.Lock()
		closed := d.closed
		d.mu.Unlock()
		if !closed {
			t.Fatal("connection leak")
		}
	}
	files, _ := filepath.Glob(filepath.Join(filepath.Dir(path), "*.tmp"))
	if len(files) > 0 {
		t.Fatal(files)
	}
}

type blockedDocker struct{ fakeDocker }

func (d *blockedDocker) Stats(ctx context.Context, id string) (Stats, error) {
	d.mu.Lock()
	first := d.calls == 0
	d.mu.Unlock()
	if first {
		return d.fakeDocker.Stats(ctx, id)
	}
	<-ctx.Done()
	return Stats{}, ctx.Err()
}
func TestIndependentWorkersAndDeadlines(t *testing.T) {
	m := Monitor{Targets: []Target{{Component: "slow"}, {Component: "fast"}}, Interval: time.Millisecond, Timeout: 5 * time.Millisecond, Factory: func(_ context.Context, target Target) (Docker, error) {
		if target.Component == "slow" {
			return &blockedDocker{}, nil
		}
		return &fakeDocker{}, nil
	}}
	s, err := m.Start(context.Background(), runner.Scenario{}, filepath.Join(t.TempDir(), "r.parquet"))
	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(30 * time.Millisecond)
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	summary, err := s.Stop(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if summary.Components["slow"].Errors == 0 || summary.Components["fast"].Samples <= summary.Components["slow"].Samples {
		t.Fatal(summary)
	}
}
func TestPreflightFailureCleansTemporaryFile(t *testing.T) {
	dir := t.TempDir()
	m := Monitor{Targets: []Target{{Component: "producer"}}, Interval: time.Second, Timeout: time.Second, Factory: func(context.Context, Target) (Docker, error) { return nil, errors.New("socket denied") }}
	if _, err := m.Start(context.Background(), runner.Scenario{}, filepath.Join(dir, "r.parquet")); err == nil {
		t.Fatal("expected preflight error")
	}
	entries, _ := os.ReadDir(dir)
	if len(entries) != 0 {
		t.Fatal("temporary file leaked")
	}
}
func TestPublicationFailure(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "existing")
	if err := os.Mkdir(path, 0755); err != nil {
		t.Fatal(err)
	}
	m := Monitor{Targets: []Target{{Component: "producer"}}, Interval: time.Second, Timeout: time.Second, Factory: func(context.Context, Target) (Docker, error) { return &fakeDocker{}, nil }}
	s, err := m.Start(context.Background(), runner.Scenario{}, path)
	if err != nil {
		t.Fatal(err)
	}
	if _, err = s.Stop(context.Background()); err == nil {
		t.Fatal("expected publication error")
	}
	files, _ := filepath.Glob(filepath.Join(dir, "*.tmp"))
	if len(files) > 0 {
		t.Fatal(files)
	}
}
