#!/bin/bash

FILE="./.docker/scems/envs/.env.rabbitmq"

if [ ! -f "$FILE" ]; then
    mkdir -p "$(dirname "$FILE")"
    touch "$FILE"
fi

RABBITMQ_DEFAULT_USER=admin
RABBITMQ_DEFAULT_PASS=$(date +%s%N | sha256sum | base64 | head -c 16)

{
    echo "RABBITMQ_DEFAULT_USER=$RABBITMQ_DEFAULT_USER"
    echo "RABBITMQ_DEFAULT_PASS=$RABBITMQ_DEFAULT_PASS"
} > "$FILE"
