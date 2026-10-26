# 01: Enable independent deployment through five profiles

**What to build:** Operators can select any pipeline stage or metadata management independently, or combine roles, using the same artifact. This prerequisite establishes deployment selection without requiring the subsequent ports-and-adapters migration.

**Blocked by:** None (can start immediately).

**Status:** done

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [x] Provide exactly the role profiles intake, preparation, execution, post-execution, and metadata-management; replace producer/consumer role selection without introducing Maven build variants.
- [x] Intake exposes execution submission with existing authentication; metadata management exposes login, users, API keys, blockchain and contract configuration, and existing execution query/management endpoints other than submission.
- [x] Each worker registers only its assigned listener; combined roles register each listener and endpoint once without bean conflicts.
- [x] Worker-only deployments need no HTTP server for control. Remove the existing /start, /stop, and /consumers listener-control endpoints.
- [x] Listeners start automatically. Carry forward concurrency, maximum concurrency, prefetch, acknowledgement, and rejection configuration using YAML and environment variables; allow independent per-stage settings in combined deployments.
- [x] Only metadata-management selects the custom migration bean, including combined deployments. Do not rely solely on disabling migration auto-configuration.
- [x] Scope supporting security, web, and infrastructure configuration so unselected roles do not activate unrelated entry points or demand unrelated settings.
- [x] Focused checks cover role isolation, representative combinations, endpoint ownership, migration-bean selection without executing migrations, and worker setting binding without broker connections.
- [x] Isolate the existing context-loading test if needed so the default validation suite requires no external services; compile/package the artifact.

Validation: All 33 infrastructure-free checks passed, and Maven packaged the single executable artifact. Standards and ticket-scope specification reviews found no actionable issues. Live database operations, migrations, queue delivery, blockchain invocation, and webhook delivery were not exercised.
