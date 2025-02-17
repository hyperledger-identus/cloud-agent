# Credential Schema Introduction

## Abstract

This document describes the purpose, supported formats, and technical details of the Credential Schema implementation in
the Identus Platform.

## 1. Introduction

[Credential Schema](/home/concepts/glossary#credential-schema) is a data template for [Verifiable Credentials](/home/concepts/glossary#verifiable-credential).
It contains [claims](/home/concepts/glossary#claims) (attributes) of the Verifiable Credentials, credential schema author, type, name, version, and proof
of authorship.
By putting schema definitions on a public blockchain, they are available for all verifiers to examine to determine the
semantic interoperability of the Credential.

The Identus Platform supports the following specifications of the credential schemas:

- [Verifiable Credentials JSON Schema 2022](https://www.w3.org/TR/vc-json-schema/)
- [AnonCreds Schema](https://hyperledger.github.io/anoncreds-spec/#term:schemas)

The signed credential schema allows doing following verifications:

- semantic verification of the verifiable credentials
- authorship verification

The author can use credential schema to issue the following types of verifiable credentials:

- JSON Verifiable Credential
- JSON-LD Verifiable Credential
- Anoncred Verifiable Credential
- all types above but encoded as JWT

Limitations and constraints of the Identus Platform:

- The Issuer does not sign the Credential Schema
- The Issuer does not publish the Credential Schema to the VDR (the Cardano blockchain)

## 2. Terminology

### Credential Schema

The Credential Schema is a template that defines a set of attributes the [Issuer](/home/concepts/glossary#issuer) uses to issue the Verifiable Credential.

### Schema Registry

The registry is where the Credential Schema is published and available for parties.

### Issuer, Holder, Verifier

These are well-known roles in the [SSI](/home/concepts/glossary#self-sovereign-identity) domain.

## 2. Credential Schema Attributes

### guid (UUID)

It is the globally unique identifier of the credential schema.
It is bound to the `author`, `version`, and `id` fields as it is composed of the bytes of the `longId` string.

### id (UUID)

The locally unique identifier of the schema.

### longId (String)

Resource identifier of the given credential schema composed from the author's DID reference, id, and version fields.
**Example:** `{author}/{id}?version={version}`

> **Note:** According to the [W3C specification](https://w3c-ccg.github.io/vc-json-schemas/#id), this field is locally unique and combines the Issuer `DID`, `uuid`, and `version`.

**For example:** `did:example:MDP8AsFhHzhwUvGNuYkX7T/06e126d1-fa44-4882-a243-1e326fbe21db?version=1.0`



---

### type (String)

It is a type of the supported JSON Schema of the credential schema.
It describes the JSON Schema of the Credential Schema described in this document.

**JWT Credential Schema Example:**

```json
{
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json"
}
```

**AnonCred Credential Schema Example:**

```json
{
  "type" : "AnoncredSchemaV1"
}
```

---

### name (String)

It is a human-readable name for the schema.
**Example:**

```json
{
  "name": [
    "DrivingLicence"
  ]
}
```

---

### description (String)

It is a human-readable description of the schema.

> **Note:** This field may get removed later as it's not a part of W3C specification but rather the internal field of the JSON schema.

---

### version (String)

It is a version of the schema that contains the revision of the credential schema in [SemVer](https://semver.org/)
format.
**Example:**

```json
{
  "version": "1.0.0"
}
```

The version field must be the schema evolution and describe the impact of the changes.
For the breaking changes, the `major` version must get increased.
In the current implementation, the Identus Platform doesn't validate whether the new version is backward compatible.
This logic may get implemented later, so the Issuer is responsible for correctly setting the credential schema's next
version.

---

### author (DID)

DID of the identity which authored the credential schema.
**Example:**

```json
{
  "author": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
}
```

---

### authored (DateTime)

[RFC3339](https://www.rfc-editor.org/rfc/rfc3339) date of when the credential schema creation. A piece of Metadata.
**Example:**

```json
{
  "authored": "2022-03-10T12:00:00Z"
}
```

---

### schema (JSON Schema)

A valid [JSON-SCHEMA](https://json-schema.org/) where the credential schema semantic gets defined.
JSON Schema must be composed according to the [Metaschema](https://json-schema.org/draft/2020-12/schema) schema.
**Example:**

```json
{
  "$id": "https://example.com/driving-license-1.0",
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "description": "Driving License",
  "type": "object",
  "properties": {
    "emailAddress": {
      "type": "string",
      "format": "email"
    },
    "givenName": {
      "type": "string"
    },
    "familyName": {
      "type": "string"
    },
    "dateOfIssuance": {
      "type": "string",
      "format": "date-time"
    },
    "drivingLicenseID": {
      "type": "string"
    },
    "drivingClass": {
      "type": "integer"
    }
  },
  "required": [
    "emailAddress",
    "familyName",
    "dateOfIssuance",
    "drivingLicenseID",
    "drivingClass"
  ],
  "additionalProperties": true
}
```

---

### schema (AnonCred Schema)

A valid [ANONCRED-SCHEMA](https://hyperledger.github.io/anoncreds-spec/#term:schemas) where the credential schema semantic gets defined.
**Example:**

```json
{
    "name":"anoncred-birthday-cert",
    "version":"1.0.0",
    "description":"Birthday certificate",
    "type":"AnoncredSchemaV1",
    "author":"did:prism:e0266ee8d80a00163e5f922dc2567ab9611724a00db92423301154282169dff9",
    "tags":[
       "birth",
       "certificate"
    ],
    "schema":{
       "$schema":"https://json-schema.org/draft/2020-12/schema",
       "type":"object",
       "properties":{
          "name":{
             "type":"string",
             "minLength":1
          },
          "version":{
             "type":"string",
             "minLength":1
          },
          "attrNames":{
             "type":"array",
             "items":{
                "type":"string",
                "minLength":1
             },
             "minItems":1,
             "maxItems":125,
             "uniqueItems":true
          },
          "issuerId":{
             "type":"string",
             "minLength":1
          }
       },
       "name":"Birth Certificate Schema",
       "version":"1.0",
       "attrNames":[
          "location",
          "birthday"
       ],
       "issuerId":"did:prism:e0266ee8d80a00163e5f922dc2567ab9611724a00db92423301154282169dff9"
    },
    "required":[
       "name",
       "version"
    ],
    "additionalProperties":true
 }
```

---

### tags (String[])

It is a set of tokens that allow one to look up and filter the credential schema records.
This field is not a part of the W3C specification. Its usage by the Identus Platform for filtering the records.
**Example:**

```json
{
  "tags": [
    "id",
    "driving"
  ]
}
```

### proof (object)

The proof field is a JOSE object containing the credential schema's signature, including the following fields:

- type
- created
- verificationMethod
- proofPurpose
- proofValue
- domain
- jws

**Example:**

```json
{
  "proof": {
    "type": "Ed25519Signature2018",
    "created": "2022-03-10T12:00:00Z",
    "verificationMethod": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff#key-1",
    "proofPurpose": "assertionMethod",
    "proofValue": "FiPfjknHikKmZ...",
    "jws": "eyJhbGciOiJFZERTQSIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il0sImt0eSI6Ik...",
    "domain": "prims.atala.com"
  }
}
```

---

## References

- [Verifiable Credentials JSON Schema 2022](https://www.w3.org/TR/vc-json-schema/)
- [Verifiable Credential Data Integrity 1.0](https://www.w3.org/TR/vc-data-integrity/)
