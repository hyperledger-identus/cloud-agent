# Update DID

PRISM DID method allows [DID Controller](/home/concepts/glossary#did-controller) to update the content of the [DID document](/home/concepts/glossary#did-document) by constructing a DID update-operation.
The update-operation describes the update action on the DID document.
For example, DID Controller can add a new key to the DID document by constructing an update-operation containing the `AddKeyAction`.
It is also possible for DID Controller to compose multiple actions in the same update-operation.
The [PRISM DID method - Update DID section](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#update-did) includes a complete list of supported update actions.
The PRISM DID method only allows published [DID](/home/concepts/glossary#decentralized-identifier) to be updated.

When updating a DID, each operation is connected through cryptography,
forming a sequence of operations known as the lineage.
It's important to note that the lineage should not have any forks,
meaning that the operations should follow a single, uninterrupted path.
Only the operations within the valid lineage are considered when updating the data on
the DID document, while any operations on a forked lineage are disregarded and discarded by the protocol.

Please refer to [PRISM DID method - processing of update operation](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#processing-of-updatedidoperations) for more detail about how a DID update-operation is processed.
It has an important implication on how the operation lineage is determined.

## Roles

1. DID Controller is the organization or individual who has control of the DID.

## Prerequisites

1. DID Controller Cloud Agent is up and running
2. DID Controller has a DID created by the Cloud Agent (see [Create DID](./create.md))
3. DID Controller has a DID published to the blockchain (see [Publish DID](./publish.md))

## Overview

The Cloud Agent allows the DID Controller to update the DID easily. This update mechanism is implementation specific and links the DID update-operation from the last confirmed operation observed on the blockchain.

When updating a DID, there is a waiting period for the update to be confirmed on the blockchain.
If the DID Controller updates the DID before the previous update is confirmed,
it can create a situation where the sequence of updates splits into two separate paths,
which is not allowed according to the protocol. This happens because the Cloud Agent connects
the update operation to the latest confirmed update. Once the pending update operation is confirmed,
any other pending operation that does not link to the latest confirmed operation will be discarded.
The subsequent updates continuing from that operation will also be discarded.
However, the Cloud Agent has a safeguard in place to prevent this issue by rejecting
multiple updates submitted on the same DID while previous updates are still being processed.

Please refer to the `SECURE_DEPTH` parameter in [PRISM method - protocol parameters](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#versioning-and-protocol-parameters) for the number of confirmation blocks.
At the time of writing, this number is 112 blocks.

This example shows the DID update capability on the Cloud Agent and the steps to verify that the update has been confirmed and applied.

## Endpoints

The example uses the following endpoints

| Endpoint                                                                                                | Description                                  | Role           |
|---------------------------------------------------------------------------------------------------------|----------------------------------------------|----------------|
| [`POST /did-registrar/dids/{didRef}/updates`](/agent-api/#tag/DID-Registrar/operation/updateManagedDid) | Update a PRISM DID                           | DID Controller |
| [`GET /dids/{didRef}`](/agent-api/#tag/DID/operation/getDid)                                            | Resolve a DID to DID document representation | DID Controller |

## DID Controller interactions

### 1. Check the current state of the DID document

Given the DID Controller has a DID on the Cloud Agent and that DID is published, he can resolve the DID document using short-form DID.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/dids/{didRef}' \
--header "apikey: $API_KEY" \
--header 'Accept: */*'
```

Example DID document response (some fields omitted for readability)

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
The DID Controller wishes to remove that key and add a new key called `key-2`

The DID Controller submits a DID update request to `POST /did-registrar/dids/{didRef}/updates`.

```bash
curl --location --request POST 'http://localhost:8080/cloud-agent/did-registrar/dids/did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86/updates' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--header "apikey: $API_KEY" \
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
                "purpose": "authentication",
                "curve": "secp256k1"
            }
        }
    ]
}'
```
Under the hood, the Cloud Agent constructs the DID update-operation from the last confirmed operation observed on the blockchain.
The DID Controller should receive a response about the scheduled operation, waiting for confirmation on the blockchain.


```json
{
    "scheduledOperation": {
        "didRef": "did:prism:4262377859267f308a06ec6acf211fbe4d6745aa9e637e04548771169616fb86",
        "id": "cb61cff083e27e2f8bc35b0e561780dc027c4f0072d2a2e478b2e0f26e3783b0"
    }
}
```

### 3. Wait for the confirmation and observe the change on the DID document

When the DID Controller tries to resolve the DID again using the example in Step 1,
the content might remain the same because the operation still needs to be confirmed and applied.

The DID Controller keeps polling this endpoint until the new key, `key-2`, gets observed.

Example response of updated DID document (some fields omitted for readability)

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

A new key, called `key-2`, gets observed, and `key-1`, now removed, has disappeared from the `verificationMethod`.

## Future work

The example only shows the case of a successful update.
In case of failure, the Cloud Agent will provide the capability to retrieve the low-level operation status and failure detail in the future.
