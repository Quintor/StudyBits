#!/bin/bash
set -e 
cd v0.3
TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate --exit-code-from tests
