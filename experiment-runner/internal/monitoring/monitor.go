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
func (m *Monitor) Start(ctx context.Context, scenario runner.Scenario, destination string) (Session, error) {
	if m.Interval <= 0 || m.Timeout <= 0 || m.Factory == nil || len(m.Targets) == 0 {
		return nil, fmt.Errorf("invalid monitoring configuration")
	}
	clock := m.Clock
	if clock == nil {
		clock = realClock{}
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0755); err != nil {
		return nil, err
	}
	file, err := os.CreateTemp(filepath.Dir(destination), ".resources-*.tmp")
	if err != nil {
		return nil, err
	}
	workers := make([]worker, 0, len(m.Targets))
	cleanup := func() {
		for _, w := range workers {
			w.client.Close()
		}
		file.Close()
		os.Remove(file.Name())
	}
	initial := make([]Sample, 0, len(m.Targets))
	for _, t := range m.Targets {
		request, cancel := context.WithTimeout(ctx, m.Timeout)
		client, e := m.Factory(request, t)
		if e != nil {
			cancel()
			cleanup()
			return nil, fmt.Errorf("preflight %s: %w", t.Component, e)
		}
		id, e := client.Resolve(request, t.Container)
		if e != nil {
			cancel()
			client.Close()
			cleanup()
			return nil, fmt.Errorf("preflight %s: %w", t.Component, e)
		}
		w := worker{target: t, client: client, id: id}
		row := newSample(scenario, t, id, clock.Now())
		stats, e := client.Stats(request, id)
		row.SampleDurationMS = clock.Now().Sub(row.Timestamp).Milliseconds()
		cancel()
		if e != nil {
			client.Close()
			cleanup()
			return nil, fmt.Errorf("preflight stats %s: %w", t.Component, e)
		}
		measurements(&row, stats, nil)
		w.previous = &stats
		workers = append(workers, w)
		initial = append(initial, row)
	}
	run, cancel := context.WithCancel(ctx)
	s := &session{clock: clock, cancel: cancel, done: make(chan struct{}), summary: Summary{Components: map[string]Counts{}, Destination: destination}}
	writer := parquet.NewGenericWriter[Sample](file, parquet.Compression(&zstd.Codec{}), parquet.MaxRowsPerRowGroup(128))
	writer.SetKeyValueMetadata("schema_version", "1")
	writer.SetKeyValueMetadata("sample_interval", m.Interval.String())
	writer.SetKeyValueMetadata("window_start", clock.Now().Format(time.RFC3339Nano))
	rows := make(chan Sample, 128)
	var wg sync.WaitGroup
	for _, row := range initial {
		rows <- row
	}
	for _, w := range workers {
		wg.Add(1)
		go func(w worker) {
			defer wg.Done()
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
				// Always persist an attempted request, including shutdown cancellation.
				rows <- row
				// Drain ticks accumulated during a slow request rather than burst-catching up.
				draining := true
				for draining {
					select {
					case <-tick.C():
					default:
						draining = false
					}
				}
			}
		}(w)
	}
	go func() { wg.Wait(); close(rows) }()
	go func() {
		defer close(s.done)
		defer file.Close()
		defer os.Remove(file.Name())
		batch := make([]Sample, 0, 64)
		flush := func() {
			if len(batch) > 0 && s.err == nil {
				_, s.err = writer.Write(batch)
			}
			batch = batch[:0]
		}
		for row := range rows {
			c := s.summary.Components[row.Component]
			c.Samples++
			if row.SampleStatus == "error" {
				c.Errors++
			}
			if row.SampleStatus == "partial" {
				c.Partial++
			}
			s.summary.Components[row.Component] = c
			batch = append(batch, row)
			if len(batch) == cap(batch) {
				flush()
			}
		}
		flush()
		s.boundaryMu.Lock()
		end := s.end
		if end.IsZero() {
			end = clock.Now().UTC()
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
			f, e := os.Open(file.Name())
			if e == nil {
				info, se := f.Stat()
				if se == nil {
					var pf *parquet.File
					pf, e = parquet.OpenFile(f, info.Size())
					if e == nil {
						var count int64
						for _, c := range s.summary.Components {
							count += int64(c.Samples)
						}
						if pf.NumRows() != count {
							e = fmt.Errorf("resource row count mismatch")
						}
					}
				} else {
					e = se
				}
				f.Close()
			}
			s.err = e
		}
		if s.err == nil {
			s.err = os.Rename(file.Name(), destination)
		}
	}()
	return s, nil
}
func newSample(s runner.Scenario, t Target, id string, now time.Time) Sample {
	row := Sample{ExecutionID: s.ExecutionID, ScenarioID: s.ScenarioID, Repetition: int32(s.Repetition), Component: t.Component, Host: t.Host, ContainerName: t.Container, Timestamp: now.UTC()}
	if id != "" {
		row.ContainerID = &id
	}
	return row
}
