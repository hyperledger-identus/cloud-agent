# Update DID

PRISM DID method allows **DID Controller** to update the content of the DID document by constructing a DID update-operation.
The update-operation describes the update action on the DID document.
For example, **DID Controller** can add a new key to the DID document by constructing an update-operation containing the `AddKeyAction`.
It is also possible for **DID Controller** to compose multiple actions in the same update-operation.
The full list of supported update actions can be found in the [PRISM DID method - Update DID section](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#update-did).
The PRISM DID method only allows published DID to be updated.

Each DID update-operation is cryptographically linked creating a lineage of DID operations.
The lineage is not allowed to contain forks.
The data on the DID document is updated only from the operations on the valid lineage and the operations on the fork are discarded.

*Please refer to [PRISM DID method - processing of update operation](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#processing-of-updatedidoperations) for more detail about how a DID update-operation is processed.*
*It has an important implication on how the operation lineage is determined.*

## Roles

1. **DID Controller** is the organization or individual who has control of the DID.

## Prerequisites

1. **DID Controller** PRISM Agent up and running
2. **DID Controller** has a DID created on PRISM Agent (see [Create DID](./create.md))
3. **DID Controller** has a DID published to the blockchain (see [Publish DID](./publish.md))

## Overview

PRISM Agent allows the **DID Controller** to easily update the DID.
This update mechanism is implementation specific and it links the DID update-operation from the *last confirmed operation* observed on the blockchain.

Updating the DID will take some time until the update-operation gets confirmed on the blockchain.
By updating the DID on PRISM Agent without waiting for the *previous update-operation* to be confirmed, the **DID Controller** is creating a fork on the DID lineage and risking having the subsequent operation discarded.
Please refer to the `SECURE_DEPTH` parameter in [PRISM method - protocol parameters](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#versioning-and-protocol-parameters) for the number of confirmation blocks.
At the time of writing, this number is 112 blocks.

This example shows the DID update capability on PRISM Agent and the steps to verify that the update has been confirmed and applied.

## Endpoints

The example uses the following endpoints

| Endpoint                                                                                                | Description                                  | Role           |
|---------------------------------------------------------------------------------------------------------|----------------------------------------------|----------------|
| [`POST /did-registrar/dids/{didRef}/updates`](/agent-api/#tag/DID-Registrar/operation/updateManagedDid) | Update a PRISM DID                           | DID Controller |
| [`GET /dids/{didRef}`](/agent-api/#tag/DID/operation/getDid)                                            | Resolve a DID to DID document representation | DID Controller |

## DID Controller interactions

### 1. Check the current state of the DID document

Given the **DID Controller** has a DID on PRISM Agent and that DID is published, he can resolve the DID document using short-form DID.

```bash
curl --location --request GET 'http://localhost:8080/prism-agent/dids/{didRef}' \
--header 'Accept: */*'
```

Example DID document response (some fields are omitted for readability)

```json
{
    "@context": "https://w3id.org/did-resolution/v1",
    "didDocument": {
        "@context": ["https://www.w3.org/ns/did/v1"],
        ...
        "verificationMethod": [
            {
                "controller": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86",
                "id": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86#key-1",
                "publicKeyJwk": {
                    "crv": "secp256k1",
                    "kty": "EC",
                    "x": "biEpgXMrmKMghF8LsXbIR0VDyIO31mnPkbJBpGDYH1g",
                    "y": "0YLIMfxY0_3J8Db6W0I1wcHZG3vRCRndNEnVn4h2V7Y"
                },
                "type": "EcdsaSecp256k1VerificationKey2019"
            }
        ]
        ...
    },
    "didDocumentMetadata": {...},
    "didResolutionMetadata": {...}
}
```
The `verificationMethod` in the DID document only shows one public key called `key-1`.

### 2. Add a new key and remove the existing key

The current DID document contains a key called `key-1`.
The **DID Controller** wishes to remove that key and add a new key called `key-2`

The **DID Controller** submits a DID update request to `POST /did-registrar/dids/{didRef}/updates`.

```bash
curl --location --request POST 'http://localhost:8080/prism-agent/did-registrar/dids/did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86/updates' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data-raw '{
    "actions": [
        {
            "actionType": "REMOVE_KEY",
            "removeKey": {
                "id": "key-1"
            }
        },
        {
            "actionType": "ADD_KEY",
            "addKey": {
                "id": "key-2",
                "purpose": "authentication"
            }
        }
    ]
}'
```
Under the hood, PRISM Agent constructs the DID update-operation from the *last confirmed operation* observed on the blockchain at that time.
The **DID Controller** should receive a response about the operation that has been scheduled, waiting for confirmation on the blockchain.


```json
{
    "scheduledOperation": {
        "didRef": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86",
        "id": "cb61cff083e27e2f8bc35b0e561780dc027c4f0072d2a2e478b2e0f26e3783b0"
    }
}
```

### 3. Wait for the confirmation and observe the change on the DID document

When the **DID Controller** tries to resolve the DID again using the example in step 1,
the content might still be the same because the operation is not yet confirmed and applied.

The **DID Controller** keeps polling this endpoint until the new key `key-2` is observed.

Example response of updated DID document (some fields are omitted for readability)

```json
{
    "@context": "https://w3id.org/did-resolution/v1",
    "didDocument": {
        "@context": ["https://www.w3.org/ns/did/v1"],
        ...
        "verificationMethod": [
            {
                "controller": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86",
                "id": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86#key-2",
                "publicKeyJwk": {
                    "crv": "secp256k1",
                    "kty": "EC",
                    "x": "sg5X06yRDNaW2YcuMuOPwrDPp_vqOtKng0hMHxaME10",
                    "y": "uAKJanSsNoC_bcL4YS93qIqu_Qwdsr_80DzRTzI8RLU"
                },
                "type": "EcdsaSecp256k1VerificationKey2019"
            }
        ]
        ...
    },
    "didDocumentMetadata": {...},
    "didResolutionMetadata": {...}
}
```

A new key call `key-2` is observed and `key-1` which was removed has disappeared from the `verificationMethod`.

## Future work

The example only shows the case of a successful update.
In case of failure, PRISM Agent will be providing the capability to retrieve the low-level operation status and failure detail in the future.
