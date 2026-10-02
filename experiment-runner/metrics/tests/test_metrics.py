from datetime import timedelta

import polars as pl
import pytest

from experiment_metrics.config import STATUS_MAPPING
from experiment_metrics.errors import client_errors, fabric_outcomes
from experiment_metrics.latency import event_quality, pipeline_latency
from experiment_metrics.throughput import pipeline_throughput

from conftest import START, events


def test_throughput_warmup_and_half_open_bins(window):
    frame = events(
        [
            ("warm", "PROCESSED", -1, 0),
            ("end", "PROCESSED", 0, 2.5),
            ("latebin", "PROCESSED", 0, 2.25),
            ("failed", "FAILED", 0, 1),
            ("unknown", "new", 0, 1.2),
        ]
    )
    metrics, bins = pipeline_throughput(frame, window, STATUS_MAPPING)
    assert metrics["pipeline_completed_events_per_second"] == 0.8
    assert metrics["pipeline_terminal_completions"] == 3
    assert metrics["pipeline_unclassified_completions"] == 1
    assert [b["successful_completions"] for b in bins] == [1, 0, 1]
    assert bins[-1]["completed_events_per_second"] == 2
    assert bins[-1]["bin_duration_seconds"] == 0.5


def test_linear_quantiles_and_quality(window):
    frame = events(
        [
            ("a", "PROCESSED", 0, 0.1),
            ("b", "PROCESSED", 0, 0.2),
            ("c", "PROCESSED", 0, 0.3),
            ("d", "PROCESSED", 0, 0.4),
            ("negative", "PROCESSED", 1, 0),
            ("pending", "PENDING", 0, None),
            ("end", "PROCESSED", 0, 2.5),
            ("failed", "FAILED", 0, None),
            ("unknown", "UNRECOGNIZED", 0, None),
            ("null", "PROCESSED", None, 1),
        ]
    )
    metrics = pipeline_latency(frame, window, STATUS_MAPPING)
    assert metrics["latency_p50_ms"] == 250
    assert metrics["latency_p95_ms"] == pytest.approx(385)
    assert metrics["latency_p99_ms"] == pytest.approx(397)
    assert metrics["latency_cohort_events"] == 9
    assert metrics["latency_successful_samples"] == 4
    assert metrics["latency_failed_events"] == 1
    assert metrics["incomplete_at_cutoff_events"] == 3
    assert metrics["latency_unknown_events"] == 1
    assert metrics["latency_small_sample"]
    assert event_quality(frame, STATUS_MAPPING)["negative_latency_rows"] == 1


def test_no_observations(window):
    metrics = pipeline_latency(events([]), window, STATUS_MAPPING)
    assert metrics["latency_p99_ms"] is None
    assert metrics["incomplete_fraction"] is None
    throughput, bins = pipeline_throughput(events([]), window, STATUS_MAPPING)
    assert throughput["pipeline_completed_events_per_second"] == 0
    assert [b["successful_completions"] for b in bins] == [0, 0, 0]


def test_nanosecond_negative_latency_not_rounded_to_zero(window):
    frame = events([("e", "PROCESSED", 1, 1)]).with_columns(
        (
            pl.col("outbound_queue_processed").cast(pl.Datetime("ns", "UTC"))
            - pl.duration(nanoseconds=1)
        ).alias("outbound_queue_processed")
    )
    result = pipeline_latency(frame, window, STATUS_MAPPING)
    assert result["latency_successful_samples"] == 0
    assert result["latency_p99_ms"] is None
    assert event_quality(frame, STATUS_MAPPING)["negative_latency_rows"] == 1


def test_client_attempt_cohort_retries_and_unresolved(window):
    frame = pl.DataFrame(
        {
            "attempt_id": ["retry1", "retry2", "unresolved", "warmup", "cutoff"],
            "event_id": ["e", "e", "absent", "warm", "cutoff"],
            "dispatch_at": [START, START, START, START - timedelta(seconds=1), window.end],
            "finish_at": [START, START, None, START, window.end],
            "outcome": ["timeout", "accepted", "http_failure", "timeout", "timeout"],
            "error_category": ["timeout", None, "http_500", "timeout", "timeout"],
        }
    )
    metrics, categories = client_errors(frame, window)
    assert metrics["client_attempts"] == 3
    assert metrics["client_failed_attempts"] == 1
    assert metrics["client_unknown_attempts"] == 1
    assert metrics["client_failure_rate"] == pytest.approx(1 / 3)
    assert sum(c["count"] for c in categories) == 3


def test_fabric_observation_times(window):
    frame = pl.DataFrame(
        {
            "transaction_id": ["a", "b", "c", "d", "e"],
            "event_id": ["e"] * 5,
            "commit_observed_at": [START, START, START, None, window.end],
            "validation_code": ["VALID", "MVCC_READ_CONFLICT", None, None, "VALID"],
            "outcome": ["committed", "committed", "unknown", "submission_failure", "committed"],
        }
    )
    metrics, _ = fabric_outcomes(frame, window)
    assert metrics["fabric_valid_transactions_per_second"] == 0.4
    assert metrics["fabric_invalid_rate"] == 0.5
    assert metrics["fabric_unknown_observed_transactions"] == 1
    assert metrics["fabric_unknown_unobserved_transactions"] == 0
    assert metrics["fabric_submission_failures_whole_run"] == 1
