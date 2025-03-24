# Create DID

PRISM DIDs are a type of [decentralized identifier (DID)](/home/concepts/glossary#decentralized-identifier) used across the Atala PRISM product suite.

It is a variation of a [sidetree protocol](https://identity.foundation/sidetree/spec/) and uses the Cardano blockchain as the underlying ledger for [DID resolution](/home/concepts/glossary#did-resolution) and operation publication.
Please refer to the [PRISM method specification](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md) for a more detailed explanation of how it works.

PRISM DIDs can be created entirely offline without interacting with the blockchain by constructing a DID create-operation, a [protocol buffer (protobuf)](/home/concepts/glossary#protocol-buffer) message with a set of public keys and services.
Once the create-operation gets constructed, deriving a DID from this operation is possible, which is well-defined by the [PRISM DID method](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md).

## Roles

1. [DID Controller](/home/concepts/glossary#did-controller) is the organization or individual who has control of the DID.

## Prerequisites

1. DID Controller Cloud Agent up and running

## Overview

For this example, a PRISM DID gets created and stored inside the Cloud Agent along with the private keys. It is not automatically published.
The Agent will keep track of private keys used for the create-operation and the content of the operation itself.

The Cloud Agent provides two endpoint groups to facilitate PRISM DID usage.

- `/dids/*`
facilitate of low-level interactions between DID operations and the blockchain.
The DID controllers will handle key management independently and use these endpoints for blockchain interaction.

- `/did-registrar/*`
Facilitates a higher-level interaction with PRISM DID, where the Cloud Agent handles key management concerns.

## Endpoints

The example uses the following endpoints

| Endpoint                                                                               | Description                                             | Role           |
|----------------------------------------------------------------------------------------|---------------------------------------------------------|----------------|
| [`GET /did-registrar/dids`](/agent-api/#tag/DID-Registrar/operation/listManagedDid)    | List all DIDs stored in the Cloud Agent                 | DID Controller |
| [`POST /did-registrar/dids`](/agent-api/#tag/DID-Registrar/operation/createManagedDid) | Create a new PRISM DID to be managed by the Cloud Agent | DID Controller |
| [`GET /dids/{didRef}`](/agent-api/#tag/DID/operation/getDid)                           | Resolve a DID to DID document representation            | DID Controller |

## DID Controller interactions

### 1. Check existing DID on the Cloud Agent

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/did-registrar/dids' \
  --header "apikey: $API_KEY" \
  --header 'Accept: application/json'
```
The result should show an empty list, as no DIDs exist on this Cloud Agent instance.

### 2. Create the Cloud Agent managed DID using DID registrar endpoint

The DID controller can create a new DID by sending a [DID document](/home/concepts/glossary#did-document) template to the Agent.
Since key pairs are generated and managed by the Cloud Agent, DID controller only has to specify the key `id`,
`purpose` (`authentication`, `assertionMethod`, etc.), and optional `curve` (`secp256k1`, `Ed25519`, `X25519`).
If the `curve` is omitted, the agent uses the `secp256k1` curve by default.

```bash
curl --location --request POST 'http://localhost:8080/cloud-agent/did-registrar/dids' \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --header "apikey: $API_KEY" \
  --data-raw '{
    "documentTemplate": {
      "publicKeys": [
        {
          "id": "auth-1",
          "purpose": "authentication",
          "curve": "secp256k1"
        }
      ],
      "services": []
    }
  }'
```

The response should look like

```json
{
    "longFormDid": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ"
}
```

### 3. List the created DID

Check the `GET /did-registrar/dids` endpoint. The response should return a list containing one DID.

```json
{
    "contents": [
        {
            "did": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
            "longFormDid": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ",
            "status": "CREATED"
        }
    ],
    "kind": "ManagedDIDPage",
    "pageOf": "http://localhost:8080/cloud-agent/did-registrar/dids",
    "self": "http://localhost:8080/cloud-agent/did-registrar/dids"
}
```

### 4. Resolution of the created DID

To check that the DID document is correctly populated, test the created DID against the resolution endpoint.

Replacing the `{didRef}` with the long-form DID, and the response should return the DID document.
Replacing the `{didRef}` with the short-form DID, and the resolution should fail since the DID still needs to be published.

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
        "id": "did:prism:62675a438616773280f70e4f4d1047133fc56bb183758fcccd5d5714ea5b1959:Cr0BCroBEloKBWtleS0xEARCTwoJc2VjcDI1NmsxEiDRh7iIj8WKJ28nde1uc6ZnEBWIwEVMXlIEmrqCo-bE5Bogn6o2TzP0HzekLOhA-06MrIpOuaaHL_Rhy01wyjV4ypsSXAoHbWFzdGVyMBABQk8KCXNlY3AyNTZrMRIg0y28R1CS3F0-kwNcQShdRhtcvz-LQlI86z1DIYrKM7oaIPkmCAegj-sSaAy0zTxrR9F4TSXB-62vCQxIsEovkEcA",
        "verificationMethod": [
            {...}
        ],
    },
    "didDocumentMetadata": {...},
    "didResolutionMetadata": {...}
}
```
