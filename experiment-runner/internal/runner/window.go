package runner

import (
	"fmt"
	"time"
)

// RunWindow is the shared logical window, independent of export and HTTP completion.
type RunWindow struct {
	WorkloadStartedAt    time.Time
	MeasurementStartedAt time.Time
	MeasurementEndedAt   time.Time
}

func (w RunWindow) Validate() error {
	if w.WorkloadStartedAt.IsZero() || w.MeasurementStartedAt.Before(w.WorkloadStartedAt) || !w.MeasurementEndedAt.After(w.MeasurementStartedAt) {
		return fmt.Errorf("invalid workload/measurement boundaries")
	}
	return nil
}

func NewRunWindow(start time.Time, warmup, duration int) RunWindow {
	start = start.UTC().Truncate(time.Microsecond)
	measurement := start.Add(time.Duration(warmup) * time.Second)
	return RunWindow{start, measurement, measurement.Add(time.Duration(duration) * time.Second)}
}

func (w RunWindow) Metadata() map[string]string {
	return map[string]string{
		"workload_started_at":    w.WorkloadStartedAt.UTC().Format(time.RFC3339Nano),
		"measurement_started_at": w.MeasurementStartedAt.UTC().Format(time.RFC3339Nano),
		"measurement_ended_at":   w.MeasurementEndedAt.UTC().Format(time.RFC3339Nano),
	}
}
