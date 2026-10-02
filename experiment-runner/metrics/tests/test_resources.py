from datetime import timedelta

import polars as pl
import pytest

from experiment_metrics.resources import resource_metrics

from conftest import START


def samples(rows):
    return pl.DataFrame(
        [
            {
                "component": "peer",
                "host": "host",
                "container_name": "peer0",
                "container_id": "id",
                "timestamp": START + timedelta(seconds=end),
                "docker_read_timestamp": START + timedelta(seconds=end),
                "cpu_interval_ms": ms,
                "cpu_percent": cpu,
                "memory_usage_bytes": 2 * 1024 * 1024 if status != "error" else None,
                "memory_working_set_bytes": 1024 * 1024 if status != "error" else None,
                "sample_status": status,
            }
            for end, ms, cpu, status in rows
        ]
    )


def test_weighted_multicore_cpu_and_memory(window):
    frame = samples(
        [(0, 1000, 999, "ok"), (0.5, 500, 100, "ok"), (2, 1500, 300, "ok"), (2.5, 500, 999, "ok")]
    )
    rows, _ = resource_metrics(frame, window)
    row = rows[0]
    assert row["cpu_mean_percent"] == 250
    assert row["cpu_sampled_peak_percent"] == 300
    assert row["cpu_covered_seconds"] == 2
    assert row["cpu_coverage_fraction"] == 0.8
    assert row["memory_usage_mean_mib"] == 2
    assert row["memory_working_set_sampled_peak_mib"] == 1


def test_sparse_null_and_failure_samples(window):
    rows, quality = resource_metrics(
        samples([(0.5, 500, None, "partial"), (2, 500, 400, "error")]), window
    )
    assert rows[0]["cpu_mean_percent"] is None
    assert rows[0]["sampling_error_count"] == 1
    assert rows[0]["memory_usage_valid_samples"] == 1
    assert rows[0]["memory_usage_estimated_coverage_fraction"] == pytest.approx(0.4)
    assert quality[0]["cpu_uncovered_seconds"] == 2.5


def test_overlaps_and_replicas(window):
    frame = samples([(1, 1000, 100, "ok"), (1.5, 1000, 200, "ok")])
    replica = frame.with_columns(pl.lit("other").alias("container_id"))
    rows, quality = resource_metrics(pl.concat([frame, replica]), window)
    assert len(rows) == 2
    assert all(row["cpu_mean_percent"] is None for row in rows)
    assert all(row["cpu_overlapping_intervals"] for row in quality)
