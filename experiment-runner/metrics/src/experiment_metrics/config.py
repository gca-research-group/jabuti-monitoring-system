from dataclasses import dataclass, field
from datetime import datetime
from math import isfinite

DIMENSIONS = (
    "events",
    "integration_processes",
    "consumers",
    "lambda",
    "max_start_delay",
    "warmup_duration",
    "duration",
)
STATUS_MAPPING = {
    "PROCESSED": "success",
    "FAILED": "failure",
    "PENDING": "pending",
    "PROCESSING": "pending",
}


@dataclass(frozen=True)
class AnalysisConfig:
    experiment_label: str | None = None
    status_mapping: dict[str, str] = field(default_factory=lambda: dict(STATUS_MAPPING))
    sample_interval_seconds: float = 1.0
    small_sample_threshold: int = 100

    def __post_init__(self):
        if self.experiment_label is not None and not self.experiment_label.strip():
            raise ValueError("Experiment label must be nonempty when provided")
        if (
            not isfinite(self.sample_interval_seconds)
            or self.sample_interval_seconds <= 0
            or self.small_sample_threshold < 1
        ):
            raise ValueError("Sample interval and small-sample threshold must be positive")
        if not set(self.status_mapping.values()) <= {"success", "failure", "pending", "unknown"}:
            raise ValueError("Status mapping values must be success/failure/pending/unknown")


@dataclass(frozen=True)
class Window:
    start: datetime
    end: datetime

    def __post_init__(self):
        if (
            self.start.tzinfo is None
            or self.end.tzinfo is None
            or self.start.utcoffset().total_seconds() != 0
            or self.end.utcoffset().total_seconds() != 0
            or self.end <= self.start
        ):
            raise ValueError("Window requires UTC timestamps with end after start")

    @property
    def seconds(self) -> float:
        return (self.end - self.start).total_seconds()
