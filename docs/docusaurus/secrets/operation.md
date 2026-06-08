# Operating an agent with secrets

## Introduction

The Cloud Agent offers a DID (Decentralized Identifier) management solution
which involves creating, storing and using key materials.
To generate a DID key material, the software relies on a seed, following the BIP32 / BIP39 standards.
The system operators have the option to either provide their own seed or
allow the software to generate one automatically. However, in a production environment,
it is crucial for the system operators to explicitly supply the seed to the agent.
This ensures full control over the DID key material and guarantees secure management of user identities.

The Cloud Agent uses the following environment variables for secret management.

| Name                     | Description                                                     | Default                 |
|--------------------------|-----------------------------------------------------------------|-------------------------|
| `SECRET_STORAGE_BACKEND` | The storage backend that will be used for the secret storage    | `vault`                 |
| `VAULT_ADDR`             | The address which the Cloud Agent can reach the Vault           | `http://localhost:8200` |
| `VAULT_TOKEN`            | The token for accessing HashiCorp Vault                         | -                       |
| `VAULT_APPROLE_ROLE_ID`  | The `role_id` for HashiCorp Vault authentication with AppRole   | -                       |
| `VAULT_APPROLE_SECRET_ID`| The `secret_id` for HashiCorp Vault authentication with AppRole | -                       |
| `VAULT_USE_SEMANTIC_PATH`| Enable full path convention for vault secret path               | true                    |
| `DEFAULT_WALLET_SEED`    | The seed used for DID key management for the default wallet     | -                       |

## Storage backend configuration

Secret storage supports various backends like the Vault service or Postgres database.
By default, the backend chosen for secret storage is Vault, which is suitable for production environments.
There are multiple supported backend implementations, each catering to specific use cases.

### HashiCorp Vault

When operating in a production environment, the agent has the option to utilize Vault
as a secure secret storage backend. This choice is deemed suitable for production because
all data is encrypted and it also offers additional security-related capabilities.
By default, the agent uses this backend but the option is configurable.
To utilize this backend, set the `SECRET_STORAGE_BACKEND` variable to `vault`.

__Authentication and Authorization__

The agent expects to read and write secrets to the path `/secret/*`,
so ensure the appropriate permissions are provisioned.

Example Vault policy

```
path "secret/*" {
    capabilities = ["create", "read", "update", "patch", "delete", "list"]
}
```

HashiCorp Vault provides multiple authentication methods.
One of the simplest methods is [token authentication](https://developer.hashicorp.com/vault/docs/auth/token).
To authenticate using the token, set the environment variable `VAULT_TOKEN`.
The agent prefers token authentication if provided with multiple authentication methods.

Another method is [AppRole authentication](https://developer.hashicorp.com/vault/docs/auth/approle) which is suitable for automatic workflows.
To use AppRole authentication, set the environment variable `VAULT_APPROLE_ROLE_ID` and `VAULT_APPROLE_SECRET_ID`.

__Storage Backend__

HashiCorp Vault supports multiple backends for storage, such as filesystem, Etcd, PostgreSQL, or Integrated Storage (Raft).
Each backend has different properties, which have implications for how secrets can be stored.
The agent logically stores secrets in the following hierarchies.

```
# Wallet seed
/secret/<wallet-id>/seed

# Peer DID keys
/secret/<wallet-id>/dids/peer/<peer-did>/keys/<key-id>

# Generic secrets
/secret/<wallet-id>/generic-secrets/<specific-path>
```

Each storage backend has certain limitations, such as size, number of sub-paths, or path length.
Some backends can support path lengths of up to 32,768 characters, while others only allow a few hundred characters.
In some cases, the storage backends might not support the above logical convention due to excessively long paths.

To address this issue, the agent supports path shortening.
This feature can be toggled by setting the environment variable `VAULT_USE_SEMANTIC_PATH=false`.
When it is disabled, the unbounded portion of the path will be replaced by a SHA-256 digest of the original relative path.
Additionally, the original path will be stored in the secret metadata.

### Postgres

Postgres is an alternative backend option for secret storage.
However, this option must be explicitly chosen and will replace Vault.
By opting for Postgres, there is no need for an additional service like Vault,
which simplifies the process of setting up a local development instance.
It utilizes the same database instance as the agent itself. To enable this option,
set the `SECRET_STORAGE_BACKEND` to `postgres`, and it will utilize the same database
configuration as the agent's database. It is important to note that while this option
facilitates an easier development experience, it does not provide a secure method of storing secrets.
The data is not encrypted, making it unsuitable for production use.
