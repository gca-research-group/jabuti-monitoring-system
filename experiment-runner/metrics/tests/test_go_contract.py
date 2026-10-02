from pathlib import Path

from experiment_metrics import analyze_experiments


def test_actual_go_parquet_schema_and_independent_answers(tmp_path):
    source = Path(__file__).parent / "fixtures/current_go"
    first = analyze_experiments(source, tmp_path / "reports")
    second = analyze_experiments(source, tmp_path / "reports")
    assert first.data_quality["invalid_run_count"] == 0
    row = first.runs_summary.row(0, named=True)
    assert row["pipeline_completed_events_per_second"] == 1 / 2.5
    assert row["target_offered_events_per_second"] == 10 * 2
    assert row["latency_successful_samples"] == 1
    assert row["latency_p50_ms"] == row["latency_p95_ms"] == row["latency_p99_ms"] == 100
    assert row["fabric_valid_transactions_per_second"] is None
    resource = first.resources_summary.row(0, named=True)
    assert resource["cpu_mean_percent"] == 200
    assert resource["memory_usage_mean_mib"] == 2097152 / 1048576
    assert resource["cpu_covered_seconds"] == 1
    assert resource["cpu_coverage_fraction"] == 1 / 2.5
    assert first.runs_summary.equals(second.runs_summary)
    assert first.resources_summary.equals(second.resources_summary)
