# Experiment metrics

Offline analysis of the Go runner's finalized Parquet datasets. Requires Python
3.12 or 3.13. Dependencies are pinned in `pyproject.toml` and resolved in `uv.lock`;
verification uses Python 3.12.13 on Windows. No experiment execution, infrastructure
reset, or backlog collection is performed.

## Run with uv

From this project's `python-metrics` directory:

```powershell
uv sync --locked
uv run --locked run_metrics.py
```

Before running, edit **`PARQUET_FOLDER` immediately after the imports in
`run_metrics.py`**. Set it to an execution folder or the experiments root. The
default is the Go runner's `output/experiments`, resolved relative to the script,
independent of the working directory. Set `REPORT_FOLDER` to your report directory.
Each execution's reports are written to `REPORT_FOLDER/<execution-id>/`.
When analyzing multiple executions, each folder contains only that execution's
data and scenario summaries; the returned result still includes the combined analysis.
Reports inside the input tree are excluded from discovery; the report directory
cannot equal or contain the input directory.

For environments restricting the default uv cache/installation directories:

```powershell
$env:UV_CACHE_DIR = Join-Path $PWD '.uv-cache'
$env:UV_PYTHON_INSTALL_DIR = Join-Path $PWD '.uv-python'
uv sync --locked --python 3.12
```

Package use:

```python
from pathlib import Path
from experiment_metrics import analyze_experiments

PARQUET_FOLDER = Path(r"C:\experiments\execution-id")
REPORT_FOLDER = Path(r"C:\metrics-reports")

result = analyze_experiments(
    PARQUET_FOLDER,
    REPORT_FOLDER,
    experiment_label="hardware-network-fabric-config-A",
    sample_interval_seconds=1.0,
    # Optional: explicitly associate each log with its execution.
    # request_logs={"execution-id": Path(r"C:\logs\execution-id.log")},
)
print(result.runs_summary)
```

The returned `AnalysisResult` contains Polars tables and the quality/manifest
dictionaries. Individual metric functions accept tables and a UTC `Window` without
filesystem access. Supplying a configuration label asserts that hardware, network,
Fabric configuration, and observation protocol match. With no label, repetitions
are aggregated only within the same execution/scenario. Use distinct labels for
different experiment configurations. Missing workload dimensions also prevent
aggregation across executions/scenarios, even with a shared label.

## Input contract and validation

The layout is `<execution-id>/<scenario-id>/<positive-repetition>.parquet` with any
number of digits. Resources use `.resources.parquet`; optional client attempts and
transactions use `.requests.parquet` and `.transactions.parquet`. Temporary files
and unrelated Parquet files are ignored. Dataset kinds are loaded separately, one
run at a time. IDs in rows, when present, must agree with the directory/filename.
`scenarios.csv` supplies dimensions for empty files and identifies scheduled runs
with missing output. Its headers are the Go runner's PascalCase headers.

Supported schema versions are **2 and 3**, declared in the Parquet footer.
`duration` and `warmup_duration` are converted from seconds for v2 and milliseconds
for v3; `max_start_delay` is milliseconds. Measurement boundaries must exist in rows
or footer metadata, agree across rows/files, and form a positive UTC window. Empty
files require footer boundaries. There is no historical configured-duration fallback.
Timezone-free timestamps are accepted only for the Go event/resource export contract
(UTC `time.Time`); optional outcome tables require explicit UTC annotations.

Corrupt files, incompatible schemas, duplicate IDs, inconsistent boundaries, or
conflicting dimensions invalidate the entire repetition. Reports still contain its
run key with null metrics and `analysis_status=invalid`; other valid runs are analyzed.
Missing event data means unknown throughput, while valid empty event data means zero
observed completions. Null observations remain null. Duplicate resource observations
are reported; overlapping CPU intervals suppress the CPU mean.

## Metric definitions

The backend enum `api/.../enums/SmartContractExecutionStatus.java` defines
`PROCESSED`, `FAILED`, `PENDING`, and `PROCESSING`. The default mapping is respectively
success, failure, pending, pending. `SmartContractQueueExecutionService` sets terminal
status before publishing to the outbound queue; `SmartContractQueueOutboundService`
records `OUTBOUND_QUEUE_PROCESSED` after the final outbound stage, including configured
post-execution actions. Therefore success requires **both `PROCESSED` and a final
outbound completion timestamp**. Status by itself does not establish pipeline
completion. UI `SUCCESS`/`ERROR` values are not silently accepted. Override
`status_mapping` explicitly for other application versions; unfamiliar values are
preserved in quality counts.

