import polars as pl

from .config import DIMENSIONS

DIMENSION_COLUMNS = [
    name + "_ms" if name in {"duration", "warmup_duration", "max_start_delay"} else name
    for name in DIMENSIONS
]
METRICS = [
    "pipeline_completed_events_per_second",
    "latency_p50_ms",
    "latency_p95_ms",
    "latency_p99_ms",
    "pipeline_failure_rate",
    "client_failure_rate",
    "client_attempted_requests_per_second",
    "fabric_valid_transactions_per_second",
    "fabric_invalid_rate",
    "incomplete_fraction",
]


def aggregate_runs(runs: pl.DataFrame):
    empty_schema = {
        "configuration_group": pl.String,
        **{col: pl.Float64 if col == "lambda" else pl.Int64 for col in DIMENSION_COLUMNS},
        "repetition_count": pl.Int64,
        **{
            f"{metric}_{suffix}": pl.Int64 if suffix == "available_repetitions" else pl.Float64
            for metric in METRICS
            for suffix in ("repetition_mean", "repetition_stddev", "available_repetitions")
        },
    }
    if not runs.height:
        return pl.DataFrame(schema=empty_schema)
    valid = runs.filter(pl.col("analysis_status") == "analyzed")
    if not valid.height:
        return pl.DataFrame(schema=empty_schema)
    # With no externally verified configuration label, never combine scenarios/executions.
    valid = valid.with_columns(
        pl.when(
            pl.col("experiment_label").is_not_null()
            & pl.all_horizontal(pl.col(name).is_not_null() for name in DIMENSION_COLUMNS)
        )
        .then(pl.col("experiment_label"))
        .otherwise(pl.concat_str(["execution_id", "scenario_id"], separator="/"))
        .alias("configuration_group")
    )
    return valid.group_by(["configuration_group", *DIMENSION_COLUMNS], maintain_order=True).agg(
        pl.len().alias("repetition_count"),
        *[
            expr
            for metric in METRICS
            for expr in (
                pl.col(metric).mean().alias(f"{metric}_repetition_mean"),
                pl.col(metric).std(ddof=1).alias(f"{metric}_repetition_stddev"),
                pl.col(metric).count().alias(f"{metric}_available_repetitions"),
            )
        ],
    )
