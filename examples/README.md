# How to Run Examples

## Prerequisites

- `docker-compose` version >= `2.23.1`

## Running Examples

Most examples follow the same pattern.
Navigate to the desired example directory and start its Docker Compose setup:

```bash
cd <EXAMPLE_DIR>
docker-compose up
```

If an example requires a different command, refer to its local README for instructions.

When finished, you can clean up Docker volumes to avoid unexpected behavior in future runs:

```bash
docker-compose down --volumes
```

## Examples

| Example             | Description                                                               |
| ------------------- | ------------------------------------------------------------------------- |
| `st`                | Single-tenant configuration without external services (except database)   |
| `st-multi`          | Three instances of single-tenant configuration                            |
| `st-vault`          | Single-tenant with Vault for secret storage                               |
| `st-oid4vci`        | Single-tenant agent with Keycloak as external Issuer Authorization Server |
| `mt`                | Multi-tenant configuration using built-in IAM                             |
| `mt-keycloak`       | Multi-tenant configuration using Keycloak for IAM                         |
| `mt-keycloak-vault` | Multi-tenant configuration using Keycloak and Vault                       |

## Testing Examples

Some example directories may contain a subdirectory called `hurl`.
Hurl is a CLI tool for testing HTTP requests. You can install it by following [this documentation](https://hurl.dev/docs/installation.html).

If the example contains a `hurl` subdirectory, you can test HTTP calls with:

```bash
cd ./hurl
hurl --variables-file ./local *.hurl --verbose
```

# Contributing

All Docker Compose files in the examples are generated using [Nickel](https://nickel-lang.org/).
They are defined in the shared `.nickel` directory and generated using the `build.sh` script.

## Prerequisites

- [Nickel](https://nickel-lang.org/) version >= `1.5` installed

## Generate Example Compose Files

To generate Docker Compose configs for all examples, run:

```bash
cd .nickel
./build.sh
```

## Updating Example Compose Files

To update the configuration, edit the relevant `*.ncl` file in the `.nickel` directory and regenerate the Docker Compose files.

## Adding New Examples

To add a new example with a Docker Compose file:

1. Create a new configuration key in `root.ncl`.
2. Add a new entry in the `build.sh` script.
3. Create the target example directory if it does not already exist.

## Example with Bootstrapping Script

If an example requires initialization steps, include them in the Docker Compose `depends_on` construct.
Ideally, infrastructure bootstrapping (e.g., database, IAM) should be automatic, but application bootstrapping (e.g., tenant onboarding) may require manual steps.
