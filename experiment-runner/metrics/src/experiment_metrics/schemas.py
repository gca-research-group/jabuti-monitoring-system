"""Stable report schemas, including when an optional dataset has no observations."""

import polars as pl

KEY_SCHEMA = {"execution_id": pl.String, "scenario_id": pl.String, "repetition": pl.Int64}
QUEUE_SCHEMA = {
    **KEY_SCHEMA,
    "captured_at": pl.Datetime("us", "UTC"),
    "virtual_host": pl.String,
    "queue_name": pl.String,
    "messages_ready": pl.Int64,
    "messages_unacknowledged": pl.Int64,
    "messages": pl.Int64,
}
RUN_COUNTS = (
    "pipeline_successful_completions",
    "pipeline_terminal_completions",
    "pipeline_unsuccessful_completions",
    "pipeline_unclassified_completions",
    "latency_cohort_events",
    "latency_successful_samples",
    "latency_failed_events",
    "latency_unknown_events",
    "latency_pending_events",
    "incomplete_at_cutoff_events",
    "pipeline_failure_events",
    "pipeline_error_denominator",
    "client_attempts",
    "client_failed_attempts",
    "client_accepted_attempts",
    "client_unknown_attempts",
    "fabric_valid_transactions",
    "fabric_invalid_transactions",
    "fabric_error_denominator",
    "fabric_unknown_observed_transactions",
    "fabric_unknown_unobserved_transactions",
    "fabric_submission_failures_whole_run",
    "fabric_gateway_failures_whole_run",
    "client_requests_sent_whole_run",
    "client_successful_requests_whole_run",
    "client_failed_requests_whole_run",
)
RESOURCE_SCHEMA = {
    **KEY_SCHEMA,
    **{name: pl.String for name in ("component", "host", "container_name", "container_id")},
    **{
        name: pl.Int64
        for name in (
            "cpu_valid_samples",
            "cpu_invalid_samples",
            "sampling_error_count",
            "partial_sample_count",
            "memory_usage_valid_samples",
            "memory_working_set_valid_samples",
        )
    },
    "cpu_overlapping_intervals": pl.Boolean,
    **{
        name: pl.Float64
        for name in (
            "cpu_mean_percent",
            "cpu_sampled_peak_percent",
            "cpu_covered_seconds",
            "cpu_coverage_fraction",
            "memory_max_sampling_gap_seconds",
        )
    },
    **{
        f"{prefix}_{suffix}": pl.Float64
        for prefix in ("memory_usage", "memory_working_set")
        for suffix in (
            "mean_mib",
            "sampled_peak_mib",
            "estimated_covered_seconds",
            "estimated_coverage_fraction",
        )
    },
}
TIMESERIES_SCHEMA = {
    **KEY_SCHEMA,
    "bin_started_at": pl.Datetime("us", "UTC"),
    "bin_ended_at": pl.Datetime("us", "UTC"),
    "bin_duration_seconds": pl.Float64,
    "successful_completions": pl.Int64,
    "completed_events_per_second": pl.Float64,
}
ERROR_SCHEMA = {
    **KEY_SCHEMA,
    "layer": pl.String,
    "category": pl.String,
    "count": pl.Int64,
    "denominator": pl.Int64,
    "scope": pl.String,
}
