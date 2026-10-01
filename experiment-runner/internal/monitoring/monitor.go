package monitoring

import (
	"context"
	"fmt"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/runner"
	"github.com/parquet-go/parquet-go"
	"github.com/parquet-go/parquet-go/compress/zstd"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type Clock interface {
	Now() time.Time
	NewTicker(time.Duration) Ticker
}
type Ticker interface {
	C() <-chan time.Time
	Stop()
}
type realClock struct{}
type realTicker struct{ *time.Ticker }

func (realClock) Now() time.Time                   { return time.Now().UTC() }
func (realClock) NewTicker(d time.Duration) Ticker { return realTicker{time.NewTicker(d)} }
func (t realTicker) C() <-chan time.Time           { return t.Ticker.C }

type Counts struct{ Samples, Errors, Partial int }
type Summary struct {
	Components  map[string]Counts
	Destination string
}
type Session interface {
	Stop(context.Context) (Summary, error)
}
type Monitor struct {
	Targets           []Target
	Factory           Factory
	Interval, Timeout time.Duration
	Clock             Clock
}
type worker struct {
	target   Target
	client   Docker
	id       string
	previous *Stats
}
type session struct {
	cancel     context.CancelFunc
	done       chan struct{}
	once       sync.Once
	boundaryMu sync.Mutex
	end        time.Time
	clock      Clock
	summary    Summary
	err        error
}

func (s *session) Stop(ctx context.Context) (Summary, error) {
	s.once.Do(func() { s.boundaryMu.Lock(); s.end = s.clock.Now().UTC(); s.boundaryMu.Unlock(); s.cancel() })
	select {
	case <-s.done:
		return s.summary, s.err
	case <-ctx.Done():
		return Summary{}, ctx.Err()
	}
}
func (m *Monitor) Prepare(ctx context.Context, scenario runner.Scenario, destination string) (PreparedSession, error) {
	if m.Interval <= 0 || m.Timeout <= 0 || m.Factory == nil || len(m.Targets) == 0 {
		return nil, fmt.Errorf("invalid monitoring configuration")
	}
	clock := m.Clock
	if clock == nil {
		clock = realClock{}
	}
	file, err := createResourceFile(destination)
	if err != nil {
		return nil, err
	}
	workers, initial, err := m.preflight(ctx, scenario, clock)
	if err != nil {
		file.Close()
		os.Remove(file.Name())
		return nil, err
	}
	return preparedFunc(func(window runner.RunWindow) Session {
		scenario.Window = window
		for i := range initial {
			setWindow(&initial[i], scenario)
		}
		run, cancel := context.WithCancel(ctx)
		s := &session{clock: clock, cancel: cancel, done: make(chan struct{}), summary: Summary{Components: map[string]Counts{}, Destination: destination}}
		writer := m.resourceWriter(file, clock)
		for key, value := range window.Metadata() {
			writer.SetKeyValueMetadata(key, value)
		}
		rows := make(chan Sample, 128)
		m.launchWorkers(run, scenario, workers, initial, clock, rows)
		go s.writeResources(file, writer, rows)
		return s
	}), nil
}

type PreparedSession interface {
	Begin(runner.RunWindow) Session
}
type preparedFunc func(runner.RunWindow) Session

func (p preparedFunc) Begin(w runner.RunWindow) Session { return p(w) }

// Start retains the standalone monitoring entry point.
func (m *Monitor) Start(ctx context.Context, scenario runner.Scenario, destination string) (Session, error) {
	p, err := m.Prepare(ctx, scenario, destination)
	if err != nil {
		return nil, err
	}
	window := scenario.Window
	if window.WorkloadStartedAt.IsZero() {
		duration := scenario.Duration
		if duration <= 0 {
			duration = 1000
		}
		clock := m.Clock
		if clock == nil {
			clock = realClock{}
		}
		window = runner.NewRunWindow(clock.Now(), scenario.WarmupDuration, duration)
	}
	return p.Begin(window), nil
}

func setWindow(row *Sample, s runner.Scenario) {
	row.WarmupDuration = int64(s.WarmupDuration)
	row.WorkloadStartedAt = s.Window.WorkloadStartedAt
	row.MeasurementStartedAt = s.Window.MeasurementStartedAt
	row.MeasurementEndedAt = s.Window.MeasurementEndedAt
}
func createResourceFile(destination string) (*os.File, error) {
	if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return nil, err
	}
	file, err := os.CreateTemp(filepath.Dir(destination), ".resources-*.tmp")
	if err != nil {
		return nil, err
	}
	return file, nil
}

