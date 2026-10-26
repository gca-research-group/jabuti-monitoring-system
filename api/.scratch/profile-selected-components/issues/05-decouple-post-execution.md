# 05: Decouple post-execution handling from infrastructure

**What to build:** The post-execution worker consumes existing outbound messages, resolves configured actions, and delivers webhooks through adapters.

**Blocked by:** 03: Decouple execution preparation from infrastructure.

**Status:** ready-for-agent

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [ ] Keep the outbound RabbitMQ listener as an inbound adapter delegating to post-execution application logic.
- [ ] Reuse execution-persistence and metadata-access ports established by ticket 03 for loading executions and configured contract actions and storing completion timestamps.
- [ ] Move HTTP request/client handling into a focused webhook adapter behind a delivery port.
- [ ] Preserve action selection, destination URLs, HTTP request contents including the existing webhook payload, timestamps, errors, and observable delivery behaviour; do not redesign retries or asynchronous execution incidentally.
- [ ] Keep the role independently selectable and combinable without exposing HTTP listener-control endpoints.
- [ ] Use application-level checks with mocked persistence and webhook delivery to verify configured actions, no-action cases, and existing error propagation without network requests.
