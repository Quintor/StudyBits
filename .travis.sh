#!/bin/bash
set -e 
docker build -t quindy:latest quindy/
TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate --exit-code-from tests
