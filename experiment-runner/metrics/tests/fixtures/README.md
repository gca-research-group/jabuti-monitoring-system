# Current Go export contract

`current_go` was generated directly by the project's `parquet-go` exporter and
`monitoring.Sample` schema using an offline temporary Go test. No database, Docker,
SSH, or workload was started. Event timestamps are nanosecond UTC; boundary columns
are microsecond UTC. Footer schema version is 3.

Known values: one successful event, inbound at `2026-01-01T00:00:00Z`, final outbound
100 ms later, measurement duration 2.5 seconds. Throughput is 0.4 events/s and all
latency percentiles are 100 ms. One producer container CPU interval from 0 to 1 s
has 200% CPU, and both memory usage and working set are 2 MiB. Workload dimensions:
10 events/s, 2 integration processes, 1 consumer, 2500 ms duration, zero warm-up,
lambda 0, max start delay 0. Attempt and transaction files are absent.
