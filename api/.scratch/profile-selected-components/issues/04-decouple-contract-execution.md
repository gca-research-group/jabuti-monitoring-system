# 04: Decouple contract execution from infrastructure

**What to build:** The execution worker processes the existing execution payload and invokes the blockchain through adapters, retaining execution outcomes and outbound notifications.

**Blocked by:** 03: Decouple execution preparation from infrastructure.

**Status:** done

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [x] Use a thin RabbitMQ inbound adapter and separate execution application logic.
- [x] Reuse execution-persistence and metadata-access ports established by ticket 03; reuse the existing blockchain integration interface where suitable rather than introducing a duplicate.
- [x] Keep RabbitMQ publishing and blockchain-specific configuration conversion/client calls outside application logic.
- [x] Preserve contract invocation arguments, stored result, statuses, timestamps, outbound payloads, and existing failure/rejection semantics, including existing publication-failure behaviour.
- [x] Keep execution independently selectable and compatible with combined profiles; migrate only callers owned by this slice and retain shared implementations required elsewhere.
- [x] Verify success and representative invocation/failure outcomes through application entry points with mocked persistence, blockchain, and publishing dependencies; compile without infrastructure.

Validation: All 57 infrastructure-free checks passed and Maven packaged the single executable artifact. Application checks preserve success, invocation errors, lookup/persistence errors, publication-failure behaviour, timestamps, and rejection causes. Adapter checks use real configuration conversion and mappings with mocked repository, RabbitMQ, and existing blockchain-interface clients, including configuration failure before contract lookup. Standards and ticket-scope specification reviews found no actionable issues. No live database, broker, blockchain, or webhook services were exercised.
