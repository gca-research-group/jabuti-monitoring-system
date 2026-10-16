# Experiment metrics: execution and calculation guide

[run_metrics.py](run_metrics.py) analyzes finalized experiment Parquet files offline.
It does not execute experiments or collect new observations. Requires Python **3.12
or 3.13** and `uv`; dependencies are pinned in `pyproject.toml` and `uv.lock`.

## Execute the analyzer

From the experiment-runner repository root, run:

```powershell
cd metrics
uv sync --locked
uv run --locked run_metrics.py
```

Before running, edit the constants at the top of `run_metrics.py`:

| Constant | Default | Purpose |
| --- | --- | --- |
| `PARQUET_FOLDER` | `<repository>/output/experiments` | An experiments root or one execution folder. |
| `REPORT_FOLDER` | `<repository>/metrics/reports` | Destination for reports. |
| `EXPERIMENT_LABEL` | `None` | Optional label asserting matching hardware, network, Fabric configuration, and observation protocol for aggregation. |

Default paths are resolved relative to the script, independent of the working
directory. For a custom location, use `Path(r"C:\experiments\execution-id")`.
Reports are written to `REPORT_FOLDER/<execution-id>/`. Reports inside the input
tree are excluded from discovery; the report folder cannot equal or contain the
input folder. The script prints resolved locations, run counts, and invalid-run counts.

Input layout:

```text
<PARQUET_FOLDER>/<execution-id>/<scenario-id>/<repetition>.parquet
                                            <repetition>.resources.parquet
                                            <repetition>.queues.parquet
                                            <repetition>.requests.parquet
                                            <repetition>.transactions.parquet
```

When pointing directly to an execution folder, omit the execution level beneath
`PARQUET_FOLDER`. Repetitions are positive integers, including zero-padded filenames.
The unsuffixed file contains events; the other files contain resources, queue
snapshots, optional client attempts, and optional transactions respectively.
`scenarios.csv` supplies workload dimensions for empty files and scheduled runs.
Temporary and unrelated Parquet files are ignored.

Event/resource schemas support versions 2 and 3; optional requests/transactions
require version 3, and queues require version 1. Missing measurement metadata in
historical files cannot be replaced by the configured experiment duration.

## Shared definitions and missing data

Each run is identified by `execution_id`, `scenario_id`, and `repetition`.
Measurement boundaries come from `measurement_started_at` and
`measurement_ended_at` in dataset rows or Parquet footer metadata. They must agree
across files and define a positive UTC window. Empty files require footer boundaries.

```text
S = measurement_started_at
E = measurement_ended_at
T = (E - S).total_seconds()
In-window timestamp t: S <= t < E
```

These become `measurement_started_at`, `measurement_ended_at`, and
`measurement_duration_seconds` in `runs_summary`. `duration` is a workload
configuration dimension, not the denominator used for observed rates.

The default `status` mapping is:

| Input value | Classification |
| --- | --- |
| `PROCESSED` | success |
| `FAILED` | failure |
| `PENDING`, `PROCESSING` | pending |
| Other values or null | unknown |

Rates named `*_rate` or `*_fraction` are fractions, not percentages; multiply by
100 to display a percentage. Zero count denominators produce null rates. A valid
empty event dataset produces zero throughput and null latency percentiles. Missing
event data produces null pipeline metrics, not zero. Availability flags distinguish
missing observations from measured values. Invalid measurement datasets invalidate
the repetition; queue snapshot errors only make queue metrics unavailable.

## Pipeline throughput and configured offered rate

**Input:** event dataset columns `status`, `outbound_queue_processed`; measurement
boundaries supply `T`. Implementation: [throughput.py](src/experiment_metrics/throughput.py).

**Eligible rows:** `S <= outbound_queue_processed < E`. Among these rows, successful
completions have `status` mapped to success. A `PROCESSED` status without an outbound
completion timestamp is insufficient. Arrival time is not filtered here: warm-up
arrivals completing inside the window count toward throughput.

**Formula and outputs:**

