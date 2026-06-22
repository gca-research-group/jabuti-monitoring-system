#!/bin/bash

FILE="./.docker/scems/envs/.env.postgres"

if [ ! -f "$FILE" ]; then
    mkdir -p "$(dirname "$FILE")"
    touch "$FILE"
fi

POSTGRES_USER=postgres
POSTGRES_PASSWORD=$(date +%s%N | sha256sum | base64 | head -c 16)
POSTGRES_DB=scemsdb
POSTGRES_HOST=scems_database
POSTGRES_PORT=5432

{
    echo "POSTGRES_USER=$POSTGRES_USER"
    echo "POSTGRES_PASSWORD=$POSTGRES_PASSWORD"
    echo "POSTGRES_DB=$POSTGRES_DB"
    echo "POSTGRES_HOST=$POSTGRES_HOST"
    echo "POSTGRES_PORT=$POSTGRES_PORT"
} > "$FILE"