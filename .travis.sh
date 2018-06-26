#!/bin/bash
set -e 
cd v0.3
docker-compose up --build --force-recreate --exit-code-from tests
