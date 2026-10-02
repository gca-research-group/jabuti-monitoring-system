from datetime import timedelta

import polars as pl
import pyarrow.parquet as pq
import pytest

from experiment_metrics.discovery import RunKey, discover
from experiment_metrics.validation import load_dataset

from conftest import START, write_events

KEY = RunKey("execution", "scenario", 1)


@pytest.mark.parametrize("version", ["2", "3"])
def test_empty_footer_and_duration_units(tmp_path, version):
    path = write_events(tmp_path / "execution/scenario/1.parquet", version=version)
    frame, window, dimensions, _ = load_dataset(path, "events", KEY, {"duration": 3})
    assert frame.height == 0
    assert window.seconds == 2.5
    assert dimensions["duration_ms"] == (3000 if version == "2" else 3)


@pytest.mark.parametrize(
    "rows",
    [
        [("a", "PROCESSED", 0, 1), ("a", "FAILED", 0, 1)],
        [(None, "PENDING", 0, None)],
        [("", "PENDING", 0, None)],
    ],
)
def test_duplicate_or_missing_ids_rejected(tmp_path, rows):
    path = write_events(tmp_path / "execution/scenario/1.parquet", rows)
    with pytest.raises(ValueError, match="event_id"):
        load_dataset(path, "events", KEY, {})


@pytest.mark.parametrize(
    "metadata",
    [
        {"schema_version": "1"},
        {"measurement_ended_at": START.isoformat()},
        {"measurement_started_at": "2026-01-01T00:00:00"},
        {"measurement_started_at": "2026-01-01T00:00:00+01:00"},
    ],
)
def test_invalid_footer(tmp_path, metadata):
    path = write_events(tmp_path / "execution/scenario/1.parquet", **metadata)
    with pytest.raises(ValueError):
        load_dataset(path, "events", KEY, {})


def test_no_historical_window_fallback(tmp_path):
    path = write_events(tmp_path / "execution/scenario/1.parquet", footer=False)
    with pytest.raises(ValueError, match="no configured-duration fallback"):
        load_dataset(path, "events", KEY, {"duration": 2500})


def test_rows_footer_conflict_and_types(tmp_path):
    path = write_events(tmp_path / "execution/scenario/1.parquet", [("e", "PROCESSED", 0, 1)])
    arrow = pq.read_table(path)
    frame = pl.from_arrow(arrow).with_columns(
        pl.lit(START + timedelta(seconds=1)).alias("measurement_started_at")
    )
    pq.write_table(frame.to_arrow().replace_schema_metadata(arrow.schema.metadata), path)
    with pytest.raises(ValueError, match="disagreement"):
        load_dataset(path, "events", KEY, {})
    frame = frame.with_columns(pl.lit(12).alias("status"))
    pq.write_table(frame.to_arrow().replace_schema_metadata(arrow.schema.metadata), path)
    with pytest.raises(ValueError, match="Incompatible status"):
        load_dataset(path, "events", KEY, {})


def test_discovery_any_width_multiple_executions_and_exclusion(tmp_path):
    write_events(tmp_path / "execution/scenario/1.parquet")
    write_events(tmp_path / "other/scenario/00001.parquet")
    write_events(tmp_path / "reports/fake/scenario/1.parquet")
    (tmp_path / "execution/scenario/.events.parquet.tmp").write_text("partial")
    _, runs, _, _ = discover(tmp_path, tmp_path / "reports")
    assert len(runs) == 2
    assert {key.execution_id for key in runs} == {"execution", "other"}


def test_duplicate_dataset_names_rejected(tmp_path):
    write_events(tmp_path / "execution/scenario/1.parquet")
    write_events(tmp_path / "execution/scenario/0001.parquet")
    with pytest.raises(ValueError, match="Duplicate"):
        discover(tmp_path, tmp_path / "reports")
