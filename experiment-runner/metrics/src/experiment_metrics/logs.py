"""Parse only aggregate counters, never copy request diagnostics or secrets into reports."""

import json
import re

from .discovery import RunKey

SUMMARY = re.compile(
    r"scenario request summary: scenario_id=(\S+) repetition=(\d+) "
    r"requests_sent=(\d+) successful=(\d+) failed=(\d+)(.*)"
)


def request_log_summaries(path, execution_id):
    summaries = {}
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            try:
                message = json.loads(line).get("msg", "")
            except (json.JSONDecodeError, AttributeError):
                message = line
            match = SUMMARY.search(message)
            if not match:
                continue
            scenario, repetition, sent, successful, failed, tail = match.groups()
            key = RunKey(execution_id, scenario, int(repetition))
            counts = {
                "client_requests_sent_whole_run": int(sent),
                "client_successful_requests_whole_run": int(successful),
                "client_failed_requests_whole_run": int(failed),
            }
            if int(successful) + int(failed) != int(sent):
                raise ValueError(f"Inconsistent request log totals for {key}")
            categories = {name: int(count) for name, count in re.findall(r"([\w_]+)=(\d+)", tail)}
            if categories and sum(categories.values()) != int(failed):
                raise ValueError(f"Inconsistent request log categories for {key}")
            if key in summaries:
                raise ValueError(
                    f"Repeated request summary for {key}; use an execution-specific log"
                )
            summaries[key] = counts, categories
    return summaries
