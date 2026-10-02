import polars as pl

from .throughput import in_window

REQUEST_MAPPING = {
    "accepted": "success",
    "transport_failure": "failure",
    "http_failure": "failure",
    "timeout": "failure",
    "unknown": "unknown",
}
TRANSACTION_MAPPING = {
    "committed": "commit",
    "submission_failure": "failure",
    "gateway_failure": "failure",
    "unknown": "unknown",
}


def category_rows(frame, column, layer, denominator, scope):
    return [
        {
            "layer": layer,
            "category": category,
            "count": count,
            "denominator": denominator,
            "scope": scope,
        }
        for category, count in frame.group_by(column).len().sort(column).iter_rows()
    ]


def pipeline_errors(events, window, mapping):
    cohort = events.filter(in_window("inbound_queue_published", window)).with_columns(
        pl.col("status").replace_strict(mapping, default="unknown").alias("classification")
    )
    failed = cohort.filter(pl.col("classification") == "failure").height
    return {
        "pipeline_failure_events": failed,
        "pipeline_error_denominator": cohort.height,
        "pipeline_failure_rate": failed / cohort.height if cohort.height else None,
    }, category_rows(
        cohort, "status", "pipeline", cohort.height, "measurement_arrival_cohort_observed_status"
    )


def client_errors(requests, window):
    cohort = (
        requests.filter(in_window("dispatch_at", window))
        .with_columns(
            pl.col("outcome")
            .replace_strict(REQUEST_MAPPING, default="unknown")
            .alias("classification")
        )
        .with_columns(
            pl.when(pl.col("finish_at").is_null() | (pl.col("finish_at") < pl.col("dispatch_at")))
            .then(pl.lit("unknown"))
            .otherwise(pl.col("classification"))
            .alias("classification")
        )
        .with_columns(
            pl.when(pl.col("classification") == "failure")
            .then(pl.col("error_category").fill_null(pl.col("outcome")))
            .otherwise(pl.col("classification"))
            .alias("category")
        )
    )
    failed = cohort.filter(pl.col("classification") == "failure").height
    return {
        "client_attempts": cohort.height,
        "client_attempted_requests_per_second": cohort.height / window.seconds,
        "client_failed_attempts": failed,
        "client_accepted_attempts": cohort.filter(pl.col("classification") == "success").height,
        "client_unknown_attempts": cohort.filter(pl.col("classification") == "unknown").height,
        "client_failure_rate": failed / cohort.height if cohort.height else None,
    }, category_rows(
        cohort, "category", "client", cohort.height, "measurement_dispatch_cohort_observed_outcome"
    )


def fabric_outcomes(transactions, window):
    observed = transactions.filter(in_window("commit_observed_at", window)).with_columns(
        pl.when((pl.col("outcome") == "committed") & (pl.col("validation_code") == "VALID"))
        .then(pl.lit("valid"))
        .when(
            (pl.col("outcome") == "committed")
            & pl.col("validation_code").is_not_null()
            & (pl.col("validation_code") != "VALID")
        )
        .then(pl.lit("invalid"))
        .when(pl.col("outcome").is_in(["submission_failure", "gateway_failure"]))
        .then(pl.col("outcome"))
        .otherwise(pl.lit("unknown"))
        .alias("classification")
    )
    valid = observed.filter(pl.col("classification") == "valid").height
    invalid = observed.filter(pl.col("classification") == "invalid").height
    confirmed = valid + invalid
    return {
        "fabric_valid_transactions": valid,
        "fabric_invalid_transactions": invalid,
        "fabric_unknown_observed_transactions": observed.filter(
            pl.col("classification") == "unknown"
        ).height,
        "fabric_unknown_unobserved_transactions": transactions.filter(
            pl.col("commit_observed_at").is_null()
            & (
                ~pl.col("outcome").is_in(["submission_failure", "gateway_failure"])
                | pl.col("outcome").is_null()
            )
        ).height,
        "fabric_submission_failures_whole_run": transactions.filter(
            pl.col("outcome") == "submission_failure"
        ).height,
        "fabric_gateway_failures_whole_run": transactions.filter(
            pl.col("outcome") == "gateway_failure"
        ).height,
        "fabric_valid_transactions_per_second": valid / window.seconds,
        "fabric_error_denominator": confirmed,
        "fabric_invalid_rate": invalid / confirmed if confirmed else None,
    }, category_rows(
        observed,
        "classification",
        "fabric",
        observed.height,
        "measurement_commit_observation_window",
    ) + category_rows(
        transactions.filter(pl.col("outcome").is_in(["submission_failure", "gateway_failure"])),
        "outcome",
        "fabric",
        transactions.height,
        "whole_run_submission_outcomes",
    )
