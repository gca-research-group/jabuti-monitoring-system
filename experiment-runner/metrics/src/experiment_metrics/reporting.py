import json
from pathlib import Path

import polars as pl


def table(rows, empty_schema):
    return (
        pl.DataFrame(rows, schema_overrides=empty_schema, infer_schema_length=None)
        if rows
        else pl.DataFrame(schema=empty_schema)
    )


def write_reports(result, folder: Path):
    folder.mkdir(parents=True, exist_ok=True)
    for name in (
        "runs_summary",
        "resources_summary",
        "queues_summary",
        "throughput_timeseries",
        "errors_by_category",
    ):
        frame = getattr(result, name)
        frame.write_parquet(folder / f"{name}.parquet", compression="zstd")
        if name in {"runs_summary", "resources_summary", "queues_summary"}:
            frame.write_csv(folder / f"{name}.csv")
    result.scenario_summary.write_csv(folder / "scenario_summary.csv")
    for name, value in (
        ("data_quality", result.data_quality),
        ("analysis_manifest", result.manifest),
    ):
        (folder / f"{name}.json").write_text(
            json.dumps(value, indent=2, sort_keys=True, default=str, allow_nan=False) + "\n",
            encoding="utf-8",
        )