func (m *Monitor) preflight(ctx context.Context, scenario runner.Scenario, clock Clock) ([]worker, []Sample, error) {
	workers := make([]worker, 0, len(m.Targets))
	initial := make([]Sample, 0, len(m.Targets))
	for _, target := range m.Targets {
		w, row, err := m.preflightTarget(ctx, scenario, target, clock)
		if err != nil {
			for _, w := range workers {
				w.client.Close()
			}
			return nil, nil, err
		}
		workers = append(workers, w)
		initial = append(initial, row)
	}
	return workers, initial, nil
}

func (m *Monitor) preflightTarget(ctx context.Context, scenario runner.Scenario, t Target, clock Clock) (worker, Sample, error) {
	request, cancel := context.WithTimeout(ctx, m.Timeout)
	client, e := m.Factory(request, t)
	if e != nil {
		cancel()
		return worker{}, Sample{}, fmt.Errorf("preflight %s: %w", t.Component, e)
	}
	id, e := client.Resolve(request, t.Container)
	if e != nil {
		cancel()
		client.Close()
		return worker{}, Sample{}, fmt.Errorf("preflight %s: %w", t.Component, e)
	}
	w := worker{target: t, client: client, id: id}
	row := newSample(scenario, t, id, clock.Now())
	stats, e := client.Stats(request, id)
	row.SampleDurationMS = clock.Now().Sub(row.Timestamp).Milliseconds()
	cancel()
	if e != nil {
		client.Close()
		return worker{}, Sample{}, fmt.Errorf("preflight stats %s: %w", t.Component, e)
	}
	measurements(&row, stats, nil)
	w.previous = &stats
	return w, row, nil
}

func (m *Monitor) resourceWriter(file *os.File, clock Clock) *parquet.GenericWriter[Sample] {
	writer := parquet.NewGenericWriter[Sample](file, parquet.Compression(&zstd.Codec{}), parquet.MaxRowsPerRowGroup(128))
	writer.SetKeyValueMetadata("schema_version", "3")
	writer.SetKeyValueMetadata("sample_interval", m.Interval.String())
	writer.SetKeyValueMetadata("window_start", clock.Now().Format(time.RFC3339Nano))
	return writer
}

func (m *Monitor) launchWorkers(run context.Context, scenario runner.Scenario, workers []worker, initial []Sample, clock Clock, rows chan Sample) {
	var wg sync.WaitGroup
	for _, row := range initial {
		rows <- row
	}
	for _, w := range workers {
		wg.Add(1)
		go func(w worker) {
			defer wg.Done()
			m.runWorker(run, scenario, w, clock, rows)
		}(w)
	}
	go func() { wg.Wait(); close(rows) }()
}

func (m *Monitor) runWorker(run context.Context, scenario runner.Scenario, w worker, clock Clock, rows chan<- Sample) {
	defer func() {
		if w.client != nil {
			w.client.Close()
		}
	}()
	tick := clock.NewTicker(m.Interval)
	defer tick.Stop()
	for {
		select {
		case <-run.Done():
			return
		case <-tick.C():
		}
		if run.Err() != nil {
			return
		}
		row := m.sampleWorker(run, scenario, &w, clock)
		// Always persist an attempted request, including shutdown cancellation.
		rows <- row
		// Drain ticks accumulated during a slow request rather than burst-catching up.
		drainTicks(tick)
	}
}

