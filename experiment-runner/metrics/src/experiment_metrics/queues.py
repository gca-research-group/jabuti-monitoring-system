"""Post-shutdown message counts, independent of measurement-window metrics."""

import polars as pl
import pyarrow as pa
import pyarrow.parquet as pq

from .schemas import QUEUE_SCHEMA

QUEUE_METRICS = (
    "queue_messages_ready",
    "queue_messages_unacknowledged",
    "queue_messages_total",
)


def load_queues(path, key):
    parquet = pq.ParquetFile(path)
    metadata = {
        k.decode(): v.decode()
        for k, v in (parquet.metadata.metadata or {}).items()
        if k != b"ARROW:schema"
    }
    if metadata.get("schema_version") != "1":
        raise ValueError("Unsupported/missing queue schema_version; expected 1")
    schema = parquet.schema_arrow
    for name, expected in QUEUE_SCHEMA.items():
        if name not in schema.names:
            raise ValueError(f"Missing required queue column {name}")
        dtype = schema.field(name).type
        valid = (
            (
                expected == pl.String
                and (pa.types.is_string(dtype) or pa.types.is_large_string(dtype))
            )
            or (expected == pl.Int64 and pa.types.is_integer(dtype))
            or (name == "captured_at" and pa.types.is_timestamp(dtype))
        )
        if not valid:
            raise ValueError(f"Incompatible queue column {name}: {dtype}")
    timestamp_type = schema.field("captured_at").type
    if timestamp_type.tz not in {None, "UTC", "Etc/UTC", "+00:00"}:
        raise ValueError("Queue captured_at must be UTC")
    frame = pl.from_arrow(parquet.read()).select(list(QUEUE_SCHEMA))
    # Go time.Time exports UTC timestamps without an Arrow timezone annotation.
    if timestamp_type.tz is None:
        frame = frame.with_columns(pl.col("captured_at").dt.replace_time_zone("UTC"))
    frame = frame.cast(QUEUE_SCHEMA)
    if any(frame[name].null_count() for name in QUEUE_SCHEMA):
        raise ValueError("Null queue snapshot fields")
    for name, expected in key.as_dict().items():
        if frame.height and frame[name].unique().to_list() != [expected]:
            raise ValueError(f"Queue run identity mismatch in {name}")
    if (frame["virtual_host"] == "").any() or (frame["queue_name"] == "").any():
        raise ValueError("Empty virtual host or queue name")
    if frame.select("virtual_host", "queue_name").n_unique() != frame.height:
        raise ValueError("Duplicate virtual-host/queue pair")
    totals = [0, 0, 0]
    for ready, unacknowledged, total in frame.select(
        "messages_ready", "messages_unacknowledged", "messages"
    ).iter_rows():
        if min(ready, unacknowledged, total) < 0 or total != ready + unacknowledged:
            raise ValueError("Invalid queue message counts")
        for index, value in enumerate((ready, unacknowledged, total)):
            totals[index] += value
            if totals[index] > 2**63 - 1:
                raise ValueError("Queue message total exceeds Int64")
    metrics = dict(zip(QUEUE_METRICS, totals, strict=True))
    metrics.update(queue_count=frame.height, queue_metrics_available=True)
    return frame.sort("virtual_host", "queue_name"), metrics, metadata
