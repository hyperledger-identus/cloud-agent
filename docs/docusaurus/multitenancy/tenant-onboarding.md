# Tenant Onboarding

In a multi-tenant setup, it's crucial to understand the various roles within the system.
There are two key roles in tenant management: administrators and tenants.
Administrators are in charge of managing wallets and tenants,
while tenants are users who engage in standard SSI interactions with the Cloud Agent.

## Roles

In tenant management, there are 2 roles:

1. [Administrator](/home/concepts/glossary#administrator)
2. [Tenant](/home/concepts/glossary#tenant)

## Prerequisites

1. The Cloud Agent up and running
2. The Cloud Agent is configured with the following environment variables:
    1. `ADMIN_TOKEN=my-admin-token`
    2. `API_KEY_ENABLED=true`
    3. `API_KEY_AUTO_PROVISIONING=false`
    4. `DEFAULT_WALLET_ENABLED=false`

## Overview

This is a guide on how to onboard a new tenant from scratch.
This tutorial will demonstrate the creation of a new entity representing the tenant,
the provisioning of a wallet, and enabling an authentication method for this tenant.
Subsequently, the tenant will gain the capability to engage in SSI activities within an
isolated wallet environment using the Cloud Agent.

## Endpoints

| Endpoint                          | Description                                | Role          |
|-----------------------------------|--------------------------------------------|---------------|
| `GET /wallets`                    | List the wallets on the Cloud Agent        | Administrator |
| `POST /wallets`                   | Create a new wallet on the Cloud Agent     | Administrator |
| `POST /iam/entities`              | Create a new entity on the Cloud Agent     | Administrator |
| `POST /iam/apikey-authentication` | Create a new authentication for the entity | Administrator |
| `GET /did-registrar/dids`         | List the DIDs inside the wallet            | Tenant        |

## Administrator interactions

### 1. Check the existing wallets

When running the Cloud Agent using the configurations above, the Agent should start with an empty state.
Listing wallets on it should return empty results.

```bash
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'accept: application/json' \
  -H 'x-admin-api-key: my-admin-token'
```

Response Example:

```json
{
  "self": "/wallets",
  "kind": "WalletPage",
  "pageOf": "/wallets",
  "contents": []
}
```

### 2. Create a new wallet

The wallet can be created using a `POST /wallets` endpoint.
This wallet is going to act as a container for the tenant's assets (DIDs, VCs, Connections, etc.).
The wallet seed may be provided during the wallet creation or omitted to let the Agent generate one randomly.

```bash
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'accept: application/json' \
  -H 'x-admin-api-key: my-admin-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "seed": "c9994785ce6d548134020f610b76102ca1075d3bb672a75ec8c9a27a7b8607e3b9b384e43b77bb08f8d5159651ae38b98573f7ecc79f2d7e1f1cc371ce60cf8a",
    "name": "my-wallet"
  }'
```

Response Example:

```json
{
  "id": "99734c87-5c9d-4697-b5fd-dea4e9590ba7",
  "name": "my-wallet",
  "createdAt": "2023-01-01T00:00:00Z",
  "updatedAt": "2023-01-01T00:00:00Z"
}
```

### 3. Create a new entity

A new entity can be created to represent a tenant.
To create a new entity, send a `POST` request to the `/iam/entities` endpoint with the following parameters:

```bash
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/iam/entities' \
  -H 'accept: application/json' \
  -H 'x-admin-api-key: my-admin-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "John",
    "walletId": "99734c87-5c9d-4697-b5fd-dea4e9590ba7"
  }'
```

Make sure to use the `walletId` from the previous step.

Response Example:

```json
{
  "kind": "Entity",
  "self": "/iam/entities/10000000-0000-0000-0000-000000000000",
  "id": "10000000-0000-0000-0000-000000000000",
  "name": "John",
  "walletId": "99734c87-5c9d-4697-b5fd-dea4e9590ba7",
  "createdAt": "2023-09-01T14:00:38.760045Z",
  "updatedAt": "2023-09-01T14:00:38.760047Z"
}
```

### 4. Register `apikey` authentication method

With the new tenant now equipped with both a wallet and an entity,
the final step involves setting up the entity's authentication method.
Once this step is completed, the administrator should provide the tenant with an `apikey`, granting them access to utilize the Agent.

```bash
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/iam/apikey-authentication' \
  -H 'accept: */*' \
  -H 'x-admin-api-key: my-admin-token' \
  -H 'Content-Type: application/json' \
  -d '{
    "entityId": "10000000-0000-0000-0000-000000000000",
    "apiKey": "my-tenant-token"
  }'
```

Make sure to use the `entityId` from the previous step.

HTTP code 201 returns in the case of the successful request execution.

## Tenant interactions

With the `apikey` provisioned by the administrator, the tenant is able to authenticate and use the Cloud Agent.

### 1. Perform a simple action to verify access to the Cloud Agent

To prove that the tenant can be authenticated as the created entity and use the wallet,
try listing the DIDs in the wallet using `apikey` header.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/did-registrar/dids' \
  --header "apikey: my-tenant-token" \
  --header 'Accept: application/json'
```

The result should show 200 status with an empty list.
This means that the wallet has been created and it does not contain any DIDs.
Any interactions that the tenant performs should be scoped to only this wallet.
