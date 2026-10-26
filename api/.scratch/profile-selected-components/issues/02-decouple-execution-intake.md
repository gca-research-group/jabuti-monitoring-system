# 02: Decouple execution intake from infrastructure

**What to build:** Authenticated execution submission remains available through intake, while HTTP handling and RabbitMQ publishing are separated from application decisions.

**Blocked by:** 01: Enable independent deployment through five profiles.

**Status:** ready-for-agent

Scope: Follow the specification for profile-selected components and ADR 0001. Keep one Maven module and one executable JAR. Preserve business behaviour, database schema, queue topology, messages, and retained HTTP contracts. Add only ports needed for actual external interactions; no new domain model, outbox, retry redesign, or speculative framework.

Automated validation must require no Testcontainers, Docker, embedded infrastructure, PostgreSQL, RabbitMQ, blockchain node, webhook server, or production credentials. Use focused contexts or application-level checks with mocked dependencies where meaningful; do not claim real infrastructure behaviour has been verified.

- [ ] Organise intake application logic separately from its HTTP adapter; retain the request path, request and response shapes, credential checks, generated identifiers, and timestamp behaviour.
- [ ] Introduce a focused publishing port with a RabbitMQ adapter, reusing it where suitable for later pipeline stages; application logic does not depend on the RabbitMQ client.
- [ ] Publish the existing inbound event to the existing destination with unchanged routing and payload.
- [ ] Management endpoints remain owned by metadata-management when intake alone or combined profiles are selected.
- [ ] Preserve worker and management callers of existing shared implementations until their migration tickets are complete; do not remove still-used entry points.
- [ ] Use focused web/application checks with mocked publishing and authentication dependencies to verify accepted and rejected submissions and published event contents; compilation remains green.
