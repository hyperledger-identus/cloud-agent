# Migration from `apikey` to `JWT` authentication

The Cloud Agent authentication supports multiple authentication methods simultaneously, which means the user can seamlessly use any available credentials including `apikey` or `JWT` to access the wallet.
The agent's [UMA](/home/concepts/glossary#uma) permission resource also exposes the self-service permission endpoint, allowing users to manage the permissions for their wallets.
It allows users to transition from `apikey` to `JWT` authentication without admin intervention.

## Roles

In the migration process from `apikey` to `JWT`, there is only one role:

1. [Tenant](/home/concepts/glossary#tenant)

## Prerequisites

1. Keycloak up and running
2. Keycloak is configured the same as in [Tenant Onboarding Self-Service](./tenant-onboarding-self-service.md)
3. The Cloud Agent is up and running
4. The Cloud Agent is configured the same as in [Tenant Onboarding Self-Service](./tenant-onboarding-self-service.md)
5. The user has access to the wallet using `apikey`. (See [Tenant Onboarding](./tenant-onboarding.md))
6. The user has an account registered on Keycloak

## Overview

This tutorial outlines the steps to transition from `apikey` to `JWT` authentication.
Initially, users have wallet access through the `apikey` method.
To migrate to `JWT` authentication, users can create a new UMA permission for their wallet and grant permission to their Keycloak account.

## Endpoints

### Agent endpoints

| Endpoint                                   | Description                            | Role   |
|--------------------------------------------|----------------------------------------|--------|
| `GET /wallets`                             | List the wallets on the Cloud Agent    | Tenant |
| `POST /wallets`                            | Create a new wallet on the Cloud Agent | Tenant |
| `POST /wallets/{walletId}/uma-permissions` | Create a uma-permission for a wallet   | Tenant |
| `GET /did-registrar/dids`                  | List the DIDs inside the wallet        | Tenant |

### Keycloak endpoints

| Endpoint                                             | Description           | Role   |
|------------------------------------------------------|-----------------------|--------|
| `POST /realms/{realm}/protocol/openid-connect/token` | Issue a new JWT token | Tenant |

## Tenant interactions

### 1. Check the existing wallets using `apikey`

This tutorial assumes the tenant can access the wallet using `apikey`.
Before granting more permission to the wallet, the `walletId` must be identified.
To find the wallet, list them using `apikey`.

```bash
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'accept: application/json' \
  -H "apikey: my-tenant-token"
```

Make sure to use the correct `apikey` from the pre-requisite.

Response Example:

```json
{
  "self": "/wallets",
  "kind": "WalletPage",
  "pageOf": "/wallets",
  "contents": [
    {
      "id": "99734c87-5c9d-4697-b5fd-dea4e9590ba7",
      "name": "my-wallet",
      "createdAt": "2023-01-01T00:00:00Z",
      "updatedAt": "2023-01-01T00:00:00Z"
    }
  ]
}
```

### 2. Get the access token on keycloak

Run this command to log in and get the access token

```bash
curl -X 'POST' \
  'http://localhost:9980/realms/my-realm/protocol/openid-connect/token' \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=alice" \
  -d "password=1234"
```

Make sure to use the correct username and password.
Special attention on the `client_id`; this should be the actual `client_id` of the frontend application that logs the user in.
For this tutorial, it is OK to use `admin-cli`

Example token response (some fields omitted for readability)

```json
{
    "access_token": "eyJhbGciOi...7ocDHofUDQ",
    "refresh_token": "eyJhbGciOi...otsEEi4eQA",
    ...
}
```

### 3. Extract the subject ID from JWT

When creating a UMA permission, it is important to provide the subject ID to grant permission.
To get the subject ID of the tenant, one can inspect the JWT payload `sub` claim.

Run this command to extract the `sub` claim of the token from previous step.

```bash
echo 'eyJhbGciOi...7ocDHofUDQ' | cut --delimiter='.' --fields=2 | base64 --decode | jq -r '.sub'
```

Example result

```log
4a5c6ac9-12f5-4d1e-b8f2-667525c083fd
```

### 4. Grant the user permission to the wallet

UMA permission can be added to the current wallet, giving Keycloak users access.
To do this, invoke the `POST /wallets/{walletId}/uma-permissions` endpoint on the agent.

```bash
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/wallets/99734c87-5c9d-4697-b5fd-dea4e9590ba7/uma-permissions' \
  -v \
  -H 'accept: */*' \
  -H "apikey: my-tenant-token" \
  -H 'Content-Type: application/json' \
  -d '{
    "subject": "205e04b7-0158-41b0-89c3-f91c3a09f89b"
  }'
```

Make sure to use the correct `subject` for the user and the correct `walletId` from the step earlier.

The response should return the status `200 OK` in case of successful permission creation.

### 5. Perform a simple action to verify access to the Cloud Agent

After successful UMA permission creation, the user should be able to use the `JWT` token to authenticate the wallet.
List the wallet using a new `Authorization` header. The listed wallets should contain the wallet with the same ID in step 1.

```bash
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'accept: application/json' \
  -H 'Authorization: Bearer eyJhbGciOi...7ocDHofUDQ'
```

Make sure to use the correct `JWT` from step 2.

Response Example:

```json
{
  "self": "/wallets",
  "kind": "WalletPage",
  "pageOf": "/wallets",
  "contents": [
    {
      "id": "99734c87-5c9d-4697-b5fd-dea4e9590ba7",
      "name": "my-wallet",
      "createdAt": "2023-01-01T00:00:00Z",
      "updatedAt": "2023-01-01T00:00:00Z"
    }
  ]
}
```

This response indicates that the user should be able to perform any wallet interaction with the `JWT` and `apikey` interchangeably.
