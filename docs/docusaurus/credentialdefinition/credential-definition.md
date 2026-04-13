# Anoncred Credential Definition Guide

## Abstract

This document details the structure, supported formats, and technical intricacies of Anoncred Credential Definitions within the Identus Platform.

## 1. Introduction

An Anoncred Credential Definition serves as a standardized format for any given Anoncred Verifiable Credential. By embedding essential attributes unique to each type of credential, it lays the groundwork for diverse categories of verifiable credentials. Integrating this definition on a public blockchain ensures its availability and verifiability for all stakeholders.

The Identus Platform endorses the Anoncred Credential Definition, conforming to the [Hyperledger AnonCreds specification](https://hyperledger.github.io/anoncreds-spec/#term:schemas).

## 2. Anoncred Credential Definition Attributes

### name (String)

A descriptive and readable name indicating the type or category of the credential.

**Example:**

```json
{
"name": "{{CREDENTIAL_NAME}}"
}
```

---

### description (String)

A succinct descriptor providing an overview of the credential definition's purpose or category.

**Example:**

```json
{
"description": "{{CREDENTIAL_DESCRIPTION}}"
}
```

---

### version (String)

Specifies the version of the credential definition, using the [SemVer](https://semver.org/) protocol.

**Example:**

```json
{
"version": "{{VERSION_NUMBER}}"
}
```

---

### tag (String)

A unique identifier or tag associated with the credential definition.

**Example:**

```json
{
"tag": "{{TAG_IDENTIFIER}}"
}
```

---

### author (DID)

The decentralized identifier (DID) of the entity that created the credential definition.

**Example:**

```json
{
"author": "{{ISSUER_DID_SHORT}}"
}
```

---

### schemaId (URI)

A distinct reference to retrieve the schema from the Schema Registry.

**Example:**

```json
{
"schemaId": "{{SCHEMA_REGISTRY_URI}}"
}
```

---

### signatureType (String)

Indicates the type of signature applied to the credential definition.

**Example:**

```json
{
"signatureType": "{{SIGNATURE_TYPE}}"
}
```

---

### supportRevocation (Boolean)

Specifies if the credential definition incorporates revocation capabilities.

**Example:**

```json
{
"supportRevocation": "{{BOOLEAN_VALUE}}"
}
```

---

## Conclusion

The Anoncred Credential Definition is a versatile tool that offers a standardized approach for an array of verifiable credentials. By ensuring its correct incorporation within the Identus Platform, the issuance and validation processes of various credentials can be streamlined and made more efficient.

## References

- [Hyperledger AnonCreds specification](https://hyperledger.github.io/anoncreds-spec/#term:schemas)

**Note:** Throughout the implementation phase within the Identus Platform, it's crucial to replace placeholders (such as `{{CREDENTIAL_NAME}}`, `{{VERSION_NUMBER}}`, and others) with their real, intended values.
