from datetime import timedelta
from math import ceil

import polars as pl

from .config import Window


def in_window(column: str, window: Window):
    return (pl.col(column) >= window.start) & (pl.col(column) < window.end)


def pipeline_throughput(events: pl.DataFrame, window: Window, mapping: dict):
    classified = pl.col("status").replace_strict(mapping, default="unknown")
    terminal = events.filter(in_window("outbound_queue_processed", window))
    successful = terminal.filter(classified == "success")
    unsuccessful = terminal.filter(classified == "failure").height
    counts = (
        successful.select(
            (
                (pl.col("outbound_queue_processed") - pl.lit(window.start)).dt.total_nanoseconds()
                // 1_000_000_000
            ).alias("bin")
        )
        .group_by("bin")
        .len()
    )
    lookup = dict(counts.iter_rows())
    bins = []
    for index in range(ceil(window.seconds)):
        start = window.start + timedelta(seconds=index)
        end = min(start + timedelta(seconds=1), window.end)
        seconds = (end - start).total_seconds()
        count = lookup.get(index, 0)
        bins.append(
            {
                "bin_started_at": start,
                "bin_ended_at": end,
                "bin_duration_seconds": seconds,
                "successful_completions": count,
                "completed_events_per_second": count / seconds,
            }
        )
    return {
        "pipeline_successful_completions": successful.height,
        "pipeline_terminal_completions": successful.height + unsuccessful,
        "pipeline_unsuccessful_completions": unsuccessful,
        "pipeline_unclassified_completions": terminal.height - successful.height - unsuccessful,
        "pipeline_completed_events_per_second": successful.height / window.seconds,
    }, bins
