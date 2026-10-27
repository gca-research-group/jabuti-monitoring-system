# 03: Decouple execution preparation from infrastructure

**What to build:** The preparation worker receives the existing inbound event, validates it, records preparation state, and produces the next pipeline message through separated adapters.

**Blocked by:** 01: Enable independent deployment through five profiles.

**Status:** done

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [x] Keep the preparation RabbitMQ listener as an inbound adapter delegating orchestration and validation to application logic.
- [x] Establish focused execution-persistence and metadata-access ports with adapters over existing database infrastructure; retain entity storage and mappings.
- [x] Separate RabbitMQ publishing from application logic. Reuse an existing suitable publishing port if present; keep compatibility for unmigrated callers and reconcile any overlapping publishing abstractions in ticket 06.
- [x] Preserve contract/platform matching, clause and argument validation, identifiers, execution state changes, payloads, timestamps, publication destinations, and existing rejection/failure behaviour.
- [x] Make the execution-persistence and metadata-access contracts usable by execution and post-execution without inventing a generic persistence framework.
- [x] Retain old shared entry points while downstream workers still depend on them, so the migration can compile between tickets.
- [x] Meaningful application-level checks exercise preparation success and representative validation failures through mocked persistence and publishing dependencies without opening real connections.

Validation: All 46 infrastructure-free checks passed and Maven packaged the single executable artifact. Application checks cover successful preparation, platform/clause/argument validation failures, publication errors, and persistence failure propagation. Adapter checks exercise the real service and DTO mappings with mocked repository and RabbitMQ clients, preserving routing and rejection causes. Standards and ticket-scope specification reviews found no actionable issues. No live database, broker, blockchain, or webhook services were exercised.
