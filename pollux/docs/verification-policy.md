# Verification Policies [DRAFT]

## Abstract

This document describes the purpose, formats, and technical details of the Verification Policies implementation in the Atala Prism Platform.

## 1. Introduction

Verification Policies define the rules and constraints in the verification process between the Verifier and the Holder and applied to the Verifiable Credentials and Verifiable Presentation.

Different rules and constraints can be defined and used based on the concrete implementation of the Verifiable Credentials.

Verification Policies are the input for the [Presentation Definition](https://identity.foundation/presentation-exchange/spec/v2.0.0/#presentation-definition).

Examples of the rules the Verification Policies can articulate:

- what proofs does the Verifier require
- what issuers the Verifier trust
- what Verifiable Credential format the Verifier requires

## 2. Terminology

#### Verification Policy

The Verification Policy is a template that defines a set of constraints that the Verifier defines as verification rules.

#### Issuer, Holder, Verifier

These are well-known roles in the SSI domain.

#### Party

Party refers to any of the roles: Issuer, Holder, Verifier.

## 3. Verification Policy structure

#### id (UUID)

The unique identifier of the entity

---

#### name (String)

It is a human-readable name for the verification policy.

---

#### description (String)

It is a human-readable description of the verification policy.

---

#### createdAt (DateTime)

It's the date and time stamp of the verification policy creation.

---

#### updatedAt (DateTime)

It's the date and time stamp of the verification policy update.

---

#### constraints (VerificationPolicyConstraint[])

It's the defined set of constraints for the verification policy.

---

## 3. Verification Policy Constraints

### 1. Credential Schema And Trusted Issuers

It defines the credential schema and the list of trusted issuers and is
applied to the JSON and JSON-LD verifiable credentials.
Example:

```
{
  "schemaId": "https://atala.io/credential-schemas/did:prism:shortid?id=abcde&version=1.0",
  "trustedIssuers": [
    "did:prism:trustedissueridentifier"
  ]
}
```

Given constraint defines the URL of the credential schema of the verifiable credentials a Holder must provide and the list of trusted issuers that issued the verifiable credential.

## 4. Verification Policy Flows

#### The Verifier creates the Verification Policy

#### The Verifier requests the Verifiable Presentation from the Holder that includes the rules from the Verification Policy
