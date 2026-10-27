# 06: Complete metadata and authentication separation

**What to build:** Management and authentication operations retain their existing HTTP behaviour while all application paths use the organised adapter structure, allowing superseded shared code to be removed safely.

**Blocked by:** 02: Decouple execution intake from infrastructure; 03: Decouple execution preparation from infrastructure; 04: Decouple contract execution from infrastructure; 05: Decouple post-execution handling from infrastructure.

**Status:** done

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [x] Separate management HTTP adapters from application logic for login, users, API keys, blockchain and contract configuration, and retained execution query/management operations.
- [x] Isolate database access behind focused persistence ports/adapters, reusing contracts already established where appropriate; remove direct infrastructure-client dependencies from affected application logic.
- [x] Preserve access rules, password/token handling, filtering, pagination, validation, response mapping, and existing persistence behaviour.
- [x] Intake can still validate credentials without exposing management endpoints; worker roles do not require unrelated web/security configuration.
- [x] Retain migration ownership established in ticket 01 and leave schema/changelogs unchanged.
- [x] After all callers migrate, remove superseded shared implementations, duplicated ports, obsolete role configuration, and obsolete package locations without breaking remaining callers.
- [x] Focused checks verify representative management and authentication behaviour with mocked external dependencies; package and existing applicable checks remain green without services.

Validation: `mvn -o clean package` passed with 82 tests, zero failures/errors/skips. Checks use mocked dependencies and scoped Spring contexts; no live infrastructure was verified or required. Build log: `target/ticket-06-validation.log`.

Review: separate Standards and Spec reviews against `7138e493e37113c09b57d520109ebf20710bd6e4` found no actionable findings.
