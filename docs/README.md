# Identus Cloud Agent Documentation

This is the landing page for the Cloud Agent technical documentation set.

## OpenAPI Specifications

### Source files
- The OpenAPI specification for a **service** building block is written in YAML format.
- Each building block is responsible for making its YAML spec available to other parties via HTTP GET on one of its service endpoints.

### Visualisation

[Swagger UI](https://swagger.io/tools/swagger-ui/) is used and allows development teams and service consumers to visualize and interact with the API’s resources
without having any of the implementation logic in place. It is automatically generated from the OpenAPI specification.

Swagger UI is available as a [Docker image](https://hub.docker.com/r/swaggerapi/swagger-ui).

## Run locally with Docker Compose

The following [docker-compose.yml](./docker-compose.yml) file can be used to run both services in one shot by executing the following command from the repo's root folder:

```bash
docker-compose -f docs/docker-compose.yml up
```
- OpenAPI specifications doc is available *localhost:8081*


## Docusaurus docs

These `docs/docusaurus` built as part of [Identus Docs](https://github.com/hyperledger/identus-docs) repo.
The over-arching configuration and setup needed to build the full docs are heald there (these docs are loaded in as a git submodule).