* **Pipeline throughput:** successful final completions in `[start,end)` divided by
  the exact measurement duration. Includes warm-up arrivals completing in the
  window. Also reports unsuccessful, classified terminal, and unclassified final
  completions. One-second bins are zero-filled and the final partial bin uses its
  actual duration. `target_offered_events_per_second` is configured
  `events * integration_processes`, not actual dispatch rate.
* **Latency:** inbound publication to outbound completion in milliseconds. Arrivals
  must fall in `[start,end)`; percentile samples require confirmed success, a
  nonnegative latency, and completion strictly before cutoff. Uses Polars **linear
  interpolation** for p50/p95/p99. Cohort, failure, pending, unknown, incomplete, and
  successful-sample counts accompany percentiles. Incomplete counts include
  nonfailed events with absent or post-cutoff completion. Counts overlap with
  pending/unknown status classifications and are not mutually exclusive. Negative
  latencies and missing timestamps are reported. Empty samples produce null
  percentiles; fewer than 100 samples flag p99 interpretation.
* **Pipeline errors:** observed `FAILED` statuses among the measurement arrival
  cohort, divided by all cohort events. The exported status lacks its own timestamp,
  so this is an observed cohort outcome, not an exact count of failures occurring
  during the window. Unknown and incomplete events are not treated as failures.
* **Client errors:** optional timestamped attempt records, using dispatch cohort
  membership. Retries remain separate attempts. Failure rate uses all measurement
  attempts as denominator, including unresolved/unknown attempts. Optional Go log
  counters are labeled **whole run including warm-up**, never measurement errors.
* **Fabric TPS:** unique transactions with outcome `committed`, validation code
  `VALID`, and commit-observed timestamp in `[start,end)`. Duplicate transaction IDs
  invalidate a run. Invalid rate uses confirmed valid + invalid commits as denominator.
  Client commit-observation time is not exact ledger commit time. Unknown/unobserved
  outcomes and whole-run submission/Gateway failures are separate. An event may
  relate to multiple transactions. Absent transaction data produces null Fabric TPS.
* **Resources:** per repetition/component/host/container name/container ID. Memory
  samples use `[start,end)` and report arithmetic means and sampled peaks separately
  for working set and total usage, in MiB. Valid sample counts and errors are reported.
  Memory coverage is an **estimate** from the union of nominal sample intervals
  clipped to the window; set `sample_interval_seconds` to the collector setting.
  Maximum sampling gaps include boundary gaps. CPU intervals use Docker read time
  and `cpu_interval_ms`; their start must be at/after measurement start and their end
  strictly before measurement end. Means are weighted by valid interval duration.
  Coverage is the union of valid intervals. Failed/nonfinite/negative observations
  are excluded. CPU may exceed 100%; 100% means one occupied logical CPU. Peaks are
  sampled peaks. No unsynchronized component totals or host usage are inferred.

**Latency is conditional on observed success before cutoff and may be optimistic
under overload.** The runner exports after HTTP requests finish, without waiting
for asynchronous pipeline completion. Offline processing cannot repair clock skew.
This protocol excludes work before inbound publication and does not estimate
eventual-completion percentiles.

## Optional outcome datasets

The current Go runner does not export attempt/transaction tables. This package
consumes them when supplied by a future runner/application integration; it does
not infer them from HTTP successes or event statuses. Each uses schema version 3,
the same boundaries, and the same run-key columns when present.

| File | Required columns |
| --- | --- |
| requests | `attempt_id`, `event_id`, `dispatch_at`, `finish_at`, `outcome`, `http_status`, `error_category` |
| transactions | `transaction_id`, `event_id`, `commit_observed_at`, `validation_code`, `outcome` |

IDs and outcomes are strings, timestamps are UTC timestamp columns, HTTP status is
numeric. Columns may contain nulls except the dataset's stable attempt/transaction
ID. Client outcomes: `accepted`, `transport_failure`, `http_failure`, `timeout`,
`unknown`; missing finish time or negative duration makes an attempt unknown.
Transaction outcomes: `committed`, `submission_failure`, `gateway_failure`,
`unknown`. A committed transaction with a nonnull non-`VALID` validation code is
invalid; null validation codes are unknown. Failed attempts must be retained even
when no event reaches PostgreSQL. Do not include credentials or request secrets.

