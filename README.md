# StudyBits

StudyBits allows students to receive credentials from their universities, and issue zero-knowledge proofs to other universities.

StudyBits leverages the [Sovrin network](https://sovrin.org/) to store DIDs, Schemas and Credential Definitions.

This repository contains the University Agent, which is run by universities to distribute credentials to students. See the [architecture](https://github.com/Quintor/StudyBits/wiki/StudyBits-v0.3-architecture) for a bigger picture.
The agent is used through the [StudyBits Wallet](https://github.com/Quintor/StudyBitsWallet)


Contact us on [Gitter](https://gitter.im/StudyBits/Lobby)

## Running in docker

Use `TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate pool university-agent-rug university-agent-gent` 

## Running tests in docker

Running tests: `TEST_POOL_IP=127.0.0.1 docker-compose up --build --force-recreate --exit-code-from tests`

## Running outside of docker

To run backend locally, install libindy matching the version that is installed in the Dockerfile, [following their instructions](https://github.com/hyperledger/indy-sdk#installing-the-sdk)


