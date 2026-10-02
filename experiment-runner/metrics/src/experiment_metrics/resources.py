import math
from datetime import timedelta

import polars as pl

from .throughput import in_window

IDENTITY = ["component", "host", "container_name", "container_id"]


def finite_nonnegative(value):
    return value is not None and math.isfinite(value) and value >= 0


def union_seconds(intervals):
    end = None
    total = 0.0
    for start, stop in sorted(intervals):
        total += max(0, (stop - max(start, end or start)).total_seconds())
        end = max(stop, end or stop)
    return total


def resource_metrics(samples: pl.DataFrame, window, sample_interval_seconds=1.0):
    summaries, quality = [], []
    for group in samples.partition_by(IDENTITY, maintain_order=True):
        identity = {name: group[name][0] for name in IDENTITY}
        memory_rows = group.filter(in_window("timestamp", window)).sort("timestamp").to_dicts()
        cpu_rows, intervals = [], []
        invalid_cpu = 0
        cpu_candidates = group.with_columns(
            (
                pl.col("docker_read_timestamp")
                - pl.duration(milliseconds=pl.col("cpu_interval_ms"))
            ).alias("cpu_interval_start")
        ).with_columns(
            (
                (pl.col("cpu_interval_start") >= window.start)
                & (pl.col("docker_read_timestamp") < window.end)
            ).alias("cpu_in_window")
        )
        for row in cpu_candidates.sort("docker_read_timestamp").iter_rows(named=True):
            end, ms, percent = (
                row["docker_read_timestamp"],
                row["cpu_interval_ms"],
                row["cpu_percent"],
            )
            if end is None or ms is None or not finite_nonnegative(ms) or ms == 0:
                invalid_cpu += 1
                continue
            start = end - timedelta(milliseconds=ms)
            if row["cpu_in_window"]:
                if row["sample_status"] not in {"ok", "partial"} or not finite_nonnegative(percent):
                    invalid_cpu += 1
                    continue
                cpu_rows.append((ms / 1000, percent))
                intervals.append((start, end))
        overlap = any(
            current[0] < previous[1]
            for previous, current in zip(sorted(intervals), sorted(intervals)[1:])
        )
        covered = union_seconds(intervals)
        cpu_seconds = sum(seconds for seconds, _ in cpu_rows)
        summary = {
            **identity,
            "cpu_valid_samples": len(cpu_rows),
            "cpu_invalid_samples": invalid_cpu,
            "cpu_overlapping_intervals": overlap,
            "cpu_mean_percent": sum(s * p for s, p in cpu_rows) / cpu_seconds
            if cpu_seconds and not overlap
            else None,
            "cpu_sampled_peak_percent": max((p for _, p in cpu_rows), default=None),
            "cpu_covered_seconds": covered,
            "cpu_coverage_fraction": covered / window.seconds,
            "sampling_error_count": sum(
                r["sample_status"] not in {"ok", "partial"} for r in memory_rows
            ),
            "partial_sample_count": sum(r["sample_status"] == "partial" for r in memory_rows),
        }
        for column, label in (
            ("memory_usage_bytes", "memory_usage"),
            ("memory_working_set_bytes", "memory_working_set"),
        ):
            valid = [
                r
                for r in memory_rows
                if r["sample_status"] in {"ok", "partial"} and finite_nonnegative(r[column])
            ]
            values = [r[column] / (1024 * 1024) for r in valid]
            coverage = union_seconds(
                [
                    (
                        r["timestamp"],
                        min(
                            window.end, r["timestamp"] + timedelta(seconds=sample_interval_seconds)
                        ),
                    )
                    for r in valid
                ]
            )
            summary.update(
                {
                    f"{label}_mean_mib": sum(values) / len(values) if values else None,
                    f"{label}_sampled_peak_mib": max(values, default=None),
                    f"{label}_valid_samples": len(values),
                    f"{label}_estimated_covered_seconds": coverage,
                    f"{label}_estimated_coverage_fraction": coverage / window.seconds,
                }
            )
        stamps = sorted({r["timestamp"] for r in memory_rows})
        edges = [window.start, *stamps, window.end]
        gaps = [(b - a).total_seconds() for a, b in zip(edges, edges[1:])]
        summary["memory_max_sampling_gap_seconds"] = max(gaps, default=window.seconds)
        summaries.append(summary)
        quality.append(
            {
                **identity,
                "cpu_excluded_samples": group.height - len(cpu_rows),
                "cpu_overlapping_intervals": overlap,
                "cpu_uncovered_seconds": window.seconds - covered,
                "memory_max_sampling_gap_seconds": summary["memory_max_sampling_gap_seconds"],
                "sampling_errors": summary["sampling_error_count"],
                "duplicate_resource_samples": group.height
                - group.unique(subset=["timestamp", "docker_read_timestamp"]).height,
            }
        )
    return summaries, quality
