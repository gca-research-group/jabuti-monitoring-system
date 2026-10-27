# Deployment profiles

The execution workflow lives under `br.edu.unijui.gca.api.pipeline`, with `preparation`, `execution`, and `postexecution` stage packages. Pipeline also owns lifecycle contracts, execution persistence, and event publishing; stages access metadata through its public stores. Package ownership does not change the independently selectable Spring profiles below.

Build one executable JAR with `mvn clean package`. Select roles at runtime with `--spring.profiles.active`; Maven build profiles are not used.

Run commands from `api/` with JDK 26 and Maven. Copy the resulting `target/api-0.0.1-SNAPSHOT.jar` to every deployment; every role uses the same release. The `.jar.original` file is Maven's unrepackaged intermediate, not another deployment artifact. Select at least one role explicitly: the default configuration starts no application entry points.

| Spring profile | Entry points |
| --- | --- |
| `intake` | Execution submission: `POST /smart-contract-execution/execute` |
| `preparation` | Inbound events queue listener |
| `execution` | Smart-contract execution queue listener |
| `post-execution` | Outbound events queue listener and configured webhook delivery |
| `metadata-management` | Login/refresh, users, API keys, blockchain and contract configuration, execution CRUD/query endpoints, queue purge, and database migrations |

Run the same JAR on one machine or multiple machines. Use the `prod` profile for the existing production datasource, broker, logging, and other environment configuration. For example:

```sh
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,metadata-management
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,intake
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,preparation
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,execution
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,post-execution
```

Start metadata management before workers when migrations are needed. Only that role selects the custom Liquibase bean; `--spring.liquibase.enabled=false` explicitly suppresses migrations when necessary. Other roles never run migrations by default. Database schema, queue topology, and message formats are unchanged.

For a release requiring schema changes, start one metadata-management instance and wait for its migration and startup to succeed before starting intake and workers. Coordinate this order in the existing deployment process. Additional metadata-management replicas may use the explicit migration override after the schema is ready; this application adds no migration orchestration or leader election.

Combine roles by listing profiles together:

```sh
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,intake,preparation,execution,post-execution,metadata-management
```

Worker-only processes select non-web startup. Adding `intake` or `metadata-management` selects servlet startup regardless of role ordering. Separate HTTP processes on the same machine need distinct `SERVER_PORT` values (default 8080). Worker processes do not need an HTTP port. Environment or command-line overrides of Spring's web application type still take precedence.

With `prod`, all roles require `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`. Web roles additionally require `JWT_SECRET` and `JWT_EXPIRATION` to retain credential validation. Only metadata management requires `BACKEND_URL` for blockchain platform image URLs. Workers do not require JWT or backend URL settings.

| Setting | Roles and meaning |
| --- | --- |
| `DATABASE_URL` | All roles: JDBC PostgreSQL URL, for example `jdbc:postgresql://db-host:5432/jabuti` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | All roles: credentials for the existing shared database |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | All roles: shared broker host and AMQP port |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | All roles: existing broker credentials |
| `JWT_SECRET` | Intake and metadata management: same signing secret across instances; at least 32 UTF-8 bytes for the existing HS256 implementation |
| `JWT_EXPIRATION` | Intake and metadata management: access-token lifetime in milliseconds |
| `BACKEND_URL` | Metadata management: externally reachable base URL used for platform image links |
| `SERVER_PORT` | Web roles only: optional HTTP port, default 8080 |
| `RABBITMQ_CACHE_CHANNEL_SIZE` | All roles with `prod`: optional channel cache size, default 500 |

Execution also needs network access to the blockchain endpoints configured through metadata management. The existing Hyperledger Fabric parameters include `mspId`, `peerEndpoint`, `peerHostAlias`, `channelName`, and PEM contents in `caCrt`, `keyStore`, and `signCert`; these are stored configuration values rather than file paths. Post-execution needs network access to the configured webhook destinations. These are existing integration requirements, not additional environment variables.

### Placement examples

On one machine, run metadata management on port 8080 and intake on port 8081, using the same JAR and shared database/broker settings:

```sh
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,metadata-management --server.port=8080
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,intake --server.port=8081
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,preparation,execution,post-execution
```

Each command runs in its own process. To separate all stages, replace the last process with the three individual worker commands above. For smaller installations, use the all-role command in one process and one HTTP port. A mixed process such as `prod,intake,preparation` exposes intake and consumes only the inbound queue.

On different machines, copy the same JAR and assign the desired role profiles on each host. Set database and broker addresses reachable from every host; `localhost` refers to that host, not another deployment. Keep all instances on the shared schema and queue topology and compatible release versions. HTTP ports need only be distinct when processes share a host. Route execution submissions to intake and management/login/query requests to metadata management; issue credentials there before using intake. Replicas of a worker compete on that stage's existing queue.

