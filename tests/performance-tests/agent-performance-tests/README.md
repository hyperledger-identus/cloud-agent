# Atala PRISM V2 Performance Gatling Project

Welcome to performance measurements project for Atala PRISM V2.

The project is written on Kotlin and uses [Gatling](https://gatling.io/) load test framework.

## Project structure

The project is divided by the following main modules:

* `common`: general module for constants and utility functions
* `simulations`: Gatling simulations
* `steps`: simulation steps that can be combined into simulations later

## Running simulations

To run all simulations, use the following command:

```shell
./gradlew gatlingRun
```

To run a specific simulation, specify the full path to the required simulation file.
For example, running `ConnectionSimulation` from `simulations` package:

```shell
./gradlew gatlingRun-simulations.ConnectionSimulation
```

## Environments configuration

There are multiple configuration environment variables available through `common.Configuration` module:

* `ISSUER_AGENT_URL`: URL for Issuer Agent, example: `http://localhost:8080/cloud-agent`
* `ISSUER_AGENT_API_KEY`: access key for Issuer agent if hosted on remote env
* `HOLDER_AGENT_URL`: URL for Holder Agent, example: `http://localhost:8090/cloud-agent`
* `HOLDER_AGENT_API_KEY`: access key for Holder agent if hosted on remote env
