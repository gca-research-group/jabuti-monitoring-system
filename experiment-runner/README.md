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

In scenario configuration files, `maxStartDelay` is expressed in milliseconds.

The system will:
1. Generate a series of scenarios with varying parameters (events, parallels, consumers).
2. Create an execution dataset and save its schedule to `scenarios.csv`.
3. Sequentially execute each scenario repetition.
4. Stop processing, query PostgreSQL, and atomically save the repetition as Zstandard-compressed Parquet before the next reset.

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
