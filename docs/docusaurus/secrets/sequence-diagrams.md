# Secret Storage Flows

**Status**: draft

**Date**: 2021-06-02

**NOTE**: this document is a draft and is not implemented yet. Statement in this document might be changed in the future.

## Introduction

The current document describes the sequence diagrams of the Identus Platform components: APISIX, Cloud Agent, Wallet, Vault, Tenant.
The diagrams are stated from the simplest scenarios to the more complex ones to enforce the security and privacy of the data.

## Sequence Diagrams

### Phase #1: Basic Flow for Secret Management

This diagram describes the basic flow for the secret management.

Goal : the Agent stores the secrets using the `root` token to access the Vault service

Context:

- Tenant uses the access token to access the REST API via APISIX.
- The Agent uses root Vault token to communicate with the Vault.
- Tenant represented by any REST API client, Web or Mobile application.

```mermaid
sequenceDiagram
    actor User
    User ->> Application: run
    Application->>+APISIX: Agent REST API request + API token
    loop
        APISIX->>APISIX: validate API token
    end
    APISIX->>+Agent: Agent REST API request
    activate Agent
    loop Manage Secrets
        Agent->>+Vault: Vault REST API request
        Vault->>+Agent: Vault REST API response
    end
    Agent->>+Agent: Execute Business Logic
    Agent->>+APISIX: Agent REST API response
    deactivate Agent
    APISIX->>+Application: Agent REST API response
    Application->>+ User: react
```

### Phase #2: Single Tenant Flow for Secret Management

This diagram describes the flow for the secret management for the single tenant.

Goal: AppRole authentication method is used to authenticate the Agent to the Vault service.

Context:

- The Agent is authenticated to the Vault using the AppRole authentication method.
- Tenant uses the access token to access the REST API via APISIX.
- Tenant represented by any REST API client, Web or Mobile application.

```mermaid
sequenceDiagram
    actor User
    User ->> Application: run
    Application->>+APISIX: Agent REST API request + API token
    loop
        APISIX->>APISIX: validate API token
    end
    APISIX->>+Agent: Agent REST API request
    activate Agent
    loop Authentication
        Agent->>+Vault: Get auth token
        Vault->>+Agent: Return auth token
    end
    loop Manage Secrets
        Agent->>+Vault: Vault REST API request
        Vault->>+Agent: Vault REST API response
    end
    Agent->>+Agent: Execute Business Logic
    Agent->>+APISIX: Agent REST API response
    deactivate Agent
    APISIX->>+Application: Agent REST API response
    Application->>+ User: react
```

### Phase #3: Single Tenant Flow for Secret Management with Wallet

This diagram describes the flow for the secret management for the single tenant with the Wallet.

Goal: Tenant uses JWT token to authenticate to the Identus Platform.

Context:

- The Agent is authenticated to the Vault using the AppRole authentication method.
- Tenant uses the access token to access the REST API via APISIX (probably this might be removed, we need to decide what to do with the `api-token`)
- Tenant represented by any REST API client, Web or Mobile application authenticated to the Identus Platform using JWT token.
- Tenant uses the Wallet to communicate with the Vault

```mermaid
sequenceDiagram
    participant User
    participant Application
    participant APISIX
    participant Agent
    participant Wallet
    participant Agent
    participant Wallet
    participant Vault
    actor User

    User->>+Application: run
    loop Authentication
        Application ->> APISIX: get jwt-token
        APISIX ->> Vault: get jwt-token
        Vault ->> APISIX: jwt-token
        APISIX ->> Application: jwt-token
    end
    Application->>+APISIX: REST API + api-token + jwt-token
    loop
        APISIX->>APISIX: validate api-token
    end
    APISIX->>+Agent: Agent REST API request + jwt-token
    Agent ->>+ Wallet: handle request
    loop Manage Secrets
        Wallet->>+Vault: Vault REST API request
        Vault->>+Wallet: Vault REST API response
        Wallet->>+Wallet: Execute Business Logic
    end
    Wallet ->>+ Agent: response
    Agent ->>+ APISIX: Agent REST API response
    APISIX ->>+ Application: Agent REST API response
    Application ->>+ User: react
```

### Phase #4: Multi Tenant Flow for Secret Management

This diagram describes the flow for the secret management for the multi tenant.

//TBD
