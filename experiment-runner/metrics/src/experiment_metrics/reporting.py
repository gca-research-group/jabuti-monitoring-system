import json
from pathlib import Path

import polars as pl

from .aggregation import aggregate_runs


def table(rows, empty_schema):
    return (
        pl.DataFrame(rows, schema_overrides=empty_schema, infer_schema_length=None)
        if rows
        else pl.DataFrame(schema=empty_schema)
    )


def write_reports(result, folder: Path):
    for execution_id in result.runs_summary["execution_id"].unique().sort():
        runs = result.runs_summary.filter(pl.col("execution_id") == execution_id)
        frames = {
            name: getattr(result, name).filter(pl.col("execution_id") == execution_id)
            for name in (
                "runs_summary", "resources_summary", "queues_summary",
                "throughput_timeseries", "errors_by_category",
            )
        }
        frames["scenario_summary"] = aggregate_runs(runs)
        quality = {
            **result.data_quality,
            "runs": [r for r in result.data_quality["runs"] if r["execution_id"] == execution_id],
            "invalid_run_count": runs.filter(pl.col("analysis_status") == "invalid").height,
        }
        request_logs = result.manifest["configuration"]["request_logs"]
        log_path = request_logs.get(execution_id)
        quality["request_logs"] = [
            r for r in quality["request_logs"] if r["path"] == log_path
        ]
        inputs = [
            item for item in result.manifest["inputs"]
            if Path(item["path"]).parent.parent.name == execution_id
            or (Path(item["path"]).name == "scenarios.csv"
                and Path(item["path"]).parent.name == execution_id)
            or item["path"] == log_path
        ]
        paths = {item["path"] for item in inputs}
        manifest = {
            **result.manifest,
            "execution_ids": [execution_id],
            "inputs": inputs,
            "dataset_metadata": {
                path: value for path, value in result.manifest["dataset_metadata"].items()
                if path in paths
            },
            "configuration": {
                **result.manifest["configuration"],
                "request_logs": {execution_id: log_path} if log_path else {},
            },
        }
        _write_report_files(frames, quality, manifest, folder / execution_id)


def _write_report_files(frames, quality, manifest, folder):
    folder.mkdir(parents=True, exist_ok=True)
    for name in (
        "runs_summary",
        "resources_summary",
        "queues_summary",
        "throughput_timeseries",
        "errors_by_category",
    ):
        frame = frames[name]
        frame.write_parquet(folder / f"{name}.parquet", compression="zstd")
        if name in {"runs_summary", "resources_summary", "queues_summary"}:
            frame.write_csv(folder / f"{name}.csv")
    frames["scenario_summary"].write_csv(folder / "scenario_summary.csv")
    for name, value in (
        ("data_quality", quality),
        ("analysis_manifest", manifest),
    ):
        (folder / f"{name}.json").write_text(
            json.dumps(value, indent=2, sort_keys=True, default=str, allow_nan=False) + "\n",
            encoding="utf-8",
        )
