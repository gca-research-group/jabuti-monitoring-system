from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from importlib.metadata import version
from pathlib import Path

import polars as pl

from .aggregation import DIMENSION_COLUMNS, METRICS, aggregate_runs
from .config import AnalysisConfig
from .discovery import discover, identity
from .errors import client_errors, fabric_outcomes, pipeline_errors
from .latency import event_quality, pipeline_latency
from .logs import request_log_summaries
from .reporting import table, write_reports
from .resources import resource_metrics
from .schemas import ERROR_SCHEMA, RESOURCE_SCHEMA, RUN_COUNTS, TIMESERIES_SCHEMA
from .throughput import pipeline_throughput
from .validation import load_dataset

LATENCY_CAVEAT = (
    "Latency begins at inbound publication and is conditional on confirmed observed success "
    "before measurement cutoff. The runner does not wait for asynchronous completion; "
    "percentiles may be optimistic under overload. Unknown clock skew cannot be repaired offline."
)


@dataclass
class AnalysisResult:
    runs_summary: pl.DataFrame
    resources_summary: pl.DataFrame
    throughput_timeseries: pl.DataFrame
    errors_by_category: pl.DataFrame
    scenario_summary: pl.DataFrame
    data_quality: dict
    manifest: dict


def analyze_experiments(
    parquet_folder,
    report_folder,
    *,
    experiment_label=None,
    status_mapping=None,
    sample_interval_seconds=1.0,
    small_sample_threshold=100,
    request_logs=None,
) -> AnalysisResult:
    config = AnalysisConfig(
        experiment_label=experiment_label,
        sample_interval_seconds=sample_interval_seconds,
        small_sample_threshold=small_sample_threshold,
        **({"status_mapping": status_mapping} if status_mapping is not None else {}),
    )
    report_folder = Path(report_folder).resolve()
    input_folder = Path(parquet_folder).resolve()
    if input_folder == report_folder or input_folder.is_relative_to(report_folder):
        raise ValueError("Report folder must not equal or contain input folder")
    folder, files_by_run, schedules, inputs = discover(input_folder, report_folder)
    log_summaries, log_quality = {}, []
    for execution_id, log_path in sorted((request_logs or {}).items()):
        log_path = Path(log_path).resolve()
        inputs.append(identity(log_path))
        try:
            log_summaries.update(request_log_summaries(log_path, execution_id))
        except ValueError as exc:
            log_quality.append({"path": str(log_path), "reason": str(exc)})
    run_rows, resource_rows, bins, errors, quality, versions = [], [], [], [], [], {}
    for key in sorted(set(files_by_run) | set(schedules)):
        files = files_by_run.get(key, {})
        run_quality = {
            **key.as_dict(),
            "invalid_inputs": [],
            "unavailable_metrics": [],
            "warnings": [LATENCY_CAVEAT],
        }
        loaded = {}
        for kind, path in sorted(files.items()):
            try:
                loaded[kind] = load_dataset(path, kind, key, schedules.get(key, {}))
                versions[str(path)] = loaded[kind][3]
            except (
                ValueError,
                OSError,
                UnicodeError,
                OverflowError,
                pl.exceptions.PolarsError,
            ) as exc:
                run_quality["invalid_inputs"].append({"path": str(path), "reason": str(exc)})
        dimensions = {name: None for name in DIMENSION_COLUMNS}
        # Non-duration workload settings are still known for scheduled missing/invalid files.
        for name, value in schedules.get(key, {}).items():
            if name not in {"duration", "warmup_duration"}:
                dimensions["max_start_delay_ms" if name == "max_start_delay" else name] = value
        windows = [data[1] for data in loaded.values()]
        if windows and any(w != windows[0] for w in windows[1:]):
            run_quality["invalid_inputs"].append(
                {"reason": "Dataset measurement boundaries disagree"}
            )
        for data in loaded.values():
            for name, value in data[2].items():
                if dimensions[name] is not None and value is not None and dimensions[name] != value:
                    run_quality["invalid_inputs"].append(
                        {"reason": f"Dataset dimension {name} disagrees"}
                    )
                if value is not None:
                    dimensions[name] = value
        row = {
            **key.as_dict(),
            **dimensions,
            "experiment_label": config.experiment_label,
            "analysis_status": "invalid" if run_quality["invalid_inputs"] else "analyzed",
            **{metric: None for metric in METRICS},
            **{name: None for name in RUN_COUNTS},
            "latency_small_sample": None,
            "target_offered_events_per_second": None,
            "client_log_summary_available": False,
            "measurement_started_at": None,
            "measurement_ended_at": None,
            "measurement_duration_seconds": None,
            "pipeline_metrics_available": False,
            "client_metrics_available": False,
            "fabric_metrics_available": False,
            "resource_metrics_available": False,
        }
        if not loaded:
            row["analysis_status"] = "invalid" if files else "missing"
        if windows:
            window = windows[0]
            row.update(
                measurement_started_at=window.start,
                measurement_ended_at=window.end,
                measurement_duration_seconds=window.seconds,
            )
        if not run_quality["invalid_inputs"] and windows:
            if "events" in loaded:
                events = loaded["events"][0]
                metrics, series = pipeline_throughput(events, window, config.status_mapping)
                row.update(metrics)
                row.update(
                    pipeline_latency(
                        events, window, config.status_mapping, config.small_sample_threshold
                    )
                )
                metrics, categories = pipeline_errors(events, window, config.status_mapping)
                row.update(metrics)
                bins.extend({**key.as_dict(), **item} for item in series)
                errors.extend({**key.as_dict(), **item} for item in categories)
                row["pipeline_metrics_available"] = True
                run_quality.update(event_quality(events, config.status_mapping))
            for kind, function, flag in (
                ("requests", client_errors, "client_metrics_available"),
                ("transactions", fabric_outcomes, "fabric_metrics_available"),
            ):
                if kind in loaded:
                    metrics, categories = function(loaded[kind][0], window)
                    row.update(metrics)
                    row[flag] = True
                    errors.extend({**key.as_dict(), **item} for item in categories)
                    dataset = loaded[kind][0]
                    if kind == "requests":
                        run_quality["requests"] = {
                            "missing_dispatch_timestamps": dataset["dispatch_at"].null_count(),
                            "missing_finish_timestamps": dataset["finish_at"].null_count(),
                            "negative_attempt_duration_rows": dataset.filter(
                                pl.col("finish_at") < pl.col("dispatch_at")
                            ).height,
                            "outcome_counts": dataset.group_by("outcome")
                            .len()
                            .sort("outcome")
                            .to_dicts(),
                        }
                    else:
                        run_quality["transactions"] = {
                            "missing_commit_observation_timestamps": dataset[
                                "commit_observed_at"
                            ].null_count(),
                            "outcome_counts": dataset.group_by("outcome")
                            .len()
                            .sort("outcome")
                            .to_dicts(),
                            "validation_code_counts": dataset.group_by("validation_code")
                            .len()
                            .sort("validation_code")
                            .to_dicts(),
                        }
            if "resources" in loaded:
                summaries, resource_quality = resource_metrics(
                    loaded["resources"][0], window, config.sample_interval_seconds
                )
                resource_rows.extend({**key.as_dict(), **item} for item in summaries)
                run_quality["resources"] = resource_quality
                row["resource_metrics_available"] = True
        if key in log_summaries:
            counts, categories = log_summaries[key]
            row.update(counts)
            row["client_log_summary_available"] = True
            errors.extend(
                {
                    **key.as_dict(),
                    "layer": "client",
                    "category": category,
                    "count": count,
                    "denominator": counts["client_requests_sent_whole_run"],
                    "scope": "whole_run_including_warmup",
                }
                for category, count in sorted(categories.items())
            )
        if row["events"] is not None and row["integration_processes"] is not None:
            row["target_offered_events_per_second"] = row["events"] * row["integration_processes"]
        for flag, reason in (
            (
                "pipeline_metrics_available",
                "Missing/invalid event data; zero completions cannot be assumed",
            ),
            (
                "client_metrics_available",
                "Timestamped request attempts unavailable; logs include warm-up",
            ),
            (
                "fabric_metrics_available",
                "Confirmed transaction commit outcomes unavailable; pipeline is not Fabric TPS",
            ),
            ("resource_metrics_available", "Container resource observations unavailable"),
        ):
            if not row[flag]:
                run_quality["unavailable_metrics"].append({"metric": flag, "reason": reason})
        run_rows.append(row)
        quality.append(run_quality)
    runs = table(run_rows, {"execution_id": pl.String})
    # All-null metrics must remain numeric for deterministic CSV/Parquet and aggregation.
    runs = runs.with_columns(
        *[
            pl.col(name).cast(pl.Float64)
            for name in (
                *METRICS,
                "target_offered_events_per_second",
                "measurement_duration_seconds",
                "lambda",
            )
        ],
        *[
            pl.col(name).cast(pl.Int64)
            for name in (*RUN_COUNTS, *[col for col in DIMENSION_COLUMNS if col != "lambda"])
        ],
        pl.col("latency_small_sample").cast(pl.Boolean),
        pl.col("experiment_label").cast(pl.String),
        pl.col("measurement_started_at").cast(pl.Datetime("us", "UTC")),
        pl.col("measurement_ended_at").cast(pl.Datetime("us", "UTC")),
    )
    result = AnalysisResult(
        runs_summary=runs,
        resources_summary=table(resource_rows, RESOURCE_SCHEMA),
        throughput_timeseries=table(bins, TIMESERIES_SCHEMA),
        errors_by_category=table(errors, ERROR_SCHEMA),
        scenario_summary=aggregate_runs(runs),
        data_quality={
            "runs": quality,
            "invalid_run_count": sum(row["analysis_status"] == "invalid" for row in run_rows),
            "latency_caveat": LATENCY_CAVEAT,
            "request_logs": log_quality,
        },
        manifest={
            "input_folder": str(folder),
            "execution_ids": sorted({key.execution_id for key in files_by_run}),
            "inputs": inputs,
            "dataset_metadata": versions,
            "versions": {
                package: version(package)
                for package in ("experiment-metrics", "polars", "pyarrow", "tzdata")
            },
            "configuration": {
                **asdict(config),
                "request_logs": {
                    execution: str(Path(path).resolve())
                    for execution, path in sorted((request_logs or {}).items())
                },
            },
            "quantile_method": "linear",
            "timing_policy": {
                "events": "[start,end)",
                "latency": "arrival cohort; success before cutoff",
                "cpu": "interval start >= start; interval end < end; duration weighted",
                "memory_coverage": "estimated union of nominal sampling intervals",
                "naive_timestamps": "v2/v3 Go UTC time.Time export contract",
            },
            "units": {
                "latency": "ms",
                "memory": "MiB (2^20 bytes)",
                "cpu": "percent; 100% is one logical CPU",
                "scenario_duration": "ms",
            },
            "analysis_timestamp": datetime.now(timezone.utc).isoformat(),
        },
    )
    write_reports(result, report_folder)
    return result