```text
pipeline_successful_completions = count(eligible success rows)
pipeline_unsuccessful_completions = count(eligible failure rows)
pipeline_terminal_completions = successful + unsuccessful
pipeline_unclassified_completions = eligible row count - terminal completions
pipeline_completed_events_per_second = pipeline_successful_completions / T
```

Pending and unknown classifications with an in-window completion timestamp contribute
to `pipeline_unclassified_completions`. For example, 240 successful completions in
12 seconds give `240 / 12 = 20 events/s`.

For `throughput_timeseries`, bin `i` starts at `S + i seconds` and ends at
`min(start + 1 second, E)`:

```text
bin_duration_seconds = (bin_ended_at - bin_started_at).total_seconds()
successful_completions = count(success rows completing within that bin)
completed_events_per_second = successful_completions / bin_duration_seconds
```

Bins with no completions contain zero. A final 0.5-second bin containing three
completions has `3 / 0.5 = 6 events/s`. Overall throughput uses total count divided
by `T`, rather than an unweighted average of bin rates.

Configured offered rate uses workload dimension columns `events` and
`integration_processes`, resolved from dataset rows or `scenarios.csv`:

```text
target_offered_events_per_second = events * integration_processes
```

This is a configured target, not observed dispatch throughput. If either dimension
is unavailable, this output is null.

## Latency, percentiles, and incomplete events

**Input:** event columns `inbound_queue_published`, `outbound_queue_processed`,
`status`. Implementation: [latency.py](src/experiment_metrics/latency.py).

**Eligible rows:** the arrival cohort contains rows with
`S <= inbound_queue_published < E`. Percentile samples additionally require success
classification, `outbound_queue_processed < E`, and nonnegative latency.

```text
latency_ms = (outbound_queue_processed - inbound_queue_published)
             .total_nanoseconds() / 1,000,000
```

Missing completion timestamps and negative latencies cannot enter the percentile
sample. Missing arrival timestamps cannot enter the arrival cohort.

**Percentile formula:** sort the `n` eligible latencies as `x[0] ... x[n-1]`.
Polars uses linear interpolation:

```text
h = (n - 1) * q
j = floor(h)
k = ceil(h)
p(q) = x[j] + (h - j) * (x[k] - x[j])
```

| Output | q |
| --- | --- |
| `latency_p50_ms` | 0.50 |
| `latency_p95_ms` | 0.95 |
| `latency_p99_ms` | 0.99 |

For samples `[10, 20, 30, 40, 50]` ms: p50 is 30 ms; p95 uses `h = 3.8`
and is `40 + 0.8 * (50 - 40) = 48` ms; p99 uses `h = 3.96` and is 49.6 ms.
A single sample supplies all three percentiles; no samples produce null percentiles.
p55 is not exported.

**Cohort counts and incomplete fraction:**

```text
latency_cohort_events = arrival cohort row count
latency_successful_samples = n
latency_failed_events = cohort rows classified failure
latency_pending_events = cohort rows classified pending
latency_unknown_events = cohort rows classified unknown
incomplete_at_cutoff_events = cohort rows not classified failure whose completion
                             timestamp is null or >= E
incomplete_fraction = incomplete_at_cutoff_events / latency_cohort_events
latency_small_sample = n < 100
```

The small-sample threshold defaults to 100. Incomplete counts overlap pending and
unknown counts; these counts are not mutually exclusive. No arrivals produce a null
incomplete fraction. `data_quality.json` also reports `negative_latency_rows`,
`missing_arrival_timestamps`, `missing_completion_timestamps`, and
`unknown_status_counts` across the event dataset.

Latency is conditional on observed success before cutoff. The runner exports after
HTTP requests finish without waiting for asynchronous pipeline completion, so these
percentiles can look optimistic under overload. They exclude work before inbound
publication and do not estimate eventual completion latency or correct clock skew.

## Pipeline failures

**Input:** event columns `inbound_queue_published`, `status`.
Implementation: [errors.py](src/experiment_metrics/errors.py).

**Eligible rows:** the measurement arrival cohort, regardless of completion time.

