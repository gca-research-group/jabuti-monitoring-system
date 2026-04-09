#!/bin/bash
set -e

DIRECTORY=./.docker/scems/volumes/postgres/data
COMPOSE_DIR=./.docker/scems
ROOT_DIR=$(pwd)

if [[ ! -d "$DIRECTORY" ]]; then
  mkdir -p "$DIRECTORY"
fi

cd "$COMPOSE_DIR"

docker compose -f network.yml -f postgres.yml up --build -d

cd "$ROOT_DIR"
