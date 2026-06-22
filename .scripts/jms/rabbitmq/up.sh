#!/bin/bash
DIRECTORY=./.docker/jms/volumes/rabbitmq

if [[ ! -d "$DIRECTORY" ]]; then
  mkdir -p "$DIRECTORY"
fi

cd ./.docker/jms && docker compose -f network.yml -f rabbitmq.yml up --build -d
