import csv
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

PATTERN = re.compile(r"([0-9]+)(?:\.(resources|requests|transactions))?\.parquet$")
CSV_NAMES = {
    "ExecutionId": "execution_id",
    "ScenarioId": "scenario_id",
    "Repetition": "repetition",
    "Events": "events",
    "IntegrationProcesses": "integration_processes",
    "Consumers": "consumers",
    "Lambda": "lambda",
    "Duration": "duration",
    "MaxStartDelay": "max_start_delay",
    "WarmupDuration": "warmup_duration",
}


@dataclass(frozen=True, order=True)
class RunKey:
    execution_id: str
    scenario_id: str
    repetition: int

    def as_dict(self):
        return vars(self)


def identity(path: Path) -> dict:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {
        "path": str(path.resolve()),
        "size_bytes": path.stat().st_size,
        "sha256": digest.hexdigest(),
    }


def discover(folder: Path, report_folder: Path):
    folder = folder.resolve(strict=True)
    if not folder.is_dir():
        raise ValueError(f"Input must be a directory: {folder}")
    runs, schedules, inputs = {}, {}, []
    for path in sorted(folder.rglob("*.parquet")):
        if path.is_relative_to(report_folder):
            continue
        match = PATTERN.fullmatch(path.name)
        if not match or int(match[1]) <= 0:
            continue
        if len(path.relative_to(folder).parts) < 2:
            raise ValueError(f"Expected execution/scenario/repetition layout: {path}")
        key = RunKey(path.parent.parent.name, path.parent.name, int(match[1]))
        kind = match[2] or "events"
        files = runs.setdefault(key, {})
        if kind in files:
            raise ValueError(f"Duplicate {kind} dataset for {key}: {path}")
        files[kind] = path
        inputs.append(identity(path))
    if not runs:
        raise ValueError(f"No recognized finalized datasets in {folder}")
    for execution in sorted({p.parent.parent for files in runs.values() for p in files.values()}):
        path = execution / "scenarios.csv"
        if not path.exists():
            continue
        inputs.append(identity(path))
        with path.open(newline="", encoding="utf-8-sig") as stream:
            for row in csv.DictReader(stream):
                row = {CSV_NAMES.get(k, k): v for k, v in row.items()}
                key = RunKey(row["execution_id"], row["scenario_id"], int(row["repetition"]))
                values = {
                    k: (float(v) if k == "lambda" else int(v))
                    for k, v in row.items()
                    if k in CSV_NAMES.values() and k not in key.as_dict() and v != ""
                }
                if key in schedules and schedules[key] != values:
                    raise ValueError(f"Conflicting schedule rows: {key}")
                schedules[key] = values
    return folder, runs, schedules, inputs
