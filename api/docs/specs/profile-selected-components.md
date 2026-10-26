# Independently deployable components selected by Spring profiles

Status: Draft. Functional scope synthesised from the agreed design and ADR 0001. Validation is constrained to checks that require no external infrastructure. Issue tracker publication and the `ready-for-agent` label are pending project tracker setup.

## Problem Statement

Operators currently select between producer and consumer roles, and the consumer role starts all three queue-processing stages together. They cannot independently place or scale preparation, smart-contract execution, and post-execution processing using the same application artifact. Queue listeners also mix transport handling, application decisions, persistence, and external calls, making the responsibilities difficult to organise independently.

## Solution

Keep one Maven module and one executable JAR. Allow operators to select and combine five Spring profiles: `intake`, `preparation`, `execution`, `post-execution`, and `metadata-management`. Each deployment starts only the entry points belonging to its selected roles, along with their required dependencies.

Organise code by responsibility and use ports and adapters to separate application logic from HTTP, RabbitMQ, database operations, blockchain access, and webhook delivery. Preserve business behaviour and existing external contracts except for the explicitly agreed removal of listener-control endpoints.

## User Stories

1. As an operator, I want to build one executable JAR, so that all deployment roles use the same release artifact.
2. As an operator, I want to select roles at runtime through Spring profiles, so that deployments do not require different Maven builds.
3. As an operator, I want to run intake independently, so that accepting execution requests can be placed separately from queue processing.
4. As an operator, I want to run preparation independently, so that validation and execution preparation can scale separately.
5. As an operator, I want to run contract execution independently, so that blockchain invocation has its own deployment resources.
6. As an operator, I want to run post-execution independently, so that notification delivery can scale separately.
7. As an operator, I want to run metadata management independently, so that management endpoints have an explicit deployment owner.
8. As an operator, I want to combine profiles in one process, so that smaller installations can run multiple roles together.
9. As an operator, I want to place processes on one machine or different machines, so that code organisation does not constrain placement.
10. As an operator, I want only selected queue listeners to start, so that a worker does not consume another stage's messages.
11. As an operator, I want workers configured through YAML and environment variables, so that deployment settings remain external to the artifact.
12. As an operator, I want concurrency, maximum concurrency, and prefetch configurable for each worker deployment, so that stages can be tuned independently.
13. As an operator, I want worker listeners to start automatically, so that HTTP control endpoints are unnecessary.
14. As an operator, I want only metadata management to run migrations, so that schema changes have one designated role.
15. As an API client, I want to submit executions through intake using the existing request and response contract, so that client integration remains compatible.
16. As an API client, I want intake to retain credential validation, so that separating management endpoints does not remove authentication.
17. As an administrator, I want login, users, and API-key management exposed by metadata management, so that access administration remains available.
18. As an administrator, I want blockchain and smart-contract configuration exposed by metadata management, so that execution metadata retains its existing management capabilities.
19. As an API client, I want execution queries exposed by metadata management, so that execution results remain accessible.
20. As an integrator, I want existing queue names, routing, and message formats retained, so that deployment separation does not require producer changes.
21. As an operator, I want existing execution statuses, timestamps, results, and failure behaviour retained, so that this reorganisation does not change processing semantics.
22. As a maintainer, I want HTTP controllers and queue listeners separated from application logic, so that transport concerns remain local to adapters.
23. As a maintainer, I want database and external-system interactions behind focused ports, so that application logic does not directly use infrastructure clients.
24. As a maintainer, I want only necessary integration interfaces introduced, so that the reorganisation avoids speculative abstractions.
25. As a maintainer, I want deployment instructions to explain profiles, endpoint ownership, and migration startup order, so that operators can use the new organisation correctly.
26. As a maintainer, I want automated checks to run without containers or external services, so that this reorganisation adds no infrastructure setup burden.

## Implementation Decisions

