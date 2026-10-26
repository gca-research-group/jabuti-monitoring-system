# Profile-selected components in a single application

Keep one Maven module and one executable JAR, with independently selectable Spring profiles `intake`, `preparation`, `execution`, `post-execution`, and `metadata-management`. Profiles can be combined in one process or enabled in separate deployments, avoiding separate build artifacts while allowing independent placement of pipeline stages.

Organise packages by responsibility and use ports and adapters to isolate application logic from HTTP, RabbitMQ, database access, blockchain access, and webhook delivery. Introduce only interfaces needed for these interactions; preserve existing business behaviour, database schema, queue topology, and message formats.

`intake` exposes execution submission and retains the dependencies needed to authenticate requests. `metadata-management` exposes login, users, API keys, blockchain and smart-contract configuration, and execution queries, and owns database migrations. Start it before workers when migrations are required.

The three worker profiles select preparation, contract execution, and post-execution listeners respectively. Carry forward the YAML and environment-variable configuration approach from `application-consumer.yml`, with settings configurable for each worker. Remove the existing local listener-control endpoints `/start`, `/stop`, and `/consumers`; workers start listeners automatically and require no HTTP control endpoints.

Reliability redesigns, new domain models, separate Maven modules, and separate executable artifacts are outside this change. Implementation follows the final shared-understanding confirmation required by the design interview.
