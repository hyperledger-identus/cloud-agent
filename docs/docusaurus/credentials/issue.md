# Issue Credentials

In Atala PRISM, the [Issue Credentials Protocol](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#issue-credentials-protocol) allows you to create, retrieve, and manage issued [verifiable credentials (VCs)](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#verifiable-credentials) between a VC issuer and a VC holder.

## Roles

In the Issue Credentials Protocol, there are two roles:

1. The [Issuer](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#issuer) is responsible for creating a new credential offer, sending it to a Holder, and issuing the VC once the offer is accepted.
2. The [Holder](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#holder) is responsible for accepting a credential offer from an issuer and receiving the VC.

The Issuer and Holder interact with the PRISM Agent API to perform the operations defined in the protocol.


## Prerequisites

Before using the Issuing Credentials protocol, the following conditions must be present:

1. Issuer and Holder PRISM Agents up and running
2. A connection must be established between the Issuer and Holder PRISM Agents (see [Connections](../connections/connection.md))
3. The Issuer must have a published PRISM DID, and the [DID document](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#did-document) must have at least one `assertionMethod` key for issuing credentials (see [Create DID](../dids/create.md) and [Publish DID](../dids/publish.md))
4. The Holder must have a PRISM DID, and the DID document must have at least one `authentication` key for presenting the proof.

## Overview

The protocol described is a VC issuance process between two Atala PRISM Agents, the Issuer and the Holder.

The protocol consists of the following main parts:

1. The Issuer creates a new credential offer using the [`/issue-credentials/credential-offers`](/agent-api/#tag/Issue-Credentials-Protocol/operation/createCredentialOffer) endpoint, which includes information such as the schema identifier and claims.
2. The Holder can then retrieve the offer using the [`/issue-credentials/records`](/agent-api/#tag/Issue-Credentials-Protocol/operation/getCredentialRecords) endpoint and accept the offer using the [`/issue-credentials/records/{recordId}/accept-offer`](/agent-api/#tag/Issue-Credentials-Protocol/operation/acceptCredentialOffer) endpoint.
3. The Issuer then uses the [`/issue-credentials/records/{recordId}/issue-credential`](/agent-api/#tag/Issue-Credentials-Protocol/operation/issueCredential) endpoint to issue the credential, which gets sent to the Holder via [DIDComm](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#didcomm). The Holder receives the credential, and the protocol is complete.

The schema identifier defines the structure and the credential type issued,
while the claims provide specific information about the individual, such as their name or qualifications.

This protocol applies in various real-life scenarios, such as educational credentialing, employment verification, etc.
In these scenarios, the Issuer could be a school, an employer, etc., and the Holder could be a student or an employee.
The VCs issued during this protocol could represent a diploma, a certificate of employment, etc.

## Endpoints

| Endpoint                                                                                                                           | Description                                                                              | Role           |
|------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|----------------|
| [`/issue-credentials/credential-offers`](/agent-api/#tag/Issue-Credentials-Protocol/operation/createCredentialOffer)               | This endpoint allows you to create a new credential offer                                | Issuer         |
| [`/issue-credentials/records`](/agent-api/#tag/Issue-Credentials-Protocol/operation/getCredentialRecords)                          | This endpoint allows you to retrieve a collection of all the existing credential records | Issuer, Holder |
| [`/issue-credentials/records/{recordId}`](/agent-api/#tag/Issue-Credentials-Protocol/operation/getCredentialRecord)                | This endpoint allows you to retrieve a specific credential record by its `id`            | Issuer, Holder |
| [`/issue-credentials/records/{recordId}/accept-offer`](/agent-api/#tag/Issue-Credentials-Protocol/operation/acceptCredentialOffer) | This endpoint allows you to accept a credential offer                                    | Holder         |
| [`/issue-credentials/records/{recordId}/issue-credential`](/agent-api/#tag/Issue-Credentials-Protocol/operation/issueCredential)   | This endpoint allows you to issue a VC for a specific credential record.                 | Issuer         |


:::info
Please check the full [PRISM Agent API](/agent-api) specification for more detailed information.
:::

## Issuer interactions

This section describes the Issuer role's available interactions with the PRISM Agent.

### Creating a Credential Offer

To start the process, the issuer needs to create a credential offer.
To do this, make a `POST` request to the [`/issue-credentials/credential-offers`](/agent-api/#tag/Issue-Credentials-Protocol/operation/createCredentialOffer) endpoint with a JSON payload that includes the following information:

1. `schemaId`: This is an identifier for a schema, which defines the structure and format of the data in a verifiable credential. The schema identifier must be unique and typically a URL or a URN.
2. `claims`: are the data stored in a verifiable credential. Claims get expressed in a key-value format and must conform to the structure and format defined in the schema. Claims contain the data that the issuer attests to, such as name, address, date of birth, etc.

Once the request initiates, a new credential record for the issuer gets created with a unique ID. The state of this record is now `OfferPending`.

```shell
# Issuer POST request to create a new credential offer
curl -X 'POST' \
  'http://localhost:8080/prism-agent/issue-credentials/credential-offers' \
    -H 'accept: application/json' \
    -H 'Content-Type: application/json' \
    -H "apiKey: $API_KEY" \
    -d '{
          "schemaId": "schema:1234",
          "subjectId": "did:prism:subjectIdentifier",
          "claims": {
            "firstname": "Alice",
            "lastname": "Wonderland",
            "birthdate": "01/01/2000"
          }
     }'
```

### Sending the Offer to the Holder

The next step for the Issuer is to send the offer to the holder using DIDComm.
To do this, the Issuer agent will process the offer and send it to the holder agent.
This process is automatic. The state of the Issuer's record will change to `OfferSent`.

### Issuing the Credential

Once the holder has approved the offer and sent a request to the Issuer,
the Issuer will receive the request via DIDComm and update the record state to `RequestReceived.`

The Issuer can then use the [`/issue-credentials/records/{recordId}/issue-credential`](/agent-api/#tag/Issue-Credentials-Protocol/operation/issueCredential) endpoint to issue the credential to the holder.

```shell
# Issuer POST request to issue the credential
# make sure you have `issuer_record_id` extracted from created credential offer
# and the record achieved `RequestReceived` state
curl -X POST \
    "http://localhost:8080/prism-agent/issue-credentials/records/$issuer_record_id/issue-credential" \
    -H "Content-Type: application/json" \
    -H "apiKey: $API_KEY"
```

When this endpoint gets called, the state of the record will change to `CredentialPending,` and after processing, it will change to `CredentialGenerated.`

Finally, the Issuer agent will send the credential to the holder via DIDComm,
and the state of the record will change to `CredentialSent`.
At this point, the Issuer's interactions with the holder are complete.

```mermaid
---
title: Issuer flow
---
stateDiagram-v2
  [*] --> OfferPending: create credential offer (`/issue-credentials/credential-offers`)
  OfferPending --> OfferSent: send offer (auto via PRISM Agent DIDComm)
  OfferSent --> RequestReceived: receive request (auto via PRISM Agent DIDComm)
  RequestReceived --> CredentialPending: issue credential (`/issue-credentials/records/{recordId}/issue-credential`)
  CredentialPending --> CredentialGenerated: process issued credential (auto via PRISM Agent)
  CredentialGenerated --> CredentialSent: send credential (auto via PRISM Agent)
```

## Holder interactions

This section describes the Holder role's available interactions with the PRISM Agent.

### Receiving the VC Offer

The Holder will receive the offer from the Issuer via DIDComm,
and a new credential record with a unique ID gets created in the `OfferReceived` state.

This process is automatic for the PRISM Agent.

You could check if a new credential offer is available using [`/issue-credentials/records`](/#tag/Issue-Credentials-Protocol/operation/getCredentialRecords) request and check for any records available in `OfferReceived` state:
```shell
# Holder GET request to retrieve credential records
curl "http://localhost:8090/prism-agent/issue-credentials/records" \
    -H "Content-Type: application/json" \
    -H "apiKey: $API_KEY"
```


### Approving the VC Offer

To accept the offer, the Holder can make a `POST` request to the [`/issue-credentials/records/{recordId}/accept-offer`](/agent-api/#tag/Issue-Credentials-Protocol/operation/acceptCredentialOffer) endpoint with a JSON payload that includes the following information:

1. `holder_record_id`: The unique identifier of the issue credential record known by the holder PRISM Agent.
2. `subjectId`: This field represents the unique identifier for the subject of the verifiable credential. It is a short-form PRISM [DID](https://github.com/input-output-hk/atala-prism-docs/blob/main/documentation/docs/concepts/glossary.md#decentralized-identifier) string, such as `did:prism:subjectIdentifier`.

```shell
# Holder POST request to accept the credential offer
curl -X POST "http://localhost:8090/prism-agent/issue-credentials/records/$holder_record_id/accept-offer" \
    -H 'accept: application/json' \
    -H 'Content-Type: application/json' \
    -H "apiKey: $API_KEY" \
    -d '{
          "subjectId": "did:prism:subjectIdentifier"
     }'
```

This request will change the state of the record to `RequestPending`.

### Receiving the VC Credential

Once the Holder has approved the offer and sent a request to the Issuer, the Holder agent will process the request and send it to the Issuer agent.
The state of the Holder's record will change to `RequestSent`.

After the Issuer has issued the credential, the Holder will receive the credential via DIDComm, and the state of the Holder's record will change to `CredentialReceived`.
This process is automatic for the PRISM Agent.

The Holder can check the achieved credential using a GET request to [`/issue-credentials/records/{recordId}/`](/agent-api/#tag/Issue-Credentials-Protocol/operation/getCredentialRecord) endpoint.

```mermaid
---
title: Holder Flow
---
stateDiagram-v2
  [*] --> OfferReceived: receive offer (auto via PRISM Agent)
  OfferReceived --> RequestPending: accept offer (`/issue-credentials/records/{recordId}/accept-offer`)
  RequestPending --> RequestSent: send request (auto via PRISM Agent)
  RequestSent --> CredentialReceived: receive credential (auto via PRISM Agent)
```

## Sequence diagram

The following diagram shows the end-to-end flow for an issuer to issue a VC to a holder.

![](issue-flow.png)