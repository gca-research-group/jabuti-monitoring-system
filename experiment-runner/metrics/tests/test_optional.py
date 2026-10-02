import json

import polars as pl
import pyarrow.parquet as pq
import pytest

from experiment_metrics import analyze_experiments
from experiment_metrics.config import AnalysisConfig, Window
from experiment_metrics.discovery import RunKey
from experiment_metrics.logs import request_log_summaries
from experiment_metrics.validation import load_dataset

from conftest import START, write_events


def write_optional(path, kind, duplicate=False):
    count = 2 if duplicate else 1
    common = {
        "event_id": ["e"] * count,
        "outcome": ["accepted" if kind == "requests" else "committed"] * count,
    }
    columns = (
        {
            "attempt_id": ["a"] * count,
            "dispatch_at": [START] * count,
            "finish_at": [START] * count,
            "http_status": [200] * count,
            "error_category": pl.Series([None] * count, dtype=pl.String),
        }
        if kind == "requests"
        else {
            "transaction_id": ["tx"] * count,
            "commit_observed_at": [START] * count,
            "validation_code": ["VALID"] * count,
        }
    )
    frame = pl.DataFrame({**common, **columns})
    path.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(
        frame.to_arrow().replace_schema_metadata(
            {
                b"schema_version": b"3",
                b"measurement_started_at": START.isoformat().encode(),
                b"measurement_ended_at": b"2026-01-01T00:00:02.5+00:00",
            }
        ),
        path,
    )


@pytest.mark.parametrize("kind", ["requests", "transactions"])
def test_duplicate_optional_ids(tmp_path, kind):
    path = tmp_path / f"execution/scenario/1.{kind}.parquet"
    write_optional(path, kind, duplicate=True)
    with pytest.raises(ValueError, match="Duplicate"):
        load_dataset(path, kind, RunKey("execution", "scenario", 1), {})


def test_optional_layers_end_to_end_and_no_event_required(tmp_path):
    directory = tmp_path / "data/execution/scenario"
    write_optional(directory / "1.requests.parquet", "requests")
    write_optional(directory / "1.transactions.parquet", "transactions")
    result = analyze_experiments(tmp_path / "data", tmp_path / "reports")
    row = result.runs_summary.row(0, named=True)
    assert row["pipeline_completed_events_per_second"] is None
    assert row["client_attempts"] == row["client_accepted_attempts"] == 1
    assert row["fabric_valid_transactions_per_second"] == 0.4
    assert result.errors_by_category["layer"].to_list() == ["client", "fabric"]


def test_disagreeing_optional_window_invalidates_run(tmp_path):
    path = write_events(tmp_path / "data/execution/scenario/1.parquet")
    optional = path.with_name("1.requests.parquet")
    write_optional(optional, "requests")
    arrow = pq.read_table(optional)
    metadata = dict(arrow.schema.metadata)
    metadata[b"measurement_ended_at"] = b"2026-01-01T00:00:03+00:00"
    pq.write_table(arrow.replace_schema_metadata(metadata), optional)
    result = analyze_experiments(tmp_path / "data", tmp_path / "reports")
    assert result.runs_summary["analysis_status"][0] == "invalid"
    assert result.runs_summary["pipeline_completed_events_per_second"][0] is None


def test_whole_run_log_separate_from_measurement(tmp_path):
    write_events(tmp_path / "data/execution/scenario/1.parquet")
    log = tmp_path / "execution.log"
    message = "scenario request summary: scenario_id=scenario repetition=1 requests_sent=3 successful=1 failed=2 timeout=2"
    log.write_text(json.dumps({"msg": message}) + "\n")
    result = analyze_experiments(
        tmp_path / "data", tmp_path / "reports", request_logs={"execution": log}
    )
    row = result.runs_summary.row(0, named=True)
    assert row["client_failed_requests_whole_run"] == 2
    assert row["client_failure_rate"] is None
    assert row["client_log_summary_available"]
    assert not row["client_metrics_available"]
    assert result.errors_by_category["scope"].to_list() == ["whole_run_including_warmup"]
    log.write_text(message + "\n" + message)
    with pytest.raises(ValueError, match="Repeated"):
        request_log_summaries(log, "execution")


@pytest.mark.parametrize(
    "kwargs",
    [
        {"status_mapping": {"PROCESSED": "valid"}},
        {"sample_interval_seconds": 0},
        {"small_sample_threshold": 0},
    ],
)
def test_invalid_config(kwargs):
    with pytest.raises(ValueError):
        AnalysisConfig(**kwargs)


def test_invalid_window():
    with pytest.raises(ValueError):
        Window(START, START)


def test_missing_dimensions_prevent_cross_scenario_aggregation(tmp_path):
    for execution in ("a", "b"):
        write_events(tmp_path / f"data/{execution}/scenario/1.parquet")
    result = analyze_experiments(
        tmp_path / "data", tmp_path / "reports", experiment_label="matching-hardware"
    )
    assert result.scenario_summary.height == 2
