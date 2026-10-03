from datetime import datetime, timezone
from math import sqrt

import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq
import pytest

from experiment_metrics.analysis import analyze_experiments
from experiment_metrics.discovery import RunKey, discover
from experiment_metrics.queues import load_queues
from experiment_metrics.schemas import QUEUE_SCHEMA

from conftest import write_events

KEY = RunKey("execution", "scenario", 2)


def write_queues(path, rows=None, version="1", timezone_name="UTC"):
    if rows is None:
        rows = [
            {
                "execution_id": "execution",
                "scenario_id": "scenario",
                "repetition": 2,
                "captured_at": datetime(2026, 1, 1, 0, 0, 3, tzinfo=timezone.utc),
                "virtual_host": "/",
                "queue_name": "inbound",
                "messages_ready": 3,
                "messages_unacknowledged": 1,
                "messages": 4,
            }
        ]
    frame = pl.DataFrame(rows, schema=QUEUE_SCHEMA)
    arrow = frame.to_arrow()
    if timezone_name != "UTC":
        index = arrow.schema.get_field_index("captured_at")
        arrow = arrow.set_column(
            index, "captured_at", arrow.column(index).cast(pa.timestamp("us", tz=timezone_name))
        )
    arrow = arrow.replace_schema_metadata(
        {b"schema_version": version.encode(), b"dataset_kind": b"queues"}
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(arrow, path)
    return path


def test_queue_details_totals_and_output_files(tmp_path):
    inputs = tmp_path / "inputs"
    event_path = inputs / "execution/scenario/0002.parquet"
    write_events(event_path, [("event", "PROCESSED", 0, 1)])
    baseline = analyze_experiments(inputs, tmp_path / "baseline")
    queue_path = event_path.with_name("0002.queues.parquet")
    write_queues(queue_path)
    original = pq.read_table(queue_path).to_pylist()[0]
    write_queues(
        queue_path,
        [
            original,
            {
                **original,
                "virtual_host": "tenant",
                "queue_name": "dead-letter",
                "messages_ready": 0,
                "messages_unacknowledged": 0,
                "messages": 0,
            },
        ],
    )
    _, runs, _, inventory = discover(inputs, tmp_path / "report")
    assert runs[KEY]["queues"] == queue_path.resolve()
    assert any(item["path"] == str(queue_path.resolve()) for item in inventory)
    result = analyze_experiments(inputs, tmp_path / "report")
    row = result.runs_summary.row(0, named=True)
    assert row["queue_metrics_available"]
    assert [
        row[name]
        for name in (
            "queue_count",
            "queue_messages_ready",
            "queue_messages_unacknowledged",
            "queue_messages_total",
        )
    ] == [2, 3, 1, 4]
    assert (
        row["pipeline_completed_events_per_second"]
        == baseline.runs_summary["pipeline_completed_events_per_second"][0]
    )
    assert result.resources_summary.equals(baseline.resources_summary)
    assert result.queues_summary.height == 2
    assert result.manifest["dataset_metadata"][str(queue_path.resolve())]["schema_version"] == "1"
    assert pl.read_parquet(tmp_path / "report/execution/queues_summary.parquet").equals(
        result.queues_summary
    )
    assert pl.read_csv(tmp_path / "report/execution/queues_summary.csv").height == 2


@pytest.mark.parametrize(
    "change",
    [
        {"execution_id": "wrong"},
        {"scenario_id": "wrong"},
        {"repetition": 3},
        {"captured_at": None},
        {"virtual_host": ""},
        {"queue_name": ""},
        {"messages_ready": -1},
        {"messages_unacknowledged": -1},
        {"messages": -1},
        {"messages": 5},
        {"messages_ready": None},
    ],
)
def test_invalid_queue_rows(change, tmp_path):
    path = write_queues(tmp_path / "queues.parquet")
    row = pq.read_table(path).to_pylist()[0]
    write_queues(path, [{**row, **change}])
    with pytest.raises(ValueError):
        load_queues(path, KEY)


@pytest.mark.parametrize("problem", ["version", "missing", "duplicate", "timezone", "float"])
def test_invalid_queue_schema_and_duplicates(problem, tmp_path):
    path = write_queues(tmp_path / "queues.parquet")
    row = pq.read_table(path).to_pylist()[0]
    if problem == "version":
        write_queues(path, version="3")
    elif problem == "duplicate":
        write_queues(path, [row, row])
    elif problem == "timezone":
        write_queues(path, timezone_name="America/Sao_Paulo")
    else:
        arrow = pq.read_table(path)
        if problem == "missing":
            arrow = arrow.drop(["queue_name"])
        else:
            index = arrow.schema.get_field_index("messages")
            arrow = arrow.set_column(index, "messages", arrow.column(index).cast(pa.float64()))
        pq.write_table(arrow, path)
    with pytest.raises(ValueError):
        load_queues(path, KEY)


def test_naive_go_timestamps_and_empty_snapshot(tmp_path):
    path = write_queues(tmp_path / "queues.parquet", timezone_name=None)
    frame, _, _ = load_queues(path, KEY)
    assert frame.schema["captured_at"] == pl.Datetime("us", "UTC")
    write_queues(path, [])
    frame, metrics, _ = load_queues(path, KEY)
    assert frame.is_empty()
    assert metrics == {
        "queue_count": 0,
        "queue_messages_ready": 0,
        "queue_messages_unacknowledged": 0,
        "queue_messages_total": 0,
        "queue_metrics_available": True,
    }


def test_invalid_snapshot_does_not_suppress_event_metrics(tmp_path):
    inputs = tmp_path / "inputs"
    write_events(inputs / "execution/scenario/0002.parquet", [("event", "PROCESSED", 0, 1)])
    path = write_queues(inputs / "execution/scenario/0002.queues.parquet", version="bad")
    result = analyze_experiments(inputs, tmp_path / "reports")
    row = result.runs_summary.row(0, named=True)
    assert row["analysis_status"] == "analyzed"
    assert row["pipeline_metrics_available"]
    assert not row["queue_metrics_available"]
    assert row["queue_messages_total"] is None
    assert result.data_quality["runs"][0]["queues"]["status"] == "invalid"
    assert result.data_quality["runs"][0]["queues"]["path"] == str(path.resolve())


def test_repetition_aggregation_missing_and_single_snapshot(tmp_path):
    inputs = tmp_path / "inputs"
    for repetition in (1, 2, 3):
        write_events(inputs / f"execution/scenario/{repetition}.parquet")
    path = write_queues(inputs / "execution/scenario/1.queues.parquet")
    row = pq.read_table(path).to_pylist()[0]
    write_queues(path, [{**row, "repetition": 1}])
    result = analyze_experiments(inputs, tmp_path / "single")
    summary = result.scenario_summary.row(0, named=True)
    assert summary["queue_messages_total_repetition_mean"] == 4
    assert summary["queue_messages_total_repetition_stddev"] is None
    assert summary["queue_messages_total_available_repetitions"] == 1
    assert result.runs_summary["queue_messages_total"].to_list() == [4, None, None]
    write_queues(
        inputs / "execution/scenario/2.queues.parquet",
        [{**row, "messages_ready": 8, "messages_unacknowledged": 0, "messages": 8}],
    )
    result = analyze_experiments(inputs, tmp_path / "multiple")
    summary = result.scenario_summary.row(0, named=True)
    assert summary["queue_messages_total_repetition_mean"] == 6
    assert summary["queue_messages_total_repetition_stddev"] == pytest.approx(sqrt(8))
    assert summary["queue_messages_total_available_repetitions"] == 2


def test_queue_only_empty_snapshot_is_analyzed(tmp_path):
    inputs = tmp_path / "inputs"
    write_queues(inputs / "execution/scenario/2.queues.parquet", [])
    result = analyze_experiments(inputs, tmp_path / "reports")
    row = result.runs_summary.row(0, named=True)
    assert row["analysis_status"] == "analyzed"
    assert row["queue_metrics_available"]
    assert row["queue_messages_total"] == 0
    assert not row["pipeline_metrics_available"]
    assert result.queues_summary.schema == QUEUE_SCHEMA
