# Deployment profiles

Build one executable JAR with `mvn clean package`. Select roles at runtime with `--spring.profiles.active`; Maven build profiles are not used.

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

Combine roles by listing profiles together:

```sh
java -jar target/api-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod,intake,preparation,execution,post-execution,metadata-management
```

Worker-only processes select non-web startup. Adding `intake` or `metadata-management` selects servlet startup regardless of role ordering. Separate HTTP processes on the same machine need distinct `SERVER_PORT` values (default 8080). Worker processes do not need an HTTP port. Environment or command-line overrides of Spring's web application type still take precedence.

With `prod`, all roles require `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD`. Web roles additionally require `JWT_SECRET` and `JWT_EXPIRATION` to retain credential validation. Only metadata management requires `BACKEND_URL` for blockchain platform image URLs. Workers do not require JWT or backend URL settings.

## Worker tuning

| Role | Concurrency | Maximum concurrency | Prefetch |
| --- | --- | --- | --- |
| Preparation | `PREPARATION_CONCURRENCY` | `PREPARATION_MAX_CONCURRENCY` | `PREPARATION_PREFETCH` |
| Execution | `EXECUTION_CONCURRENCY` | `EXECUTION_MAX_CONCURRENCY` | `EXECUTION_PREFETCH` |
| Post-execution | `POST_EXECUTION_CONCURRENCY` | `POST_EXECUTION_MAX_CONCURRENCY` | `POST_EXECUTION_PREFETCH` |

Each setting falls back to its corresponding existing `RABBITMQ_LISTENER_CONCURRENCY`, `RABBITMQ_LISTENER_MAX_CONCURRENCY`, or `RABBITMQ_LISTENER_PREFETCH` environment variable, then 20. Set maximum concurrency to at least concurrency. Per-role values remain independent when several workers run together.

The equivalent YAML properties are `app.workers.<role>.concurrency`, `app.workers.<role>.max-concurrency`, and `app.workers.<role>.prefetch`. The shared `spring.rabbitmq.listener.simple` properties retain automatic startup, automatic acknowledgements, and no requeue of rejected messages; these remain externally configurable.

The existing producer Compose definition now selects `prod,intake,metadata-management`. The consumer definition selects `prod,preparation,execution,post-execution`. Their filenames and service names are retained for operational continuity; the old Spring role names are removed.

The listener-control endpoints `/rabbitmq/start`, `/rabbitmq/stop`, and `/rabbitmq/consumers/{quantity}` have been removed. `/rabbitmq/purge-all` remains available only with metadata management.

## Validation

Run `mvn test` for isolated endpoint, authentication, profile wiring, migration selection, and worker configuration checks. They use mocked database/broker boundaries, do not start real consumers or run migrations, and require no Docker, Testcontainers, or configured external services. `mvn clean package` also produces the single executable JAR.

These checks do not verify live broker delivery, PostgreSQL mappings, schema migrations, or blockchain/webhook connectivity. An optional smoke check may use an already available development environment; no new infrastructure setup is required by this change.