- Retain one repository, one Maven module, one executable artifact, and a shared release lifecycle. Do not produce an artifact per role.
- Use exactly `intake`, `preparation`, `execution`, `post-execution`, and `metadata-management` as role profile names. Environment configuration remains separate from role selection.
- Replace the producer/consumer role selection with the new profiles and update deployment examples accordingly. Compatibility aliases are not a requirement of this specification.
- Map event intake to `intake`, execution preparation to `preparation`, smart-contract execution to `execution`, and post-execution handling to `post-execution`.
- Keep execution submission in intake. Place login, users, API-key management, blockchain configuration, smart-contract configuration, and execution queries in metadata management. Preserve existing paths, payloads, and security behaviour for retained endpoints.
- Scope entry points and their configuration to roles. Sharing supporting dependencies does not expose another role's endpoints or start another role's listeners.
- Support combined profiles without duplicate listeners, conflicting bean definitions, or duplicate endpoint mappings. Combining metadata management with other roles still enables migrations through metadata management alone.
- Workers start listeners automatically and do not require an application HTTP server for listener control. Remove the existing `/start`, `/stop`, and `/consumers` listener-control endpoints.
- Preserve YAML/environment configuration for listener concurrency, maximum concurrency, prefetch, acknowledgement, and rejection settings. Allow worker settings to be tuned independently, including when roles are combined.
- Scope the custom Liquibase bean explicitly to metadata management. Disabling auto-configuration alone is insufficient because the application currently declares its own migration bean. Deploy metadata management before workers when a schema migration is needed; this change does not add deployment orchestration or leader election.
- Organise packages around responsibilities with application logic distinct from adapters. HTTP controllers and RabbitMQ listeners are inbound adapters. Persistence, RabbitMQ publishing, blockchain access, and webhook delivery are outbound interactions.
- Add only the ports required to remove direct infrastructure dependencies from application logic. Reuse existing integration interfaces where suitable; avoid a generic abstraction framework or an interface for every class.
- Preserve existing domain rules and models. Establish no new bounded contexts merely because processing stages have separate deployment profiles.
- Retain the shared database, schema, queues, exchanges, routing keys, dead-letter topology, message payloads, and observable processing behaviour. Separating persistence access from application logic does not imply database ownership redesign.
- Keep reliability changes separate from this work, even where existing behaviour has known limitations.

## Testing Decisions

- Do not introduce Testcontainers, Docker-dependent tests, embedded infrastructure, or a requirement to configure PostgreSQL, RabbitMQ, blockchain nodes, or webhook servers for automated validation.
- Compile and package the single artifact to verify that moved code and dependency wiring compile. Packaging alone does not prove successful startup against live infrastructure.
- Where automated tests add meaningful coverage, use the highest application-level entry point practical with mocked or lightweight in-memory implementations of external dependencies. Exercise observable decisions and outputs; do not test private methods, directory structure, trivial forwarding, or getter/setter behaviour.
- Use narrowly scoped Spring contexts or web slices with external dependencies mocked to check individual and combined profile selection, endpoint ownership, credential validation, removal of listener-control endpoints, and registration of only the intended listeners. These checks must not open database or broker connections or start real consumers.
- Verify configuration binding for worker concurrency, maximum concurrency, and prefetch without contacting RabbitMQ, including independent settings when worker profiles are combined.
- Check migration configuration selection without executing Liquibase: the migration bean is selected only when metadata management is active and is not duplicated in combined-role configurations. Applying migrations against PostgreSQL is not an automated acceptance requirement.
- Application-level checks for preparation, execution, and post-execution may supply inputs directly and observe calls to mocked persistence, publishing, blockchain, and webhook ports. They do not claim to verify broker delivery, persistence mappings, transaction behaviour, or real external-system compatibility.
- Existing prior art consists only of a Spring Boot context-loading test, which may depend on external infrastructure through application auto-configuration. Do not rely on it as an infrastructure-free test; isolate its context if it is retained in the default automated suite. Do not add a full-context test that reintroduces service setup requirements.
- A live end-to-end smoke check using an already available development environment can be documented as optional. It must require no new infrastructure setup and is not a prerequisite for completing this reorganisation. Report live startup, queue delivery, database operations, and migrations as unverified unless actually exercised.

## Out of Scope

- Separate executable artifacts, separate Maven modules, or separate releases per role.
- A DDD rewrite, new domain models, new bounded contexts, or speculative interfaces.
- Database schema changes, database splitting, and per-stage databases.
- Queue topology changes, new message formats, or API contract redesign.
- Transactional outboxes, new idempotency mechanisms, blockchain reconciliation, and retry-policy redesign.
- Changes to execution validation, statuses, timestamps, failure semantics, or webhook payloads as incidental cleanup.
- New deployment orchestration, automatic migration coordination, or a remote worker-control application.
- Testcontainers configuration, new test infrastructure, and mandatory integration or end-to-end tests against live services.

## Further Notes

- ADR 0001 records the agreed architecture. The user's latest instruction explicitly permits the focused ports and adapters needed for external interactions, superseding the earlier restriction against all new abstractions.
- One artifact can be deployed repeatedly with different role profiles, or once with combined profiles. Independent deployment does not mean independent release compatibility.
- The current execution path can conflate message-publication failures with blockchain failures, and database updates plus message publication lack an atomic handoff. These are known follow-up concerns, not changes authorised by this reorganisation.
- No configured issue tracker or triage-label vocabulary was found for this task. Run `/setup-matt-pocock-skills` to establish the publication destination before publishing this specification with `ready-for-agent`.
