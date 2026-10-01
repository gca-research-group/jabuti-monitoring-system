package runner

import (
	"fmt"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/api"
	"github.com/gca-research-group/jabuti-monitoring-system-experiments/internal/config"
	"sync"
	"time"
)

func (e *Executor) Prepare(s Scenario) error {
	if err := e.Validate(); err != nil {
		return err
	}
	if err := (config.Parameters{Duration: s.Duration, WarmupDuration: s.WarmupDuration}).ValidateTiming(); err != nil {
		return err
	}
	var ready sync.WaitGroup
	for i := 0; i < s.IntegrationProcesses; i++ {
		ready.Add(1)
		go func() { defer ready.Done(); e.Sleep(e.startDelay(s.MaxStartDelay)) }()
	}
	ready.Wait()
	return nil
}
func (e *Executor) now() time.Time {
	if e.Now != nil {
		return e.Now()
	}
	return time.Now()
}
func (e *Executor) waitUntil(t time.Time) {
	if e.WaitUntil != nil {
		e.WaitUntil(t)
		return
	}
	if d := t.Sub(e.now()); d > 0 {
		e.Sleep(d)
	}
}

func (e *Executor) RunPrepared(s Scenario) error {
	if err := e.Validate(); err != nil {
		return err
	}
	if err := s.Window.Validate(); err != nil {
		return err
	}
	if err := (config.Parameters{Duration: s.Duration, WarmupDuration: s.WarmupDuration}).ValidateTiming(); err != nil {
		return err
	}
	if s.Window.MeasurementStartedAt.Sub(s.Window.WorkloadStartedAt) != time.Duration(s.WarmupDuration)*time.Second || s.Window.MeasurementEndedAt.Sub(s.Window.MeasurementStartedAt) != time.Duration(s.Duration)*time.Second {
		return fmt.Errorf("window does not match scenario durations")
	}
	// Preserve a monotonic clock anchor while accounting for collection startup.
	now := e.now()
	anchor := now.Add(s.Window.WorkloadStartedAt.Sub(now))
	end := anchor.Add(s.Window.MeasurementEndedAt.Sub(s.Window.WorkloadStartedAt))
	var requests sync.WaitGroup
	counters := &requestCounters{failures: make(map[string]uint64)}
	for second := 0; second < s.WarmupDuration+s.Duration; second++ {
		bucket := anchor.Add(time.Duration(second) * time.Second)
		e.waitUntil(bucket)
		if !e.now().Before(end) {
			break
		}
		if !e.now().Before(bucket.Add(time.Second)) {
			continue
		}
		for process := 0; process < s.IntegrationProcesses; process++ {
			for _, interval := range e.generateEvents(s.Events, s.Lambda) {
				deadline := bucket.Add(interval)
				if deadline.Before(e.now()) {
					continue
				}
				requests.Add(1)
				go func() {
					defer requests.Done()
					e.waitUntil(deadline)
					if !e.now().Before(end) {
						return
					}
					counters.sent.Add(1)
					if err := e.Client.ExecuteSmartContract(e.Token, BuildMessage(e.Env, s)); err != nil {
						counters.failed.Add(1)
						counters.recordFailure(api.ClassifyExecutionFailure(err))
						return
					}
					counters.successful.Add(1)
				}()
			}
		}
	}
	e.waitUntil(end)
	requests.Wait()
	e.Logf("%s", formatRequestSummary(s, counters))
	return nil
}
