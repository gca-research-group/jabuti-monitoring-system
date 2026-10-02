from datetime import datetime, timedelta, timezone

import polars as pl
import pyarrow.parquet as pq
import pytest

from experiment_metrics import Window

START = datetime(2026, 1, 1, tzinfo=timezone.utc)


@pytest.fixture
def window():
    return Window(START, START + timedelta(seconds=2.5))


def events(rows):
    """Rows: id, status, arrival seconds, completion seconds relative to START."""
    return pl.DataFrame(
        {
            "event_id": [r[0] for r in rows],
            "status": [r[1] for r in rows],
            "inbound_queue_published": [
                START + timedelta(seconds=r[2]) if r[2] is not None else None for r in rows
            ],
            "outbound_queue_processed": [
                START + timedelta(seconds=r[3]) if r[3] is not None else None for r in rows
            ],
        },
        schema={
            "event_id": pl.String,
            "status": pl.String,
            "inbound_queue_published": pl.Datetime("us", "UTC"),
            "outbound_queue_processed": pl.Datetime("us", "UTC"),
        },
    )


def write_events(path, rows=(), version="3", footer=True, **metadata):
    frame = events(rows)
    arrow = frame.to_arrow()
    meta = {"schema_version": version}
    if footer:
        meta.update(
            measurement_started_at=START.isoformat(),
            measurement_ended_at=(START + timedelta(seconds=2.5)).isoformat(),
        )
    meta.update(metadata)
    arrow = arrow.replace_schema_metadata({k.encode(): v.encode() for k, v in meta.items()})
    path.parent.mkdir(parents=True, exist_ok=True)
    pq.write_table(arrow, path)
    return path
