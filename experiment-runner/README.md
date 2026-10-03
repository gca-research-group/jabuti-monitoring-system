<h1 align="center">
  Jabuti Monitoring System Experiments
  <br>
</h1>

<div align="center">

🚧 **This project is currently under development.** 🚧  
Expect frequent updates and changes. Your feedback is appreciated!

</div>

## Overview

Application logs are printed to the terminal and appended to `logs/app.log` in JSON format.
Unhandled panic and runtime-fatal diagnostics are also copied to that file as plain-text
Go stack traces.

This project centralizes the experiments that evaluate the [Fabric Network Orchestrator](https://github.com/gca-research-group/hyperledger-fabric-development-network-manager) and the [Jabuti Monitoring System](https://github.com/gca-research-group/jabuti-monitoring-system). It automates the execution of various benchmark scenarios to measure the performance and scalability of Jabuti Monitoring System.

## Table of contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Configuration](#configuration)
  - [Running the Experiments](#running-the-experiments)
- [Project repositories](#project-repositories)
- [Related Publications](#related-publications)
- [License](#license)
- [Contact](#contact)

## Project Structure

Offline experiment metrics are available in [`python-metrics`](python-metrics/README.md).
Set the top-level `PARQUET_FOLDER` in `python-metrics/run_metrics.py`, then run
`uv sync --locked` and `uv run --locked run_metrics.py` from that directory. The
package reports throughput, latency percentiles, layered errors, container resources,
and data quality without executing experiments.

- `cmd/experiments/main.go`: The main entry point that orchestrates the execution of various scenarios.
- `internal/api/`: Contains the HTTP client used to interact with the benchmark API and execute smart contracts.
- `internal/config/`: Handles configuration loading from environment variables and `.env` files.
- `internal/report/`: Provides functionality to save experiment metadata and results (e.g., `scenarios.csv`).
- `internal/runner/`: Contains the core logic for generating scenarios and managing parallel execution with randomized event intervals.

## Getting Started

### Prerequisites

- **Go** (version 1.26 or higher)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/gca-research-group/jabuti-monitoring-system-experiments
   ```

2. Install the dependencies:
   ```bash
   go mod tidy
   ```

### Configuration

Before running the experiments, you need to configure the environment variables. You can create a `.env` file based on the provided example:

```bash
cp .env.example .env
```

Edit the `.env` file with your specific settings:

- `API_BASE_URL`: The URL of the benchmark API.
- `ADMIN_EMAIL`: Admin email for authentication.
- `ADMIN_PASSWORD`: Admin password for authentication.
- `BLOCKCHAIN_ID`: The ID of the blockchain network to use.
- `SMART_CONTRACT_ID`: The ID of the smart contract to execute.
- `DATABASE_URL`: PostgreSQL connection string used to export results before each database reset.
- `FABRIC_SERVER_IP`, `RABBITMQ_SERVER_IP`, `POSTGRES_SERVER_IP`: Addresses of the infrastructure servers reset between scenarios.
- `<SERVER>_SSH_USER`: Required SSH login for `FABRIC`, `RABBITMQ`, and `POSTGRES`.
- `<SERVER>_SSH_PORT`: SSH port for those infrastructure servers; defaults to `22`.
- `API_PRODUCER_SSH_SERVER`, `API_PRODUCER_SSH_USER`, `API_PRODUCER_SSH_PORT`: Producer SSH host, required login, and port (defaults to `22`).
- `API_CONSUMER_SSH_SERVER`, `API_CONSUMER_SSH_USER`, `API_CONSUMER_SSH_PORT`: Consumer SSH host, required login, and port (defaults to `22`).
- `EXPERIMENT_OUTPUT_DIR`: Dataset root (defaults to `output/experiments`).
- `HTTP_MAX_IDLE_CONNS`: Maximum idle connections retained across all API hosts (defaults to `3000`).
- `HTTP_MAX_IDLE_CONNS_PER_HOST`: Maximum idle connections retained for one API host (defaults to `3000`).
- `HTTP_IDLE_CONN_TIMEOUT`: How long an idle connection remains reusable (defaults to `90s`).
- `HTTP_RESPONSE_HEADER_TIMEOUT`: Maximum wait for API response headers (defaults to `15s`).
- `HTTP_REQUEST_TIMEOUT`: Overall timeout for an API request, including its response body (defaults to `60s`).

The HTTP idle connection limits allow established TCP connections to be reused during
high-load experiments. They do not limit concurrent or in-flight requests. Connection
counts must be positive integers, and timeout values use Go duration syntax such as
`500ms`, `15s`, or `2m`.

The SSH client reads `%USERPROFILE%\.ssh\id_ed25519` on Windows (or `~/.ssh/id_ed25519` on Unix). Configure each server to authorize that key for its corresponding SSH user. `FABRIC_PRIVATE_KEY_PATH` is the remote Fabric certificate key path; it does not configure SSH authentication.

### Running the Experiments

To start the experiment suite, run the following command:

```bash
go run cmd/experiments/main.go
```

In scenario configuration files, `duration`, `warmupDuration`, and `maxStartDelay` are expressed in milliseconds.
Convert existing `duration` and `warmupDuration` values by multiplying by 1000
(for example, 300 seconds becomes `300000`). Event rates remain events per second.

The system will:
1. Generate a series of scenarios with varying parameters (events, parallels, consumers).
2. Create an execution dataset and save its schedule to `scenarios.csv`.
3. Sequentially execute each scenario repetition.
4. Finish workload and resource monitoring, call `/rabbitmq/stop`, and stop the separate consumer container over SSH. RabbitMQ stays running.
5. Inspect all RabbitMQ virtual hosts and queues (including dead-letter queues), save ready, unacknowledged, and total message counts to `<repetition>.queues.parquet`, and log each queue.
6. Query PostgreSQL and atomically save event results before registering success and allowing the next reset.

Queue snapshots are always collected, including when resource monitoring is disabled. They use the existing RabbitMQ and consumer SSH credentials and `RESOURCE_RABBITMQ_CONTAINER` / `RESOURCE_CONSUMER_CONTAINER` settings. Each snapshot row contains `execution_id`, `scenario_id`, `repetition`, `captured_at` (UTC), `virtual_host`, `queue_name`, `messages_ready`, `messages_unacknowledged`, and `messages`. Files use Zstandard compression and atomic publication; zero-count queues are included. Counts describe the post-shutdown capture, which can occur after the measurement cutoff; queues are never purged or drained.

Shutdown and event export are attempted even when workload execution or monitoring fails. A shutdown failure prevents queue collection. Any shutdown, inspection, snapshot-write, or event-export failure aborts before reset and success registration, preserves saved artifacts, and reports combined errors. The next successful infrastructure reset recreates the consumer worker.

### Experiment dataset

Each invocation produces the following dataset:

```text
output/experiments/
├── successful-scenarios.json
└── <execution-uuid>/
    ├── scenarios.csv
    └── <scenario-uuid>/
        ├── 0001.parquet
        ├── 0002.parquet
        └── ...
```

`successful-scenarios.json` is the global completion registry. Before starting an
experiment, the runner skips repetitions whose stable scenario metadata is already
registered. A repetition is registered after its Parquet export completes
successfully. Event failures and incomplete result sets remain available for later
analysis and do not affect runner control flow. If all configured repetitions are
registered, the runner exits without connecting to PostgreSQL or resetting
infrastructure.

`repetitions` is the total target for each scenario configuration. For example,
after successfully completing repetitions 1–10, change `repetitions` to `25`
and run again with the same `EXPERIMENT_OUTPUT_DIR`: only repetitions 11–25
will execute. Their original numbers remain in `scenarios.csv` and filenames
such as `0011.parquet` and `0011.resources.parquet`. The new invocation creates
a fresh execution directory and leaves previous datasets intact.

Completion matches events, lambda, duration, integration processes, maximum
start delay, consumers, and the repetition number. Generated execution/scenario
UUIDs and the total repetition target do not affect matching. Changing scenario
parameters makes those repetitions eligible again. If an interrupted run completed
only repetitions 2 and 4 out of five, the next invocation executes 1, 3, and 5.
The registry is authoritative; existing Parquet files alone do not mark a
repetition complete. Schedule logs report requested, skipped, and pending counts
across all scenario configurations.

Use only one runner process for a given `EXPERIMENT_OUTPUT_DIR`. The global registry
uses atomic updates but does not provide cross-process locking.

DuckDB can query all repetitions in one execution:

```sql
SELECT *
FROM read_parquet(
  'output/experiments/<execution-uuid>/*/[0-9][0-9][0-9][0-9].parquet',
  filename = true
);
```

Polars can scan the same dataset lazily:

```python
import polars as pl

events = pl.scan_parquet(
    "output/experiments/<execution-uuid>/*/[0-9][0-9][0-9][0-9].parquet",
    include_file_paths="source_file",
)
```

## Project repositories

- [Jabuti Monitoring System](https://github.com/gca-research-group/jabuti-monitoring-system)
- [Fabric Network Orchestrator](https://github.com/gca-research-group/hyperledger-fabric-development-network-manager)
- [Transformation Engine](https://github.com/gca-research-group/jabuti-ce-transformation-engine)
- [Jabuti CE (VSCode Plug-in)](https://github.com/gca-research-group/jabuti-ce-vscode-plugin)
- [Jabuti DSL Grammar](https://github.com/gca-research-group/jabuti-ce-jabuti-dsl-grammar)
- [Jabuti XText/Xtend implementation](https://github.com/gca-research-group/dsl-smart-contract-eai)

## Related Publications

- 2025
  - [Proposing a Tool to Monitor Smart Contract Execution in Integration Processes](https://sol.sbc.org.br/index.php/sbsi_estendido/article/view/34617)
  - [Towards a Smart Contract Toolkit for Application Integration](#)
 
- 2024
  - [Jabuti CE: A Tool for Specifying Smart Contracts in the Domain of Enterprise Application Integration](https://www.scitepress.org/Link.aspx?doi=10.5220/0012413300003645)

- 2022
  - [Advances in a DSL to Specify Smart Contracts for Application Integration Processes](https://sol.sbc.org.br/index.php/cibse/article/view/20962)
  - [On the Need to Use Smart Contracts in Enterprise Application Integration](https://idus.us.es/handle/11441/140199)

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contact

For any questions or issues, please open an issue on GitHub or contact the maintainers.

## Container resource monitoring

Set `RESOURCE_MONITORING_ENABLED=true` to collect producer, consumer and RabbitMQ
container statistics. Defaults are a 1s interval and 5s request timeout. Configure
`RESOURCE_SAMPLE_INTERVAL`, `RESOURCE_SAMPLE_TIMEOUT`, `RESOURCE_PRODUCER_CONTAINER`
(default `api-producer`), `RESOURCE_CONSUMER_CONTAINER` (`api-consumer`),
`RESOURCE_RABBITMQ_CONTAINER` (`rabbitmq`), and `RESOURCE_DOCKER_SOCKET`
(`/var/run/docker.sock`). Existing host/user/port settings and the infrastructure
SSH private key are reused. Hosts must run Linux Docker API >=1.41, allow SSH
Unix socket forwarding, and grant the SSH user access to the Docker socket.
The monitoring connections are separate from reset connections.

Each repetition publishes `<execution-uuid>/<scenario-uuid>/0001.resources.parquet`
next to `0001.parquet`. Resource rows are written incrementally with Zstandard
compression. File metadata contains schema version, sampling interval and UTC
window boundaries. Startup verifies all running containers and primes CPU counters;
initial memory rows ensure short executions have observations. Collection stops
when the executor returns, including HTTP request completion. It does not wait for
RabbitMQ queue drainage. Preflight failures abort before workload generation;
sampling failures produce error rows and retry on the next tick. Publication
failures prevent successful registration, while event export is still attempted.

CPU uses consecutive successful counters and Docker's online logical CPU count:
`container_delta / host_delta * cpu_count * 100`. 100% means one fully occupied
logical CPU; multicore usage can exceed 100%. Invalid baselines and reset counters
produce null CPU. Total memory includes cache; working set subtracts
`total_inactive_file` on cgroup v1 or `inactive_file` on v2. Missing or inconsistent
cache counters produce null working set. Docker's memory limit can represent host
memory when no container limit is configured. Rows have `ok`, `partial`, or `error`
status; missing measurements are null, never fabricated zeroes. Sampled peaks can
miss spikes shorter than the interval. These are container metrics, not server
usage or JVM heap statistics.

Query event and resource schemas separately. Numeric event filenames can be
selected with `*/[0-9][0-9][0-9][0-9].parquet`; resources use
`*/*.resources.parquet`.

```sql
SELECT scenario_id, repetition, component,
       avg(cpu_percent) AS avg_cpu, max(cpu_percent) AS peak_cpu,
       avg(memory_working_set_bytes) AS avg_ram,
       max(memory_working_set_bytes) AS peak_ram,
       count(*) FILTER (WHERE sample_status = 'error') AS errors
FROM read_parquet('output/experiments/<execution-uuid>/*/*.resources.parquet')
GROUP BY scenario_id, repetition, component;
```

```python
resources = pl.scan_parquet(
    'output/experiments/<execution-uuid>/*/*.resources.parquet'
)
summary = resources.group_by(['scenario_id', 'repetition', 'component']).agg(
    pl.col('cpu_percent').mean().alias('avg_cpu'),
    pl.col('cpu_percent').max().alias('peak_cpu'),
    pl.col('memory_working_set_bytes').mean().alias('avg_ram'),
    pl.col('memory_working_set_bytes').max().alias('peak_ram'),
)
```

Before production experiments, validate on disposable Linux Docker/SSH hosts,
compare samples against Docker statistics, exercise unavailable containers, and
compare repeated enabled/disabled throughput and latency. Real-host acceptance
requires the configured experiment infrastructure.

## Warm-up and measurement windows

Scenario configuration accepts `warmupDuration` in integer milliseconds. It defaults
to zero. `duration` is the measurement duration and must be positive; warm-up
must be nonnegative, and their sum must fit Go's time duration range.

```json
{
  "events": [10],
  "integrationProcesses": [2],
  "consumers": [2],
  "lambda": 0.5,
  "warmupDuration": 30000,
  "duration": 120000,
  "maxStartDelay": 300,
  "repetitions": 10
}
```

This example generates traffic for 150 seconds. Infrastructure reset, consumer
setup, the existing 10-second startup pause, randomized process preparation
delays, and monitoring preflight happen before the shared workload release.
`maxStartDelay` remains milliseconds, but now delays preparation: all processes
start the workload together after their delays finish. Warm-up uses the same
load as measurement, without a traffic pause, reset, or queue clear between them.
Late scheduling slots are skipped instead of sent as catch-up bursts, so actual
submitted counts can be below `events * integrationProcesses *
(warmupDuration + duration) / 1000`.

Both event and resource Parquet schemas are version 3 and include:

| Column | Meaning |
| --- | --- |
| `duration` (events) | Configured measurement milliseconds |
| `warmup_duration` | Configured warm-up milliseconds |
| `workload_started_at` | Shared logical start of warm-up traffic |
| `measurement_started_at` | Workload start plus warm-up duration |
| `measurement_ended_at` | Measurement start plus measurement duration |

The three timestamps are required UTC timestamps with microsecond precision.
They are identical across all rows in a repetition and both datasets. Parquet
file metadata repeats them as UTC RFC3339 timestamps, including for an empty
event export. `scenarios.csv` includes `WarmupDuration`; it remains a schedule
and does not contain runtime timestamps. Resource metadata `window_start` and
`window_end` still describe collection, which can begin before workload and
continue after measurement.

The measurement interval is half-open: start is included and end is excluded.
HTTP requests dispatched before the end are allowed to finish afterward;
monitoring runs until they finish. Export retains all observed warm-up,
unfinished, and post-cutoff events. There is no RabbitMQ drainage. The logical
cutoff does not move when requests finish or export runs. Synchronize runner and
infrastructure clocks: event timestamps come from other hosts and these
boundaries cannot correct clock skew.

For throughput, count final completions inside the window, including warm-up
arrivals completed during measurement. The examples below use
`outbound_queue_processed` as the terminal completion timestamp; only use it if
it represents completion of the whole pipeline for your experiment.

```sql
WITH events AS (
  SELECT * FROM read_parquet(
    'output/experiments/*/*/[0-9][0-9][0-9][0-9].parquet',
    union_by_name = true
  )
)
SELECT execution_id, scenario_id, repetition,
       count(*) FILTER (
         WHERE outbound_queue_processed >= measurement_started_at
           AND outbound_queue_processed < measurement_ended_at
       )::DOUBLE / epoch(max(measurement_ended_at) - max(measurement_started_at))
         AS completed_events_per_second
FROM events
WHERE measurement_started_at IS NOT NULL
GROUP BY execution_id, scenario_id, repetition;
```

For latency, select arrivals published during measurement. Include in the latency
summary only those completed before the cutoff, and report the unfinished
proportion for the same arrival cohort. This completion-conditioned latency
excludes unfinished events and can be optimistic under overload.

```sql
WITH arrivals AS (
  SELECT * FROM read_parquet(
    'output/experiments/*/*/[0-9][0-9][0-9][0-9].parquet',
    union_by_name = true
  )
  WHERE inbound_queue_published >= measurement_started_at
    AND inbound_queue_published < measurement_ended_at
), cohort AS (
  SELECT *, outbound_queue_processed >= inbound_queue_published
            AND outbound_queue_processed < measurement_ended_at AS finished
  FROM arrivals
)
SELECT execution_id, scenario_id, repetition,
       avg(epoch(outbound_queue_processed - inbound_queue_published) * 1000)
         FILTER (WHERE finished) AS mean_completed_latency_ms,
       count(*) FILTER (WHERE finished IS NOT TRUE)::DOUBLE / count(*)
         AS unfinished_fraction
FROM cohort
GROUP BY execution_id, scenario_id, repetition;
```

For memory, select samples with `timestamp >= measurement_started_at AND
timestamp < measurement_ended_at`. CPU measurements cover an interval ending
at `docker_read_timestamp`. For strict in-window CPU averages, also require
`docker_read_timestamp - cpu_interval_ms * INTERVAL '1 millisecond' >=
measurement_started_at` and `docker_read_timestamp < measurement_ended_at`.
Exclude null CPU values and boundary-crossing intervals. These remain sampled
container statistics, not instantaneous measurements.

Historical files remain untouched. DuckDB `union_by_name = true` exposes missing
new columns as null; exclude those rows from window-based analysis rather than
inferring their timing. Schema version 2 stores durations in seconds; version 3
stores milliseconds. The throughput query uses timestamps to handle either unit.
Warm-up duration and timing protocol version 3 are part
of completion-registry identity. Legacy entries load as protocol 0;
protocol 0 and 2 entries do not
skip new runs, even when warm-up is zero. Existing repetitions will therefore
run again under the new timing protocol.
