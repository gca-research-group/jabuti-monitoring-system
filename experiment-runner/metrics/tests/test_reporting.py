import csv
import json

import polars as pl

from experiment_metrics import analyze_experiments

from conftest import write_events


def test_end_to_end_reproducible_empty_missing_and_corrupt(tmp_path):
    inputs = tmp_path / "data"
    write_events(inputs / "execution/scenario/1.parquet", [("a", "PROCESSED", 0, 0.1)])
    write_events(inputs / "execution/scenario/2.parquet")
    corrupt = inputs / "execution/scenario/3.parquet"
    corrupt.write_bytes(b"not parquet")
    (inputs / "execution/scenarios.csv").write_text(
        "ExecutionId,ScenarioId,Repetition,Events,Duration,IntegrationProcesses\n"
        + "".join(f"execution,scenario,{i},10,2500,2\n" for i in range(1, 5))
    )
    first = analyze_experiments(inputs, tmp_path / "reports")
    second = analyze_experiments(inputs, tmp_path / "reports")
    for name in (
        "runs_summary",
        "resources_summary",
        "throughput_timeseries",
        "errors_by_category",
        "scenario_summary",
    ):
        assert getattr(first, name).equals(getattr(second, name))
    assert first.data_quality == second.data_quality
    assert first.runs_summary["analysis_status"].to_list() == [
        "analyzed",
        "analyzed",
        "invalid",
        "missing",
    ]
    assert first.runs_summary["pipeline_completed_events_per_second"].to_list() == [
        0.4,
        0,
        None,
        None,
    ]
    assert first.runs_summary["fabric_valid_transactions_per_second"].null_count() == 4
    assert first.scenario_summary["repetition_count"].to_list() == [2]
    assert first.scenario_summary["latency_p99_ms_available_repetitions"].to_list() == [1]
    manifest = json.loads((tmp_path / "reports/analysis_manifest.json").read_text())
    assert manifest["quantile_method"] == "linear"
    assert len(manifest["inputs"]) == 4
    assert pl.read_parquet(tmp_path / "reports/runs_summary.parquet").height == 4
    with (tmp_path / "reports/runs_summary.csv").open() as stream:
        assert len(list(csv.DictReader(stream))) == 4


def test_no_accidental_aggregation_across_executions(tmp_path):
    for execution in ("e1", "e2"):
        write_events(tmp_path / f"data/{execution}/scenario/1.parquet")
        (tmp_path / f"data/{execution}/scenarios.csv").write_text(
            "ExecutionId,ScenarioId,Repetition,Events,IntegrationProcesses,Consumers,Lambda,"
            "MaxStartDelay,WarmupDuration,Duration\n"
            f"{execution},scenario,1,10,2,1,0.5,0,0,2500\n"
        )
    result = analyze_experiments(tmp_path / "data", tmp_path / "reports")
    assert result.scenario_summary.height == 2
    result = analyze_experiments(
        tmp_path / "data", tmp_path / "reports", experiment_label="same-config"
    )
    assert result.scenario_summary.height == 1
    assert result.scenario_summary["repetition_count"][0] == 2


def test_mixed_schema_versions_normalize_duration_without_merging_runs(tmp_path):
    for execution, version in (("old", "2"), ("new", "3")):
        write_events(tmp_path / f"data/{execution}/scenario/1.parquet", version=version)
        duration = 3 if version == "2" else 3000
        (tmp_path / f"data/{execution}/scenarios.csv").write_text(
            "ExecutionId,ScenarioId,Repetition,Events,IntegrationProcesses,Consumers,Lambda,"
            "MaxStartDelay,WarmupDuration,Duration\n"
            f"{execution},scenario,1,10,2,1,0.5,0,0,{duration}\n"
        )
    result = analyze_experiments(
        tmp_path / "data", tmp_path / "reports", experiment_label="matching"
    )
    assert result.runs_summary.height == 2
    assert result.runs_summary["duration_ms"].to_list() == [3000, 3000]
    assert result.scenario_summary["repetition_count"].to_list() == [2]
