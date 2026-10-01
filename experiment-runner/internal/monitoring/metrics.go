package monitoring

import "time"

type Stats struct {
	Read time.Time `json:"read"`
	CPU  struct {
		Usage struct {
			Total  uint64   `json:"total_usage"`
			PerCPU []uint64 `json:"percpu_usage"`
		} `json:"cpu_usage"`
		System uint64 `json:"system_cpu_usage"`
		Online uint32 `json:"online_cpus"`
	} `json:"cpu_stats"`
	Memory struct {
		Usage uint64            `json:"usage"`
		Limit uint64            `json:"limit"`
		Stats map[string]uint64 `json:"stats"`
	} `json:"memory_stats"`
}
type Sample struct {
	WarmupDuration        int64      `parquet:"warmup_duration"`
	WorkloadStartedAt     time.Time  `parquet:"workload_started_at,timestamp(microsecond)"`
	MeasurementStartedAt  time.Time  `parquet:"measurement_started_at,timestamp(microsecond)"`
	MeasurementEndedAt    time.Time  `parquet:"measurement_ended_at,timestamp(microsecond)"`
	ExecutionID           string     `parquet:"execution_id"`
	ScenarioID            string     `parquet:"scenario_id"`
	Repetition            int32      `parquet:"repetition"`
	Component             string     `parquet:"component"`
	Host                  string     `parquet:"host"`
	ContainerName         string     `parquet:"container_name"`
	ContainerID           *string    `parquet:"container_id"`
	Timestamp             time.Time  `parquet:"timestamp"`
	DockerReadTimestamp   *time.Time `parquet:"docker_read_timestamp"`
	SampleDurationMS      int64      `parquet:"sample_duration_ms"`
	CPUPercent            *float64   `parquet:"cpu_percent"`
	CPUIntervalMS         *int64     `parquet:"cpu_interval_ms"`
	MemoryUsageBytes      *uint64    `parquet:"memory_usage_bytes"`
	MemoryWorkingSetBytes *uint64    `parquet:"memory_working_set_bytes"`
	MemoryLimitBytes      *uint64    `parquet:"memory_limit_bytes"`
	SampleStatus          string     `parquet:"sample_status"`
	ErrorMessage          *string    `parquet:"error_message"`
}

func measurements(row *Sample, current Stats, previous *Stats) {
	row.SampleStatus = "ok"
	if !current.Read.IsZero() {
		t := current.Read.UTC()
		row.DockerReadTimestamp = &t
	}
	row.MemoryUsageBytes = &current.Memory.Usage
	row.MemoryLimitBytes = &current.Memory.Limit
	cache, ok := current.Memory.Stats["total_inactive_file"]
	if !ok {
		cache, ok = current.Memory.Stats["inactive_file"]
	}
	if ok && cache <= current.Memory.Usage {
		value := current.Memory.Usage - cache
		row.MemoryWorkingSetBytes = &value
	} else {
		row.SampleStatus = "partial"
		if ok {
			row.ErrorMessage = diagnostic("inactive file cache exceeds memory usage")
		}
	}
	cpus := current.CPU.Online
	if cpus == 0 {
		cpus = uint32(len(current.CPU.Usage.PerCPU))
	}
	if previous != nil && current.CPU.Usage.Total >= previous.CPU.Usage.Total && current.CPU.System > previous.CPU.System && cpus > 0 && current.Read.After(previous.Read) {
		value := float64(current.CPU.Usage.Total-previous.CPU.Usage.Total) / float64(current.CPU.System-previous.CPU.System) * float64(cpus) * 100
		interval := current.Read.Sub(previous.Read).Milliseconds()
		row.CPUPercent = &value
		row.CPUIntervalMS = &interval
	} else {
		row.SampleStatus = "partial"
	}
}
func diagnostic(s string) *string {
	if len(s) > 1024 {
		s = s[:1024]
	}
	return &s
}
