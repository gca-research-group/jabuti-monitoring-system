# 07: Verify and document the complete deployment model

**What to build:** Operators receive a documented, coherent deployment model for the single artifact, with infrastructure-free evidence of profile/configuration selection and clear limits on live verification.

**Blocked by:** 06: Complete metadata and authentication separation.

**Status:** done

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [x] Compile and package one executable artifact, and run the applicable infrastructure-free automated checks without requiring skipped service-dependent tests as the normal workflow.
- [x] Verify individual roles, representative mixed roles, and all roles together using isolated contexts with mocked infrastructure; verify listener and endpoint selection without starting consumers or opening external connections.
- [x] Check migration-bean ownership, independent worker setting binding, retained security, and absence of removed listener-control endpoints.
- [x] Document profile names, same-machine and different-machine placement, combined-role operation, endpoint ownership, migration startup order, and required environment settings for each role.
- [x] Update applicable deployment examples to select the new profiles and use the same artifact; do not introduce per-role builds or require producer/consumer aliases.
- [x] Document how to run automated checks without services. Any smoke check against an already available development environment is optional and requires no new setup.
- [x] Report compilation and mocked-context results separately from live startup, broker delivery, database operations, and migrations; label live behaviour unverified unless actually exercised.
- [x] Confirm application code is organised by responsibility and infrastructure access is isolated without schema, message, business-rule, or reliability redesign.

Validation: `mvn -o clean package` passed: 85 tests, zero failures/errors/skips, and one executable JAR packaged. Individual, mixed, and all-role contexts use mocked external boundaries; listeners remain stopped. Migration selection is checked with execution suppressed. Existing Compose examples were inspected and already use the same artifact with the new profiles. Build log: `target/ticket-07-validation.log`.

Separate Standards and Spec reviews against `b94cb8d5836ca1e9d211f23e8178600c2c98f7e4` reported zero findings. Live startup, broker delivery, database operations, migrations, blockchain invocation, and webhook delivery remain unverified; no infrastructure setup was introduced.
