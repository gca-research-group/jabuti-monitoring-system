from pathlib import Path

from experiment_metrics import analyze_experiments

PARQUET_FOLDER = Path(__file__).resolve().parents[1] / "output" / "experiments"
REPORT_FOLDER = Path(__file__).resolve().parent / "reports"
EXPERIMENT_LABEL = None  # Set to identify matching hardware/network/Fabric configurations.

if __name__ == "__main__":
    result = analyze_experiments(
        parquet_folder=PARQUET_FOLDER,
        report_folder=REPORT_FOLDER,
        experiment_label=EXPERIMENT_LABEL,
    )
    print(f"Input: {result.manifest['input_folder']}")
    print(f"Executions: {', '.join(result.manifest['execution_ids'])}")
    print(f"Runs: {result.runs_summary.height}; reports: {REPORT_FOLDER.resolve()}")
    print(f"Invalid runs: {result.data_quality['invalid_run_count']}; see data_quality.json")
