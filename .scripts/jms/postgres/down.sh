#!/bin/bash
VOLUME=jms_postgres_volume

docker volume rm $VOLUME 2>/dev/null

cd ./.docker/jms && docker compose -f network.yml -f postgres.yml down
