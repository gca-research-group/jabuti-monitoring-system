import polars as pl

from .config import Window
from .throughput import in_window


def pipeline_latency(events: pl.DataFrame, window: Window, mapping: dict, small_sample=100):
    cohort = events.filter(in_window("inbound_queue_published", window)).with_columns(
        pl.col("status").replace_strict(mapping, default="unknown").alias("classification"),
        (
            (
                pl.col("outbound_queue_processed") - pl.col("inbound_queue_published")
            ).dt.total_nanoseconds()
            / 1_000_000
        ).alias("latency_ms"),
    )
    incomplete = cohort.filter(
        (pl.col("classification") != "failure")
        & (
            pl.col("outbound_queue_processed").is_null()
            | (pl.col("outbound_queue_processed") >= window.end)
        )
    ).height
    samples = cohort.filter(
        (pl.col("classification") == "success")
        & (pl.col("outbound_queue_processed") < window.end)
        & (pl.col("latency_ms") >= 0)
    )["latency_ms"]
    return {
        "latency_cohort_events": cohort.height,
        "latency_successful_samples": len(samples),
        "latency_failed_events": cohort.filter(pl.col("classification") == "failure").height,
        "latency_unknown_events": cohort.filter(pl.col("classification") == "unknown").height,
        "latency_pending_events": cohort.filter(pl.col("classification") == "pending").height,
        "incomplete_at_cutoff_events": incomplete,
        "incomplete_fraction": incomplete / cohort.height if cohort.height else None,
        "latency_small_sample": len(samples) < small_sample,
        **{
            f"latency_p{int(q * 100)}_ms": samples.quantile(q, interpolation="linear")
            if len(samples)
            else None
            for q in (0.50, 0.95, 0.99)
        },
    }


def event_quality(events: pl.DataFrame, mapping: dict):
    negative = events.filter(
        pl.col("outbound_queue_processed") < pl.col("inbound_queue_published")
    ).height
    unknown = events.filter(~pl.col("status").is_in(list(mapping)) | pl.col("status").is_null())
    return {
        "negative_latency_rows": negative,
        "missing_arrival_timestamps": events["inbound_queue_published"].null_count(),
        "missing_completion_timestamps": events["outbound_queue_processed"].null_count(),
        "unknown_status_counts": [
            dict(zip(("status", "count"), row))
            for row in unknown.group_by("status").len().sort("status").iter_rows()
        ],
    }