## Worker tuning

| Role | Concurrency | Maximum concurrency | Prefetch |
| --- | --- | --- | --- |
| Preparation | `PREPARATION_CONCURRENCY` | `PREPARATION_MAX_CONCURRENCY` | `PREPARATION_PREFETCH` |
| Execution | `EXECUTION_CONCURRENCY` | `EXECUTION_MAX_CONCURRENCY` | `EXECUTION_PREFETCH` |
| Post-execution | `POST_EXECUTION_CONCURRENCY` | `POST_EXECUTION_MAX_CONCURRENCY` | `POST_EXECUTION_PREFETCH` |

Each setting falls back to its corresponding existing `RABBITMQ_LISTENER_CONCURRENCY`, `RABBITMQ_LISTENER_MAX_CONCURRENCY`, or `RABBITMQ_LISTENER_PREFETCH` environment variable, then 20. Set maximum concurrency to at least concurrency. Per-role values remain independent when several workers run together.

The equivalent YAML properties are `app.workers.<role>.concurrency`, `app.workers.<role>.max-concurrency`, and `app.workers.<role>.prefetch`. The shared `spring.rabbitmq.listener.simple` properties retain automatic startup, automatic acknowledgements, and no requeue of rejected messages; these remain externally configurable.

The existing [producer Compose definition](../../.docker/jms/producer.yml) selects `prod,intake,metadata-management`. The [consumer definition](../../.docker/jms/consumer.yml) selects `prod,preparation,execution,post-execution`. Both mount the same `./api.jar` at `/app/api.jar`; copy the packaged executable JAR to that path. Their filenames and service names are retained for operational continuity; `producer` and `consumer` are not Spring role aliases. Their existing `.env` file supplies deployment settings. Role-specific worker variables can be placed there directly. Start the producer definition and wait for migrations before the consumer definition when upgrading the schema; these files do not enforce cross-process readiness.

The listener-control endpoints `/rabbitmq/start`, `/rabbitmq/stop`, and `/rabbitmq/consumers/{quantity}` have been removed. `/rabbitmq/purge-all` remains available only with metadata management.

## Validation

Run `mvn test` for isolated endpoint, authentication, profile wiring, migration selection, and worker configuration checks. They use mocked database/broker boundaries, do not start real consumers or run migrations, and require no Docker, Testcontainers, or configured external services. `mvn clean package` also produces the single executable JAR.

Use a terminal without deployment-specific Spring overrides or production credentials for reproducible checks. Maven needs its dependencies available (initial dependency resolution may require network access); `mvn -o clean package` works once they are cached. No service-dependent tests need to be skipped.

| Evidence | Automated coverage and limits |
| --- | --- |
| Compilation and packaging | Compiles production/test sources and builds the executable JAR; does not demonstrate live startup |
| `WebApplicationProfileTests`, `EndpointProfileTests` | Individual web roles, mixed web/worker roles, all roles, security, endpoint ownership and removal of listener controls; servlet requests run in memory |
| `ApiApplicationTests` | Individual/combined worker contexts and application-to-adapter behavior with mocked repository, broker and external-call boundaries |
| `ListenerProfileTests` | Real listener registration for individual and combined roles, independent concurrency/max/prefetch binding; containers remain stopped |
| `MigrationProfileTests` | Only metadata management selects one migration bean, including all roles and profile order changes; execution is suppressed before initialization |
| Focused application/adapter tests | Existing pipeline decisions, outputs and failure handling with mocked external interactions |

Application code is grouped under `intake`, `preparation`, `execution`, `postexecution`, `identity`, and `metadata`. Application packages use ports for external interactions; `adapters/http` and `adapters/rabbitmq` own inbound transport, and persistence, blockchain and webhook adapters own outbound access. `pipeline` and `shared` retain contracts needed by multiple responsibilities. Existing entities, DTOs, mappers and schema remain shared; profiles select deployment entry points rather than new domain boundaries.

These checks do not verify live broker delivery, PostgreSQL mappings, schema migrations, or blockchain/webhook connectivity. An optional smoke check may use an already available development environment; no new infrastructure setup is required by this change.

In that existing environment only, optionally start metadata management, confirm startup/migration success, then start the selected intake/workers. Use existing credentials and configured contract data to submit through intake and query through metadata management. Observe the expected queue stages, execution result and configured webhook. This can exercise database operations and external delivery, so use development data. If that environment is unavailable, omit the smoke check: live startup, broker delivery, database operations, migrations, blockchain invocation and webhook delivery remain **unverified** by this work.