`request_logs` accepts an explicit execution-ID-to-log-path mapping. JSON `msg`
fields and plain text Go `scenario request summary` lines are supported. Conflicting
totals or repeated summaries reject the log, with details in `data_quality.json`.
Only counters/category names are copied into reports, not raw diagnostic messages.

## Reports and reproducibility

* `runs_summary.parquet`, `runs_summary.csv`: one row per run key, metric units in
  names, counts/denominators, availability flags, analysis status.
* `resources_summary.parquet`, `resources_summary.csv`: per container resource metrics.
* `queues_summary.parquet`, `queues_summary.csv`: post-shutdown counts per virtual host
  and queue, with run identity and UTC capture timestamps, including dead-letter and
  zero-count queues.
* `throughput_timeseries.parquet`: measurement-aligned completion bins.
* `errors_by_category.parquet`: separate layers, scopes, counts, and denominators.
* `scenario_summary.csv`: repetition counts, available observation counts, arithmetic
  mean and sample standard deviation (`ddof=1`) of run-level metrics. Columns named
  `latency_p99_ms_repetition_mean` are means of repetition p99s, not pooled p99s.
* `data_quality.json`: invalid inputs, unknown statuses, missing/negative timestamps,
  sample gaps/exclusions, and unavailable metrics.
* `analysis_manifest.json`: resolved input, executions, SHA-256/size input identities,
  file metadata, versions, mappings/configuration, units, timing/quantile policies,
  and UTC analysis timestamp.

Repeating analysis with identical inputs/configuration gives identical metric tables
and quality reports; the manifest analysis timestamp changes. No charts are required.

Enable remote resource collection in the Go runner before executing experiments:

```dotenv
RESOURCE_MONITORING_ENABLED=true
RESOURCE_SAMPLE_INTERVAL=1s
RESOURCE_SAMPLE_TIMEOUT=5s
```

The existing collector covers producer, consumer, and RabbitMQ containers. Wider
targets (peers/orderers/chaincode/CouchDB/PostgreSQL), host metrics, timestamped
attempt exports, and commit-outcome exports require runner/infrastructure changes.
The analyzer cannot reconstruct observations that were never collected.

## Verification

```powershell
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
```

Tests synthesize Parquet fixtures with known answers, including window boundaries,
empty/corrupt/mixed-version datasets, retries and duplicate IDs, quantiles and
censoring, resource weighting/overlap/gaps, discovery separation, and reproducibility.
The historical `report/data/202607082105` execution was analyzed during implementation:
all 250 repetitions lack schema-version/window metadata and are correctly flagged
as incompatible. No real windowed metric was claimed for that historical dataset.
The committed fixtures produced by the current Go exporter were also analyzed;
independent known-answer checks verify throughput, latency, CPU, and memory.

## Post-shutdown queue counts

Queue snapshots named `<repetition>.queues.parquet` are analyzed independently of
measurement-window datasets. `AnalysisResult.queues_summary` preserves each queue's
`virtual_host`, `queue_name`, `captured_at`, `messages_ready`,
`messages_unacknowledged`, and `messages`, plus execution/scenario/repetition identity.

`runs_summary` includes `queue_metrics_available`, `queue_count`,
`queue_messages_ready`, `queue_messages_unacknowledged`, and `queue_messages_total`.
Counts are summed across all queues and virtual hosts, including dead-letter queues.
`scenario_summary` reports `repetition_mean`, `repetition_stddev` (`ddof=1`), and
`available_repetitions` for each of the three message totals, using existing
configuration grouping. A single available repetition has a null sample standard
deviation.

These are post-shutdown **message** counts, not unique-event counts or backlog at
the measurement cutoff. They may include warm-up and post-cutoff messages. They do
not change throughput, latency, or error calculations and are not used to infer
failure rates.

Schema version 1 requires run identity, UTC capture timestamps, unique
virtual-host/queue pairs, and nonnegative integer counts with
`messages = messages_ready + messages_unacknowledged`. The Go writer's timestamp
without a timezone annotation is interpreted as UTC. Missing snapshots in older
datasets yield null metrics; valid empty snapshots yield zero queues and zero
messages. Invalid snapshots make only queue metrics unavailable, leaving valid
event and resource metrics usable. `data_quality.json` records queue status and
validation errors, and the manifest includes valid queue schema metadata.
