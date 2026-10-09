# Experiment files and columns dictionary

This document describes the files and columns produced by the runner in `output/experiments/<execution_id>/`. Definitions were checked against the Go exporters.

## Organization

```text
output/experiments/<execution_id>/
  scenarios.csv
  <scenario_id>/
    <repetition>.parquet
    <repetition>.resources.parquet
    <repetition>.queues.parquet
```

The numeric filename, such as `0001`, identifies a repetition. CSV supports text inspection; Parquet is a binary columnar format that preserves types and compresses data.

## Common conventions

| Column | Meaning |
| --- | --- |
| `execution_id` | Identifier of the complete application execution. |
| `scenario_id` | Identifier of the scenario within the execution. |
| `repetition` | Scenario repetition number. |

These three columns form a repetition key. Resource samples additionally identify the component and container; queue snapshots additionally identify the virtual host and queue name.

Timestamps are interpreted as UTC. Measurement uses the window `[measurement_started_at, measurement_ended_at)`: the start is included and the end is excluded. In schema v3, `duration` and `warmup_duration` are milliseconds; in v2, they are seconds. `max_start_delay` is expressed in milliseconds.

Null means unavailable information, rather than zero. Memory columns in the raw files are expressed in bytes. For CPU, 100% represents one occupied logical core; values above 100% are possible.

## Raw data

### scenarios.csv — schedule

One row per scheduled scenario/repetition. The file does not establish that a repetition completed and does not contain the actual runtime window boundaries.

| Column | Description |
| --- | --- |
| `ExecutionId` | Execution identifier. |
| `ScenarioId` | Scenario identifier. |
| `Events` | Configured events per second per integration process. |
| `Lambda` | Parameter of the truncated exponential distribution that places events within each second; it is not the total arrival rate. |
| `Duration` | Configured measurement duration, in ms in the current runner. |
| `IntegrationProcesses` | Number of processes generating load. |
| `MaxStartDelay` | Maximum random process preparation delay, in ms. |
| `Consumers` | Configured number of consumers. |
| `Repetition` | Repetition number. |
| `WarmupDuration` | Warm-up duration, in ms in the current runner. |

### <repetition>.parquet — events

One row per event exported from PostgreSQL. Includes warm-up observations, incomplete events, and completions after the cutoff. Time filters are applied during analysis.

In addition to the common key, it contains:

| Column | Description |
| --- | --- |
| `event_id` | Event identifier. |
| `status` | Exported status: `PROCESSED` indicates success; `FAILED`, failure; `PENDING` and `PROCESSING`, pending work. Successful pipeline completion also requires an outbound completion timestamp. |
| `consumers` | Configured number of consumers. The runner also sets concurrency and prefetch to this value. |
| `duration` | Configured measurement duration. |
| `events` | Configured events per second per process; not the actual number submitted. |
| `integration_processes` | Number of integration processes generating load. |
| `lambda` | Parameter of the distribution of event times within each second. |
| `max_start_delay` | Maximum process preparation delay, in ms. |
| `warmup_duration` | Configured warm-up duration. |
| `workload_started_at` | Logical workload start, including warm-up. |
| `measurement_started_at` | Measurement window start. |
| `measurement_ended_at` | Exclusive measurement window end. |
| `inbound_queue_published` | Publication timestamp at the inbound stage. |
| `inbound_queue_consumed` | Consumption timestamp at the inbound stage. |
| `inbound_queue_processing` | Recorded processing start timestamp at the inbound stage. |
| `inbound_queue_processed` | Recorded processing completion timestamp at the inbound stage. |
| `execution_queue_published` | Publication timestamp at the contract execution stage. |
| `execution_queue_consumed` | Consumption timestamp at the contract execution stage. |
| `execution_queue_processing` | Recorded processing start timestamp at the execution stage. |
| `execution_queue_processed` | Recorded processing completion timestamp at the execution stage. |
| `outbound_queue_published` | Publication timestamp at the outbound stage. |
| `outbound_queue_consumed` | Consumption timestamp at the outbound stage. |
| `outbound_queue_processing` | Recorded processing start timestamp at the outbound stage. |
| `outbound_queue_processed` | Final outbound stage completion timestamp, used for throughput and latency. |
| `created_at` | Event record creation timestamp. |

Stage timestamps may be null when a milestone was not observed/exported. Analyzed latency is `outbound_queue_processed - inbound_queue_published`; it excludes work before inbound publication.

### <repetition>.resources.parquet — resource samples

One row per container sample. Collection may start before the workload and finish after measurement. In addition to the common key, it contains:

| Column | Description |
| --- | --- |
| `warmup_duration` | Warm-up duration. |
| `workload_started_at` | Logical workload start. |
| `measurement_started_at` | Measurement start. |
| `measurement_ended_at` | Exclusive measurement end. |
| `component` | Component: `producer`, `consumer`, or `rabbitmq`. |
| `host` | Server hosting the monitored container. |
| `container_name` | Container name. |
| `container_id` | Docker identifier of the observed container. |
| `timestamp` | Sample timestamp recorded by the collector. |
| `docker_read_timestamp` | Read timestamp reported by Docker, used for the CPU interval. |
| `sample_duration_ms` | Time spent collecting the sample, in ms. |
| `cpu_percent` | CPU usage calculated from consecutive Docker counter readings. |
| `cpu_interval_ms` | Duration of the interval used to calculate CPU usage, in ms. |
| `memory_usage_bytes` | Total memory usage, including cache, in bytes. |
| `memory_working_set_bytes` | Total memory minus inactive cache reported by the cgroup, in bytes; this is not JVM heap usage. |
| `memory_limit_bytes` | Memory limit reported by Docker; may represent host memory when no specific limit is configured. |
| `sample_status` | Collection quality: `ok`, `partial`, or `error`. |
| `error_message` | Error message or reason for a partial sample, when available. |

Missing CPU or memory values are not replaced with zero. For example, CPU may be null when a valid previous reading is unavailable.

### <repetition>.queues.parquet — queue snapshot

One row per queue and virtual host, captured after consumer shutdown. Includes empty queues and dead-letter queues. In addition to the common key, it contains:

| Column | Description |
| --- | --- |
| `captured_at` | UTC capture timestamp. |
| `virtual_host` | RabbitMQ virtual host containing the queue. |
| `queue_name` | Queue name. |
| `messages_ready` | Available messages waiting to be consumed. |
| `messages_unacknowledged` | Delivered messages that have not yet been acknowledged. |
| `messages` | Total: `messages_ready + messages_unacknowledged`. |

These counts represent messages, not unique events. They may include warm-up and post-cutoff activity; they are not an exact snapshot of queues at the measurement cutoff.