```text
pipeline_failure_events = count(cohort rows classified failure)
pipeline_error_denominator = cohort row count
pipeline_failure_rate = pipeline_failure_events / pipeline_error_denominator
```

Unknown, pending, and incomplete events stay in the denominator but are not failures.
An empty cohort yields zero counts and a null rate. Since `status` has no associated
failure timestamp, this measures observed cohort outcomes, not failures occurring
at a known instant inside the window. `errors_by_category` groups cohort rows by
original `status`, with the cohort size as `denominator`.

## Optional client attempt metrics

**Input:** `.requests.parquet` columns `attempt_id`, `dispatch_at`, `finish_at`,
`outcome`, `error_category`; the schema also requires `event_id` and `http_status`.
The latter two do not enter these formulas. Implementation: [errors.py](src/experiment_metrics/errors.py).

**Eligible rows:** `S <= dispatch_at < E`. Each retry is a separate attempt;
`attempt_id` must be unique. Classification is success for `accepted`, failure for
`transport_failure`, `http_failure`, or `timeout`, and unknown otherwise. Missing
`finish_at` or `finish_at < dispatch_at` overrides classification to unknown.
Finish time is not required to be before `E`.

```text
client_attempts = dispatch cohort row count
client_accepted_attempts = count(success)
client_failed_attempts = count(failure)
client_unknown_attempts = count(unknown)
client_attempted_requests_per_second = client_attempts / T
client_failure_rate = client_failed_attempts / client_attempts
```

Unknown attempts remain in the failure-rate denominator. Failure category is
`error_category`, falling back to `outcome` when null; other categories are the
classification. Category counts and the attempt denominator appear in
`errors_by_category`. Empty input produces zero attempt rate and null failure rate;
absent input produces null client metrics.

The current Go runner does not export attempt datasets. Optional `request_logs`
passed to `analyze_experiments` can supply `client_requests_sent_whole_run`,
`client_successful_requests_whole_run`, and `client_failed_requests_whole_run` from
Go request summaries. These are imported whole-run counters including warm-up,
not reconstructed measurement-window metrics; log category denominators use the
whole-run sent count.

## Optional Fabric transaction metrics

**Input:** `.transactions.parquet` columns `transaction_id`, `commit_observed_at`,
`outcome`, `validation_code`; required `event_id` does not enter the formulas.
Implementation: [errors.py](src/experiment_metrics/errors.py).

**Eligible rows:** `S <= commit_observed_at < E`. Transaction IDs must be unique;
duplicates invalidate the repetition rather than being deduplicated.

A valid transaction has `outcome = committed` and `validation_code = VALID`.
An invalid transaction has `outcome = committed` and a nonnull code other than
`VALID`. Submission/Gateway failures are separate classifications; remaining
observed outcomes are unknown.

```text
fabric_valid_transactions = count(in-window valid transactions)
fabric_invalid_transactions = count(in-window invalid transactions)
fabric_error_denominator = valid + invalid
fabric_valid_transactions_per_second = fabric_valid_transactions / T
fabric_invalid_rate = fabric_invalid_transactions / fabric_error_denominator
fabric_unknown_observed_transactions = count(in-window unknown classifications)
```

`fabric_unknown_unobserved_transactions` counts rows with null
`commit_observed_at` whose outcome is neither submission nor Gateway failure,
including null outcomes. `fabric_submission_failures_whole_run` and
`fabric_gateway_failures_whole_run` count `submission_failure` and `gateway_failure`
respectively across all transaction rows, without a measurement-window filter.

Observed category rows in `errors_by_category` use all in-window transaction rows
as denominator; whole-run failure category rows use all transaction rows. These
category denominators differ from `fabric_error_denominator`.

Empty input yields zero valid TPS and null invalid rate; absent input yields null
Fabric metrics. The current Go runner does not export this dataset. HTTP acceptance
or event `PROCESSED` status cannot establish Fabric TPS. An event may correspond to
multiple transactions, and client commit-observation time is not exact ledger commit time.

## CPU, memory, coverage, and sampling diagnostics