func drainTicks(tick Ticker) {
	for {
		select {
		case <-tick.C():
		default:
			return
		}
	}
}

func (m *Monitor) sampleWorker(run context.Context, scenario runner.Scenario, w *worker, clock Clock) Sample {
	start := clock.Now()
	row := newSample(scenario, w.target, w.id, start)
	request, stop := context.WithTimeout(run, m.Timeout)
	var e error
	if w.client == nil {
		w.client, e = m.Factory(request, w.target)
	}
	if e == nil && w.id == "" {
		w.id, e = w.client.Resolve(request, w.target.Container)
		row.ContainerID = nil
		if w.id != "" {
			id := w.id
			row.ContainerID = &id
		}
	}
	var stats Stats
	if e == nil {
		stats, e = w.client.Stats(request, w.id)
	}
	stop()
	row.SampleDurationMS = clock.Now().Sub(start).Milliseconds()
	if e != nil {
		row.SampleStatus = "error"
		row.ErrorMessage = diagnostic(e.Error())
		w.previous = nil
		if w.client != nil {
			w.client.Close()
			w.client = nil
		}
		w.id = ""
	} else {
		measurements(&row, stats, w.previous)
		w.previous = &stats
	}
	return row
}

func (s *session) writeResources(file *os.File, writer *parquet.GenericWriter[Sample], rows <-chan Sample) {
	defer close(s.done)
	defer file.Close()
	defer os.Remove(file.Name())
	s.collectRows(writer, rows)
	s.finalizeResources(file, writer)
}

func (s *session) collectRows(writer *parquet.GenericWriter[Sample], rows <-chan Sample) {
	batch := make([]Sample, 0, 64)
	flush := func() {
		if len(batch) > 0 && s.err == nil {
			_, s.err = writer.Write(batch)
		}
		batch = batch[:0]
	}
	for row := range rows {
		s.countSample(row)
		batch = append(batch, row)
		if len(batch) == cap(batch) {
			flush()
		}
	}
	flush()
}

func (s *session) countSample(row Sample) {
	c := s.summary.Components[row.Component]
	c.Samples++
	if row.SampleStatus == "error" {
		c.Errors++
	}
	if row.SampleStatus == "partial" {
		c.Partial++
	}
	s.summary.Components[row.Component] = c
}

func (s *session) finalizeResources(file *os.File, writer *parquet.GenericWriter[Sample]) {
	s.boundaryMu.Lock()
	end := s.end
	if end.IsZero() {
		end = s.clock.Now().UTC()
	}
	s.boundaryMu.Unlock()
	writer.SetKeyValueMetadata("window_end", end.Format(time.RFC3339Nano))
	if e := writer.Close(); s.err == nil {
		s.err = e
	}
	if s.err == nil {
		s.err = file.Sync()
	}
	if e := file.Close(); s.err == nil {
		s.err = e
	}
	if s.err == nil {
		s.err = s.verifyResourceRows(file.Name())
	}
	if s.err == nil {
		s.err = os.Rename(file.Name(), s.summary.Destination)
	}
}

func (s *session) verifyResourceRows(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		return err
	}
	pf, err := parquet.OpenFile(f, info.Size())
	if err != nil {
		return err
	}
	var count int64
	for _, c := range s.summary.Components {
		count += int64(c.Samples)
	}
	if pf.NumRows() != count {
		return fmt.Errorf("resource row count mismatch")
	}
	return nil
}

func newSample(s runner.Scenario, t Target, id string, now time.Time) Sample {
	row := Sample{ExecutionID: s.ExecutionID, ScenarioID: s.ScenarioID, Repetition: int32(s.Repetition), Component: t.Component, Host: t.Host, ContainerName: t.Container, Timestamp: now.UTC()}
	setWindow(&row, s)
	if id != "" {
		row.ContainerID = &id
	}
	return row
}
