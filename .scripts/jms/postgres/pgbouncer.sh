#!/bin/bash
set -e

DIRECTORY=./.docker/jms/volumes/postgres/data
COMPOSE_DIR=./.docker/jms
ROOT_DIR=$(pwd)
POSTGRES_SERVICE=jms_postgres
POSTGRES_USER=postgres
POSTGRES_DB=jmsdb
POSTGRES_PORT=5432
PGBOUNCER_SERVICE=jms_pgbouncer

if [[ ! -d "$DIRECTORY" ]]; then
  mkdir -p "$DIRECTORY"
fi

up() {
  cd "$COMPOSE_DIR"

  docker compose -f network.yml -f postgres.yml up --build -d

  cd "$ROOT_DIR"
}

isReady() {
  until docker exec -t "$POSTGRES_SERVICE" pg_isready -U "$POSTGRES_USER" 2>&1; do
    sleep 1
  done
}

pgStatStatements() {
  COMMAND="CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"
  docker exec -t "$POSTGRES_SERVICE" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$COMMAND" 
}

postgresExporterUser() {
  COMMAND="
    DO \$\$
    BEGIN
      -- Create role if it does not exist
      IF NOT EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter'
      ) THEN
        CREATE USER postgres_exporter WITH PASSWORD 'i74frPI99HcS';
      END IF;

      -- Grant CONNECT on database if not already granted
      IF NOT has_database_privilege('postgres_exporter', 'jmsdb', 'CONNECT') THEN
        GRANT CONNECT ON DATABASE jmsdb TO postgres_exporter;
      END IF;

      -- Grant pg_monitor role if not already a member
      IF NOT EXISTS (
        SELECT 1
        FROM pg_auth_members m
        JOIN pg_roles r_role ON r_role.oid = m.roleid
        JOIN pg_roles r_member ON r_member.oid = m.member
        WHERE r_role.rolname = 'pg_monitor'
          AND r_member.rolname = 'postgres_exporter'
      ) THEN
        GRANT pg_monitor TO postgres_exporter;
      END IF;
    END
    \$\$;
  "

  docker exec -t "$POSTGRES_SERVICE" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$COMMAND"
}

fillUserList() {
  docker exec -i "$PGBOUNCER_SERVICE" sh <<EOF
    psql -v ON_ERROR_STOP=1 \
      -U "$POSTGRES_USER" \
      -h "$POSTGRES_SERVICE" \
      -p "$POSTGRES_PORT" \
      -d postgres \
      -Atc "SELECT '\"' || rolname || '\" \"' || rolpassword || '\"' FROM pg_authid WHERE rolname = '$POSTGRES_USER' AND rolpassword IS NOT NULL;" \
      > /etc/pgbouncer/userlist.txt
EOF

  docker exec -i "$PGBOUNCER_SERVICE" kill -HUP 1
}

echo "Starting the container"
up

echo "Waiting for PostgreSQL to be ready"
isReady
echo "PostgreSQL is ready. Executing SQL scripts"

echo "Creating the pg_stat_statements extension"
pgStatStatements

echo "Creating the postgres exporter user"
postgresExporterUser

echo "Generating PgBouncer userlist.txt from PostgreSQL"
fillUserList
echo "PgBouncer userlist.txt generated successfully."

echo "Database initialisation completed."
