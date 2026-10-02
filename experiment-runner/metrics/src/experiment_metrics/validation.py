from datetime import datetime, timedelta, timezone
from math import isfinite
from pathlib import Path

import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq

from .config import DIMENSIONS, Window

REQUIRED = {
    "events": {
        "event_id": "string",
        "status": "string",
        "inbound_queue_published": "timestamp",
        "outbound_queue_processed": "timestamp",
    },
    "resources": {
        "component": "string",
        "host": "string",
        "container_name": "string",
        "container_id": "string",
        "timestamp": "timestamp",
        "docker_read_timestamp": "timestamp",
        "cpu_interval_ms": "number",
        "cpu_percent": "number",
        "memory_usage_bytes": "number",
        "memory_working_set_bytes": "number",
        "sample_status": "string",
    },
    "requests": {
        "attempt_id": "string",
        "event_id": "string",
        "dispatch_at": "timestamp",
        "finish_at": "timestamp",
        "outcome": "string",
        "http_status": "number",
        "error_category": "string",
    },
    "transactions": {
        "transaction_id": "string",
        "event_id": "string",
        "commit_observed_at": "timestamp",
        "validation_code": "string",
        "outcome": "string",
    },
}
IDENTIFIERS = {"events": "event_id", "requests": "attempt_id", "transactions": "transaction_id"}


def utc(value):
    if isinstance(value, str):
        value = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if not isinstance(value, datetime) or value.tzinfo is None or value.utcoffset() != timedelta(0):
        raise ValueError(f"Expected UTC timestamp, received {value!r}")
    return value.astimezone(timezone.utc)


def load_dataset(path: Path, kind, key, schedule):
    """Reject incompatible finalized inputs; never repair conflicting IDs or timing."""
    parquet = pq.ParquetFile(path)
    metadata = {
        k.decode(): v.decode()
        for k, v in (parquet.metadata.metadata or {}).items()
        if k != b"ARROW:schema"
    }
    version = metadata.get("schema_version")
    if version not in {"2", "3"}:
        raise ValueError(
            f"Unsupported/missing schema_version {version!r}; no historical timing inference"
        )
    schema = parquet.schema_arrow
    for name, expected in REQUIRED[kind].items():
        if name not in schema.names:
            raise ValueError(f"Missing required column {name}")
        dtype = schema.field(name).type
        valid = (
            (
                expected == "string"
                and (pa.types.is_string(dtype) or pa.types.is_large_string(dtype))
            )
            or (
                expected == "number" and (pa.types.is_integer(dtype) or pa.types.is_floating(dtype))
            )
            or (expected == "timestamp" and pa.types.is_timestamp(dtype))
        )
        if not valid:
            raise ValueError(f"Incompatible {name} type: {dtype}; expected {expected}")
    frame = pl.from_arrow(parquet.read())
    for field in schema:
        if pa.types.is_timestamp(field.type):
            if field.type.tz not in {None, "UTC", "Etc/UTC", "+00:00"}:
                raise ValueError(f"Non-UTC timestamp column: {field.name}")
            # Go's schema v2/v3 stores optional time.Time timestamps without a timezone annotation.
            if field.type.tz is None:
                if kind not in {"events", "resources"}:
                    raise ValueError(
                        f"Optional outcome contract requires explicit UTC: {field.name}"
                    )
                frame = frame.with_columns(pl.col(field.name).dt.replace_time_zone("UTC"))
    for name, expected in key.as_dict().items():
        if name in frame.columns:
            dtype = schema.field(name).type
            if not (
                pa.types.is_integer(dtype)
                if name == "repetition"
                else (pa.types.is_string(dtype) or pa.types.is_large_string(dtype))
            ):
                raise ValueError(f"Invalid run identity type: {name}")
            values = frame[name].unique().to_list()
            if frame.height and values != [expected]:
                raise ValueError(f"Run identity mismatch in {name}: {values}")
    id_column = IDENTIFIERS.get(kind)
    if id_column:
        ids = frame[id_column]
        if ids.null_count() or (ids == "").any():
            raise ValueError(f"Missing {id_column}")
        if ids.n_unique() != frame.height:
            raise ValueError(f"Duplicate {id_column}; run excluded rather than deduplicated")
    bounds = {}
    for name in ("measurement_started_at", "measurement_ended_at", "workload_started_at"):
        if name in schema.names and not pa.types.is_timestamp(schema.field(name).type):
            raise ValueError(f"Boundary column {name} must be a timestamp")
        values = frame[name].unique().to_list() if name in frame.columns and frame.height else []
        if len(values) > 1 or (values and values[0] is None):
            raise ValueError(f"Inconsistent/null {name}")
        row_value = utc(values[0]) if values else None
        footer_value = utc(metadata[name]) if name in metadata else None
        if row_value and footer_value and row_value != footer_value:
            raise ValueError(f"Footer/row disagreement for {name}")
        bounds[name] = row_value or footer_value
    start, end = bounds["measurement_started_at"], bounds["measurement_ended_at"]
    if not start or not end or end <= start:
        raise ValueError(
            "Missing or nonpositive measurement window; no configured-duration fallback"
        )
    if bounds["workload_started_at"] and bounds["workload_started_at"] > start:
        raise ValueError("Workload begins after measurement")
    dimensions = {}
    for name in DIMENSIONS:
        values = frame[name].unique().to_list() if name in frame.columns and frame.height else []
        if len(values) > 1:
            raise ValueError(f"Conflicting scenario dimension {name}")
        value = values[0] if values else schedule.get(name)
        if name in frame.columns and not frame.schema[name].is_numeric():
            raise ValueError(f"Non-numeric scenario dimension {name}")
        if value is not None and not isfinite(value):
            raise ValueError(f"Nonfinite scenario dimension {name}")
        if values and name in schedule and value != schedule[name]:
            raise ValueError(f"Schedule/row disagreement for {name}")
        if name in {"duration", "warmup_duration"} and value is not None and version == "2":
            value *= 1000
        dimensions[
            name + "_ms" if name in {"duration", "warmup_duration", "max_start_delay"} else name
        ] = value
    return frame, Window(start, end), dimensions, metadata
