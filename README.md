# StudyBits

Contact us on [Gitter](https://gitter.im/StudyBits/Lobby)

## Running in docker

Use `TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate pool university-agent-rug university-agent-gent` 

## Running tests in docker

Running tests: `TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate --exit-code-from tests`

## Running outside of docker

To run backend locally, install libindy and run `mvn install -DskipTests` in the quindy directory. 


