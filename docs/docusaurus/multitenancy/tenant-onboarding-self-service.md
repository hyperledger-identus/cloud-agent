# Tenant Onboarding Self-Service

In the [Tenant Onboarding with External IAM](./tenant-onboarding-ext-iam.md) tutorial,
we learned how [Keycloak](/home/concepts/glossary#keycloak-service) helps with user access and how it works together with the agent.
To set things up, the admin has to provision the required resources.
However, relying on the admin for onboarding operations can be restrictive for some use cases.
For example, some tenants might want to onboard on a self-service agent instance without admin intervention.

By leveraging Keycloak for a self-service agent instance,
users can self-register or link to other [Identity Providers (IDPs)](/home/concepts/glossary#idp) to register an account.
Once the account is registered, users can use it to set up their wallets.
This tutorial will investigate the steps to facilitate this scenario where [administrator](/home/concepts/glossary#administrator) intervention is unnecessary.

## Roles

In self-service tenant management with external IAM, there is only one role:

1. [Tenant](/home/concepts/glossary#tenant)

## Prerequisites

1. Keycloak is up and running.
2. Keycloak is configured as follows
   1. A realm called `my-realm` is created
   2. A client called `cloud-agent` under `my-realm` with __authorization__ feature is created. (See [create client instruction](https://www.keycloak.org/docs/latest/authorization_services/index.html#_resource_server_create_client))
   3. Make sure the `cloud-agent` client has __direct access grants__ enabled to simplify the login process for this tutorial.
   4. Make sure to [allow user self-registration](https://www.keycloak.org/docs/latest/server_admin/index.html#con-user-registration_server_administration_guide).
3. The Cloud Agent is up and running
4. The Cloud Agent is configured with the following environment variables:
   1. `ADMIN_TOKEN=my-admin-token`
   2. `DEFAULT_WALLET_ENABLED=false`
   3. `KEYCLOAK_ENABLED=true`
   4. `KEYCLOAK_URL=http://localhost:9980` (replace with appropriate value)
   5. `KEYCLOAK_REALM=my-realm`
   6. `KEYCLOAK_CLIENT_ID=cloud-agent`
   7. `KEYCLOAK_CLIENT_SECRET=<KEYCLOAK_CLIENT_SECRET>` (replace with appropriate value)
   8. `KEYCLOAK_UMA_AUTO_UPGRADE_RPT=true`

## Overview

This tutorial demonstrate the process of user self-registration on Keycloak.
Then, the users can log in to Keycloak to obtain a token.
When the agent uses this token for the wallet creation, the agent recognizes it belongs to a tenant and automatically associates the tenant's permission with the created wallet.

## Endpoints

### Agent endpoints

| Endpoint                  | Description                            | Role   |
|---------------------------|----------------------------------------|--------|
| `GET /wallets`            | List the wallets on the Cloud Agent    | Tenant |
| `POST /wallets`           | Create a new wallet on the Cloud Agent | Tenant |
| `GET /did-registrar/dids` | List the DIDs inside the wallet        | Tenant |

### Keycloak endpoints

| Endpoint                                             | Description           | Role   |
|------------------------------------------------------|-----------------------|--------|
| `POST /realms/{realm}/protocol/openid-connect/token` | Issue a new JWT token | Tenant |

## Tenant interactions

### 1. Self-register account on Keycloak

Start by registering a new account on Keycloak, accessible through its login page.
Usually, this should be available at `http://localhost:9980/realms/my-realm/account/`.

For detailed instructions on how to register a new user,
please refer to [registering a new user](https://www.keycloak.org/docs/latest/server_admin/index.html#proc-registering-new-user_server_administration_guide) section on the official documentation.

### 2. Obtain access token from Keycloak

Once a new account is registered, Keycloak should recognize it, allowing the user to log in and obtain the access token.

Run this command to log in and get the access token.

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
For this tutorial, it is OK to use `admin-cli`.

Example token response (some fields omitted for readability)

```json
{
    "access_token": "eyJhbGciOi...7ocDHofUDQ",
    "refresh_token": "eyJhbGciOi...otsEEi4eQA",
    ...
}
```

### 3. Check the existing wallets

Right after the account is registered, no permission should be associated with it.
Listing wallets on it should return empty results.

```bash
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'Authorization: Bearer eyJhbGciOi...7ocDHofUDQ' \
  -H 'Accept: application/json'
```

Use the correct `access_token` in the previous command's Authorization header.

Response Example:

```json
{
  "self": "/wallets",
  "kind": "WalletPage",
  "pageOf": "/wallets",
  "contents": []
}
```

### 4. Create a new wallet

Create a wallet using a `POST /wallets` endpoint.
This wallet will be a container for the tenant's assets (DIDs, VCs, Connections, etc.).
The Agent can provide or randomly generate the wallet seed during wallet creation.

If the user already has the wallet associated, the wallet creation will fail as multiple wallets per tenant are not yet allowed.

```bash
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/wallets' \
  -H 'Authorization: Bearer eyJhbGciOi...7ocDHofUDQ' \
  -H 'Accept: application/json' \
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

In this step, the agent creates both wallet resource and UMA permission on Keycloak, assigning the new wallet to the user who created it.

### 5. Perform a simple action to verify access to the Cloud Agent

Without further operation, the wallet should be available for the tenant.
To prove that the tenant can access the wallet, list the DIDs using RPT in the `Authorization` header.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/did-registrar/dids' \
  -H 'Authorization: Bearer eyJhbGciOi...7ocDHofUDQ' \
  -H 'Accept: application/json'
```

The wallet was successfully created, but it currently does not contain any DIDs - indicated by an empty list and a 200 status.
The tenant should only perform interactions within the scope of this wallet.
