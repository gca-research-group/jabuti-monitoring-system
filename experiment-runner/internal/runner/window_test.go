package runner

import (
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"math/rand"
	"sync"
	"testing"
	"time"
)

func TestWindowAndStableWarmupIdentity(t *testing.T) {
	start := time.Date(2026, 10, 1, 10, 0, 0, 123456789, time.FixedZone("test", -10800))
	w := NewRunWindow(start, 30, 120)
	if w.Validate() != nil || w.WorkloadStartedAt.Location() != time.UTC || w.WorkloadStartedAt.Nanosecond() != 123456000 || w.MeasurementStartedAt.Sub(w.WorkloadStartedAt) != 30*time.Second || w.MeasurementEndedAt.Sub(w.MeasurementStartedAt) != 120*time.Second {
		t.Fatal(w)
	}
	s := GenerateScenarios(config.Parameters{Events: []int{1}, IntegrationProcesses: []int{1}, Consumers: []int{1}, Repetitions: 1, Duration: 120, WarmupDuration: 30}, rand.New(rand.NewSource(1)))[0]
	original := s.Metadata()
	s.Window = w
	if s.Metadata() != original || original.WarmupDuration != 30 || original.TimingProtocolVersion != 2 {
		t.Fatal(s)
	}
	if BuildMessage(&config.Env{}, s).Metadata["WarmupDuration"] != 30 {
		t.Fatal("missing request warmup")
	}
	s.WarmupDuration++
	if s.Metadata() == original {
		t.Fatal("warmup omitted from identity")
	}
}

type clockedClient struct{ call func() }

func (c clockedClient) ExecuteSmartContract(string, api.SmartContractMessage) error {
	c.call()
	return nil
}

// A fixed injected clock records scheduling without wall-time sleeps. The end
// wait releases only after each expected request has reached the client.
func TestContinuousWarmupSchedulingAndHTTPDrain(t *testing.T) {
	start := time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC)
	s := Scenario{Events: 1, Lambda: 0.5, IntegrationProcesses: 2, WarmupDuration: 1, Duration: 2, MaxStartDelay: 10, Window: NewRunWindow(start, 1, 2)}
	var mu sync.Mutex
	var deadlines []time.Time
	var delays int
	arrived := make(chan struct{}, 6)
	release := make(chan struct{})
	done := make(chan error, 1)
	e := NewExecutor(clockedClient{func() { arrived <- struct{}{}; <-release }}, &config.Env{}, "", rand.New(rand.NewSource(1)))
	e.Now = func() time.Time { return start }
	e.Sleep = func(d time.Duration) {
		mu.Lock()
		delays++
		mu.Unlock()
		if d < 0 || d > 10*time.Millisecond {
			t.Errorf("preparation delay %v", d)
		}
	}
	e.Logf = func(string, ...any) {}
	e.WaitUntil = func(d time.Time) {
		mu.Lock()
		deadlines = append(deadlines, d)
		mu.Unlock()
		if d.Equal(s.Window.MeasurementEndedAt) {
			for i := 0; i < 6; i++ {
				<-arrived
			}
			close(release)
		}
	}
	if err := e.Prepare(s); err != nil {
		t.Fatal(err)
	}
	if delays != 2 {
		t.Fatalf("readiness delays %d", delays)
	}
	go func() { done <- e.RunPrepared(s) }()
	select {
	case err := <-done:
		if err != nil {
			t.Fatal(err)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("executor did not complete")
	}
	mu.Lock()
	defer mu.Unlock()
	buckets := map[int]int{}
	for _, d := range deadlines {
		if d.Before(s.Window.MeasurementEndedAt) {
			buckets[int(d.Sub(start)/time.Second)]++
		}
	}
	for second := 0; second < 3; second++ {
		if buckets[second] != 3 {
			t.Fatalf("second %d deadlines = %d; want bucket and two events", second, buckets[second])
		}
	}
	if !s.Window.MeasurementEndedAt.Equal(start.Add(3 * time.Second)) {
		t.Fatal("HTTP drain changed cutoff")
	}
}

func TestDeadlineDispatchAndLateBucketSkipping(t *testing.T) {
	for _, mode := range []string{"expired dispatch", "late buckets"} {
		t.Run(mode, func(t *testing.T) {
			start := time.Date(2026, 10, 1, 0, 0, 0, 0, time.UTC)
			s := Scenario{Events: 1, Lambda: 0.5, IntegrationProcesses: 1, Duration: 3, Window: NewRunWindow(start, 0, 3)}
			var mu sync.Mutex
			now := start
			calls := 0
			eventDone := make(chan struct{}, 1)
			e := NewExecutor(clockedClient{func() { mu.Lock(); calls++; mu.Unlock(); eventDone <- struct{}{} }}, &config.Env{}, "", rand.New(rand.NewSource(1)))
			e.Now = func() time.Time { mu.Lock(); defer mu.Unlock(); return now }
			e.Logf = func(string, ...any) {}
			e.WaitUntil = func(d time.Time) {
				mu.Lock()
				if mode == "late buckets" && d.Equal(start) {
					now = start.Add(2 * time.Second)
				}
				if mode == "expired dispatch" && d.Sub(start)%time.Second != 0 {
					now = s.Window.MeasurementEndedAt
				}
				mu.Unlock()
				if mode == "late buckets" && d.Equal(s.Window.MeasurementEndedAt) {
					<-eventDone
				}
			}
			if err := e.RunPrepared(s); err != nil {
				t.Fatal(err)
			}
			want := 0
			if mode == "late buckets" {
				want = 1
			}
			if calls != want {
				t.Fatalf("calls %d want %d", calls, want)
			}
		})
	}
}

func TestPreparationWaitsForEveryProcess(t *testing.T) {
	entered := make(chan struct{}, 2)
	release := make(chan struct{})
	done := make(chan error, 1)
	e := NewExecutor(clockedClient{func() { t.Error("dispatched in preparation") }}, &config.Env{}, "", rand.New(rand.NewSource(1)))
	e.Sleep = func(time.Duration) { entered <- struct{}{}; <-release }
	go func() { done <- e.Prepare(Scenario{Duration: 1, IntegrationProcesses: 2}) }()
	for i := 0; i < 2; i++ {
		select {
		case <-entered:
		case <-time.After(5 * time.Second):
			t.Fatal("preparation did not start")
		}
	}
	select {
	case <-done:
		t.Fatal("preparation returned before readiness")
	default:
	}
	close(release)
	if err := <-done; err != nil {
		t.Fatal(err)
	}
}