**Input:** `.resources.parquet`, grouped separately by run and by `component`,
`host`, `container_name`, `container_id`. Implementation: [resources.py](src/experiment_metrics/resources.py).
Outputs appear in `resources_summary`; no unsynchronized container totals are inferred.

### CPU

**Columns:** `docker_read_timestamp`, `cpu_interval_ms`, `cpu_percent`, `sample_status`.

**Eligible intervals:** end at `docker_read_timestamp`, start at
`docker_read_timestamp - cpu_interval_ms` milliseconds. Start must be at or after
`S`, end strictly before `E`. Duration must be finite and positive, CPU percentage
finite and nonnegative, and `sample_status` either `ok` or `partial`.

```text
d[i] = cpu_interval_ms[i] / 1000
cpu_valid_samples = eligible interval count
cpu_mean_percent = sum(d[i] * cpu_percent[i]) / sum(d[i])
cpu_sampled_peak_percent = max(cpu_percent[i])
cpu_covered_seconds = duration of the union of eligible intervals
cpu_coverage_fraction = cpu_covered_seconds / T
```

For 20% over one second and 80% over three seconds, the weighted mean is
`(1*20 + 3*80) / 4 = 65%`. Values can exceed 100%; 100% represents one occupied
logical CPU. The analyzer consumes the exported `cpu_percent`, rather than
reconstructing it from Docker CPU counters.

Overlapping eligible intervals set `cpu_overlapping_intervals = true` and suppress
the mean. The peak and union coverage remain available. With no eligible intervals,
mean and peak are null and coverage is zero. `cpu_invalid_samples` counts rows with
missing/invalid duration or end timestamp, plus in-window rows with invalid CPU
values or sample status. Valid intervals outside the window are excluded rather
than counted as invalid. Quality output additionally reports
`cpu_excluded_samples = all group rows - cpu_valid_samples` and
`cpu_uncovered_seconds = T - cpu_covered_seconds`.

### Memory

**Columns:** `timestamp`, `sample_status`, `memory_usage_bytes`,
`memory_working_set_bytes`.

**Eligible samples:** `S <= timestamp < E`, status `ok` or `partial`, and a finite,
nonnegative value for the relevant memory column. Each column is validated separately.
Use prefix `memory_usage` for `memory_usage_bytes`, or `memory_working_set` for
`memory_working_set_bytes`:

```text
value_mib[i] = value_bytes[i] / (1024 * 1024)
<prefix>_valid_samples = n
<prefix>_mean_mib = sum(value_mib[i]) / n
<prefix>_sampled_peak_mib = max(value_mib[i])
```

These are arithmetic sample means, not duration-weighted means. No eligible values
produce null mean/peak and zero sample count. Peaks are sampled peaks, not guaranteed
continuous-time maxima.

### Memory coverage and gaps

**Columns:** `timestamp`, `sample_status`, and the relevant memory value column;
coverage uses the same valid samples as that memory metric.

```text
nominal interval[i] = [timestamp[i], min(timestamp[i] + sample_interval_seconds, E))
<prefix>_estimated_covered_seconds = duration of the union of nominal intervals
<prefix>_estimated_coverage_fraction = estimated covered seconds / T
```

The default `sample_interval_seconds` is 1.0. If collection uses a different interval,
pass its value to `analyze_experiments` in the runner call. Coverage is an estimate;
interval overlap is counted only once. No valid samples give zero estimated coverage.

For `memory_max_sampling_gap_seconds`, sort distinct timestamps of **all** resource
rows in `[S,E)`, including failed samples, insert `S` and `E`, and take the largest
consecutive timestamp difference. With no in-window timestamps the gap is `T`.
`sampling_error_count` counts in-window rows whose status is neither `ok` nor
`partial`; `partial_sample_count` counts in-window rows with status `partial`.
Quality field `duplicate_resource_samples` is the group row count minus the number
of distinct `(timestamp, docker_read_timestamp)` pairs.

Resources cannot be reconstructed if they were never collected. Before experiments,
enable the Go collector as needed:

```dotenv
RESOURCE_MONITORING_ENABLED=true
RESOURCE_SAMPLE_INTERVAL=1s
RESOURCE_SAMPLE_TIMEOUT=5s
```

## Post-shutdown queue counts

**Input:** `.queues.parquet` columns `virtual_host`, `queue_name`, `captured_at`,
`messages_ready`, `messages_unacknowledged`, `messages`, and run identity.
Implementation: [queues.py](src/experiment_metrics/queues.py).

**Eligible rows:** all rows in the valid snapshot, without measurement-window
filtering. Virtual-host/queue pairs must be unique; counts must be nonnegative
integers and satisfy `messages = messages_ready + messages_unacknowledged`.

```text
queue_count = snapshot row count
queue_messages_ready = sum(messages_ready)
queue_messages_unacknowledged = sum(messages_unacknowledged)
queue_messages_total = sum(messages)
```

`queues_summary` preserves each queue's exported counts and UTC `captured_at`;
`runs_summary` holds the sums across all queues and virtual hosts, including
zero-count and dead-letter queues. Valid empty snapshots produce zero counts;
missing/invalid snapshots produce null metrics and `queue_metrics_available = false`.

These are post-shutdown message counts, not unique events or backlog at measurement
cutoff. They may include warm-up and post-cutoff messages and do not establish
pipeline error rates.

## Scenario means and standard deviations

**Input:** `runs_summary` rows with `analysis_status = analyzed`.
Implementation: [aggregation.py](src/experiment_metrics/aggregation.py).

**Grouping:** `configuration_group` plus `events`, `integration_processes`,
`consumers`, `lambda`, `max_start_delay_ms`, `warmup_duration_ms`, `duration_ms`.
The configuration group is `experiment_label` when supplied and all dimensions are
known; otherwise it is `execution_id/scenario_id`. Use different labels for distinct
experiment configurations. Report files aggregate only their own execution's rows.

For each metric below, use only its nonnull values `y[1] ... y[m]`:

```text
<metric>_available_repetitions = m
<metric>_repetition_mean = sum(y[i]) / m
<metric>_repetition_stddev = sqrt(sum((y[i] - mean)^2) / (m - 1))
repetition_count = total analyzed rows in the group
```

Aggregated metrics are `pipeline_completed_events_per_second`, `latency_p50_ms`,
`latency_p95_ms`, `latency_p99_ms`, `pipeline_failure_rate`, `client_failure_rate`,
`client_attempted_requests_per_second`, `fabric_valid_transactions_per_second`,
`fabric_invalid_rate`, `incomplete_fraction`, `queue_messages_ready`,
`queue_messages_unacknowledged`, and `queue_messages_total`.

No available values yield a null mean/stddev and zero available repetitions.
One available value yields its mean and a null sample standard deviation (`ddof=1`).
For example, `latency_p95_ms_repetition_mean` averages repetition p95s; it does not
pool event latencies to calculate a new p95. Resources are reported per container
and are not aggregated by this scenario summary.

## Generated reports

All files below are under `REPORT_FOLDER/<execution-id>/`.

| File | Contents |
| --- | --- |
| `runs_summary.csv`, `.parquet` | Per-run metrics, counts, denominators, dimensions, status and availability flags. |
| `resources_summary.csv`, `.parquet` | Per-run/container resource statistics. |
| `queues_summary.csv`, `.parquet` | Per-queue snapshot values and capture timestamps. |
| `throughput_timeseries.parquet` | Completion bins and their actual durations. |
| `errors_by_category.parquet` | Layer, category, count, denominator, and scope. |
| `scenario_summary.csv` | Repetition means, sample standard deviations, and available counts. |
| `data_quality.json` | Invalid inputs, unknown statuses, timestamp issues, exclusions and sampling diagnostics. |
| `analysis_manifest.json` | Input identities, versions, configuration, units, timing/quantile policies and analysis timestamp. |

Inspect `analysis_status`, availability flags, and `data_quality.json` before
interpreting results. The manifest records the inputs and policies needed to reproduce
the analysis; its analysis timestamp changes on subsequent runs.
