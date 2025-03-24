# Publish DID

PRISM DID creation involves generating key pairs and additional data (e.g., services) to construct a create-operation.
The create-operation allows [DID Controller](/home/concepts/glossary#did-controller) to derive two types of [DIDs](/home/concepts/glossary#decentralized-identifiers):

1. [Long-form DID](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#long-form-dids-unpublished-dids). It has the following format `did:prism:<initial-hash>:<encoded-state>`
2. Short-form DID. It has the following format `did:prism:<initial-hash>`

The difference is the long-form DID contains the encoded create-operation in the DID itself while, the short-form DID doesn't have this information.
As a result, when resolving the short-form DID, the resolver must look for additional data from the blockchain.
On the other hand, resolving long-form DID is self-contained.
Even if the data is not on the blockchain, the resolver can still work out the DID document of the long-form DID.

The resolution of short-form DID is achievable by DID publication, which is a process of putting the create-operation on the blockchain.


## Roles

1. DID Controller is the organization or individual who has control of the DID.

## Prerequisites

1. DID Controller Cloud Agent up and running
2. DID Controller has a DID created by the Cloud Agent (see [Create DID](./create.md))

## Overview

Publishing a DID requires the DID create-operation and the DID `MASTER` key pairs, which the Cloud Agent already created under the hood.
When the DID Controller requests a publication of their DID, the Cloud Agent uses the DID `MASTER` key to sign the operation and submit the signed operation to the blockchain.
After the operation submission to the blockchain, a specific number of confirmation blocks must get created before the DID operation is processed and published.
(see [PRISM DID method - Processing of DID operation](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#processing-of-operations))

## Endpoints

The example uses the following endpoints

| Endpoint                                                                                                      | Description                                                 | Role           |
|---------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|----------------|
| [`GET /did-registrar/dids/{didRef}`](/agent-api/#tag/DID-Registrar/operation/getManagedDid)                   | Get the DID stored in the Cloud Agent                       | DID Controller |
| [`POST /did-registrar/dids/{didRef}/publications`](/agent-api/#tag/DID-Registrar/operation/publishManagedDid) | Publish the DID stored in the Cloud Agent to the blockchain | DID Controller |
| [`GET /dids/{didRef}`](/agent-api/#tag/DID/operation/getDid)                                                  | Resolve a DID to DID document representation                | DID Controller |

## DID Controller interactions

### 1. Check the status of an unpublished DID

DID Controller checks the status by replacing the `{didRef}` with the unpublished DID on the Agent.
The `{didRef}` path segment can be either short-form or long-form DID.
When a DID gets created and not published, it has the status of `CREATED`.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/did-registrar/dids/{didRef}' \
--header "apikey: $API_KEY" \
--header 'Accept: application/json'
```

Example response

```json
{
    "did": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
    "longFormDid": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ",
    "status": "CREATED"
}
```
### 2. Request a publication of a DID

To publish a DID, use DID Controller `POST` a request to `/did-registrar/dids/{didRef}/publications` endpoint.

```bash
curl --location --request POST 'http://localhost:8080/cloud-agent/did-registrar/dids/{didRef}/publications' \
--header "apikey: $API_KEY" \
--header 'Accept: application/json'
```

The Cloud Agent will retrieve a DID `MASTER` key and sign the operation before submitting it to the blockchain.
The process is asynchronous, and it takes time until the operation is confirmed.
The DID Controller receives a scheduled operation as a response.

```json
{
    "scheduledOperation": {
        "didRef": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
        "id": "0cf945eaf3be143e5d64eef992666f3c613ae986fdb34e71ef4a9d2f25a6704f"
    }
}
```

The response contains the `scheduledOperation` property, which describes a scheduled operation.
The submitted DID operations are batched together along with other operations to reduce the transaction cost when interacting with the blockchain.

The Cloud Agent will eventually expose an endpoint to check the status of a scheduled operation.
Checking the publishing status is possible by following Step 3.

### 3. Wait until the DID operation is confirmed

The DID Controller checks the DID status the same as in Step 1. The status of the DID has changed to `PUBLICATION_PENDING`.

Example response with status `PUBLICATION_PENDING`

```json
{
    "did": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
    "longFormDid": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ",
    "status": "PUBLICATION_PENDING"
}
```

If the operation is confirmed, the status becomes `PUBLISHED`. The `longFormDid` property is no longer mandatory for published DID.

Example response with status `PUBLISHED`

```json
{
    "did": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
    "status": "PUBLISHED"
}
```

> **Note:** The `status` here is the internal status of the DID on the Cloud Agent (`PUBLISHED`, `CREATED`, `PUBLICAION_PENDING`). It does not indicate the lifecycle of the DID observed on the blockchain (e.g., deactivated, etc.). The [DID resolution](/home/concepts/glossary#did-resolution) metadata is for that purpose.

### 4. Resolve a short-form DID

Only published DID can be resolved using short-form.
To confirm that the short-form DID is resolvable, test the DID against the resolution endpoint.

Replace `{didRef}` with the short-form DID; the response should return a DID document.

```bash
curl --location --request GET 'http://localhost:8080/cloud-agent/dids/{didRef}' \
--header "apikey: $API_KEY" \
--header 'Accept: */*'
```
