# Deactivate DID

DID deactivation is an important feature that provides greater control for managing digital identity.
DID deactivation can be helpful if the [DID](/home/concepts/glossary#decentralized-identifier) is compromised or unused.
This feature is crucial for the security and risk management of identity owners.

Similar to [DID update](./update.md), deactivating a PRISM DID is a process of putting deactivate-operation on the blockchain so that other participants know that the DID is no longer active.
The PRISM DID method only allows published DID to be deactivated.

## Roles

1. DID Controller is the organization or individual who has control of the DID.

## Prerequisites

1. DID Controller Cloud Agent up and running
2. DID Controller has a DID created on the Cloud Agent (see [Create DID](./create.md))
3. DID Controller has a DID published to the blockchain (see [Publish DID](./publish.md))

## Overview

DID deactivation operates similarly to the DID update, where deactivate-operation publishes to the blockchain, and confirmation blocks must be created before participants consider it deactivated.
Once the DID is deactivated, all content in the [DID document](/home/concepts/glossary#did-document) gets deleted, and no operation will affect the DID afterward.
The same concept also holds for PRISM DID deactivation in that the fork can occur if any subsequent operation occurs before the operation is confirmed.
Please refer to the `SECURE_DEPTH` parameter in [PRISM method - protocol parameters](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#versioning-and-protocol-parameters) for the number of confirmation blocks.
At the time of writing, this number is 112 blocks.

DID deactivation is easily performed with the Cloud Agent.
Under the hood, the Cloud Agent uses the `MASTER` keys to sign the intended operation and automatically post the operation to the blockchain.
This example shows the DID deactivation and steps to observe the changes to the DID using the Cloud Agent.

## Endpoints

The example uses the following endpoints

| Endpoint                                                                                                          | Description                                  | Role           |
|-------------------------------------------------------------------------------------------------------------------|----------------------------------------------|----------------|
| [`POST /did-registrar/dids/{didRef}/deactivations`](/agent-api/#tag/DID-Registrar/operation/deactivateManagedDid) | Deactivate a PRISM DID                       | DID Controller |
| [`GET /dids/{didRef}`](/agent-api/#tag/DID/operation/getDid)                                                      | Resolve a DID to DID document representation | DID Controller |

## DID Controller interactions

### 1. Check the current state of the DID document

Given the DID Controller has a DID managed by the Cloud Agent and that DID is published, he can resolve the DID document using short-form DID.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/dids/{didRef}' \
--header "apikey: $API_KEY" \
--header 'Accept: */*'
```

Example DID document response (some fields omitted for readability)

```json
{
    "didDocument": {...},
    "didDocumentMetadata": {
        "canonicalId": "did:prism:66e431434f201c7ae43f6e63569f1ee556d7dfbee1646101547324013e545d2c",
        "deactivated": false,
        ...
    },
    "didResolutionMetadata": {...}
}
```
The DID metadata shows the `deactivation` status as `false`, meaning that this DID is still active.

### 2. Requesting the DID deactivation to PRSIM Agent

The active status comes from the last step.
The DID deactivation can be performed by calling `POST /did-registrar/dids/{didRef}/deactivations` and replacing `{didRef}` with the DID to deactivate.

```bash
curl --location --request POST 'http://localhost:8080/cloud-agent/did-registrar/dids/{didRef}/deactivations' \
--header "apikey: $API_KEY" \
--header 'Accept: application/json'
```

Under the hood, the Cloud Agent constructs the DID deactivate-operation from the last confirmed operation observed on the blockchain.
The DID Controller should receive a response about the scheduled operation, waiting for confirmation on the blockchain.
If this deactivate-operation gets confirmed on the blockchain and not discarded as a fork, the DID becomes deactivated.

### 3. Wait for the confirmation and observe the change in the DID metadata

When the DID Controller tries to resolve the DID again using the example in Step 1,
the content might remain the same because the operation still needs to be confirmed and applied.

The DID Controller keeps polling this endpoint until the `deactivated` status in DID document metadata changes to `true`.

Example response of deactivated DID document (some fields omitted for readability)

```json
{
    "didDocumentMetadata": {
        "canonicalId": "did:prism:66e431434f201c7ae43f6e63569f1ee556d7dfbee1646101547324013e545d2c",
        "deactivated": true,
        ...
    },
    "didResolutionMetadata": {...}
}
```

The DID metadata indicates that the DID is deactivated and the `didDocument` is empty.
