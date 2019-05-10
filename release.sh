#!/usr/bin/env bash

read -p "Release version? (N/y) $1" -n 1 -r
echo    # (optional) move to a new line
if [[ $REPLY =~ ^[Yy]$ ]]
then
    cd university-agent
    mvn versions:set -DnewVersion=$1
    cd ..

    TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate --exit-code-from tests

    docker tag studybits/university-agent "studybits/university-agent:$1"
    docker push "studybits/university-agent:$1"

    git commit -am "Bump to version $1"
    git tag "v$1"
    git push origin "v$1"
fi
