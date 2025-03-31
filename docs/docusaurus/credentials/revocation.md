import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Credential revocation


Identus implements the revocation mechanism of JWT credentials according to [Verifiable Credentials Status List v2021](https://www.w3.org/TR/2023/WD-vc-status-list-20230427/). This open standard enables Identus to verify the revocation status of any credential that implements the revocation mechanism using the same specification.

## Overview

Every credential will contain the property `credentialStatus`, which will look like this:

```json
"credentialStatus": {
    "id": "http://localhost:8080/cloud-agent/cloud-agent/credential-status/27526236-3836-4061-9867-f69314e258b4#94567"
    "type": "StatusList2021Entry",
    "statusPurpose": "revocation",
    "statusListIndex": "94567",
    "statusListCredential": "http://localhost:8080/cloud-agent/cloud-agent/credential-status/27526236-3836-4061-9867-f69314e258b4"
  },
```
* `type` will always be `StatusList2021Entry`
* `statusListCredential` is a publicly accessible URL that resolves a status list credential that looks like this:

<Tabs groupId="vc-formats">
<TabItem value="jwt" label="JWT">

```json
{
  "proof" : {...},
  "@context" : [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3c.github.io/vc-bitstring-status-list/contexts/2021/v1.jsonld"
  ],
  "type" : [
    "VerifiableCredential",
    "StatusList2021Credential"
  ],
  "id" : "http://localhost:8085/credential-status/c2688c98-ecb5-4dd3-87e8-a689b8fa84dd",
  "issuer" : "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
  "issuanceDate" : 1717793619,
  "credentialSubject" : {
    "type" : "StatusList2021",
    "statusPurpose" : "Revocation",
    "encodedList" : "H4sIAAAAAAAA_-3BMQ0AAAACIGf_0MbwARoAAAAAAAAAAAAAAAAAAADgbbmHB0sAQAAA"
  }
}

```

</TabItem>

</Tabs>


* `statusListIndex` is an index in a bit string at which the credential's revocation status can be verified.

The status list credential contains  `encodedList`, a base64-encoded bit string that contains the credential's revocation status.

## Verification

To verify the revocation status of the credential, one must follow these steps:

1. Resolve the Status list credential using the URL found at path: `credentialStatus.statusListCredential`
2. Verify the embedded proof of the credential.
3. Decode bit-string, which is in the JSON document of the Status list credential, found at path - `credentialSubject.encodedList`
4. Use the status list index from `credentialStatus.statusListIndex` to check if the bit at this index in the decoded bit-string from step 3 is on or off. If the bit is on, the credential is revoked. Otherwise, a revocation has yet to occur.

## Proof verification

Status list credential integrity can be verified using the embedded proof.

<Tabs groupId="vc-formats">
<TabItem value="jwt" label="JWT">

We currently support 2 types of proofs:
* `DataIntegrityProof` via crypto suite `eddsa-jcs-2022`. The exact steps on how to create a verify this proof are in the [Data Integrity EdDSA Cryptosuites v1.0](https://www.w3.org/TR/vc-di-eddsa/#eddsa-jcs-2022)
  * Used for Ed25519 EC curves, meaning if the proof was created using Ed25519 private key
* `EcdsaSecp256k1Signature2019`, the description of this cryptosuite can be found [here](https://w3c-ccg.github.io/lds-ecdsa-secp256k1-2019/)
  * Used for secp256k1 EC curves, meaning if the proof was created using secp256k1 private key
  * This proof type uses RDF canonicalization algorithm as a data normalization before singing and verifying, the variant of this algorithm used is `URDNA2015`

#### Example of status list credential with `DataIntegrityProof`

```json
{
  "proof" : {
    "type" : "DataIntegrityProof",
    "proofPurpose" : "assertionMethod",
    "verificationMethod" : "data:application/json;base64,eyJAY29udGV4dCI6WyJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L211bHRpa2V5L3YxIl0sInR5cGUiOiJNdWx0aWtleSIsInB1YmxpY0tleU11bHRpYmFzZSI6InVNRll3RUFZSEtvWkl6ajBDQVFZRks0RUVBQW9EUWdBRUNYSUZsMlIxOGFtZUxELXlrU09HS1FvQ0JWYkZNNW91bGtjMnZJckp0UzRQWkJnMkxyNEQzUFdYR2xHTXB1aHdwSk84MEFpdzFXeVVHT1hONkJqSlFBPT0ifQ==",
    "created" : "2024-03-23T16:45:50.924279Z",
    "proofValue" : "ziKx1CJPKLy4U9kMmVzYct5xztq4oHRLPgMpAjh95zQxzBZorhLFmhZ85UPixJoQbaqkVaygLBnLARyxgGJGFNKFggaPSXHgJuG",
    "cryptoSuite" : "eddsa-jcs-2022"
  },
  "@context" : [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3c.github.io/vc-bitstring-status-list/contexts/2021/v1.jsonld"
  ],
  "type" : [
    "VerifiableCredential",
    "StatusList2021Credential"
  ],
  "id" : "http://localhost:8080/cloud-agent/credential-status/27526236-3836-4061-9867-f69314e258b4",
  "issuer" : "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
  "issuanceDate" : 1711212350,
  "credentialSubject" : {
    "id" : "",
    "type" : "StatusList2021",
    "statusPurpose" : "Revocation",
    "encodedList" : "H4sIAAAAAAAAAO3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
  }
}
```
#### Example of status list credential with `EcdsaSecp256k1Signature2019` proof

```json
{
  "proof" : {
    "type" : "EcdsaSecp256k1Signature2019",
    "proofPurpose" : "assertionMethod",
    "verificationMethod" : "data:application/json;base64,eyJAY29udGV4dCI6WyJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L3YxIl0sInR5cGUiOiJFY2RzYVNlY3AyNTZrMVZlcmlmaWNhdGlvbktleTIwMTkiLCJwdWJsaWNLZXlKd2siOnsiY3J2Ijoic2VjcDI1NmsxIiwia2V5X29wcyI6WyJ2ZXJpZnkiXSwia3R5IjoiRUMiLCJ4IjoiQ1hJRmwyUjE4YW1lTEQteWtTT0dLUW9DQlZiRk01b3Vsa2MydklySnRTND0iLCJ5IjoiRDJRWU5pNi1BOXoxbHhwUmpLYm9jS1NUdk5BSXNOVnNsQmpsemVnWXlVQT0ifX0=",
    "created" : "2024-06-07T20:53:40.177613Z",
    "jws" : "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFUzI1NksifQ..-JD-ggFe1JgWd7NxUqOvmkHwuO-B2U9V-Nqn2jIsXg1TN8G_Suy__ZNSUwIRPdhFXKy3YXvisfrtheDdOemu7g"
  },
  "@context" : [
    "https://www.w3.org/2018/credentials/v1",
    "https://w3c.github.io/vc-bitstring-status-list/contexts/2021/v1.jsonld"
  ],
  "type" : [
    "VerifiableCredential",
    "StatusList2021Credential"
  ],
  "id" : "http://localhost:8085/credential-status/c2688c98-ecb5-4dd3-87e8-a689b8fa84dd",
  "issuer" : "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
  "issuanceDate" : 1717793619,
  "credentialSubject" : {
    "type" : "StatusList2021",
    "statusPurpose" : "Revocation",
    "encodedList" : "H4sIAAAAAAAA_-3BMQ0AAAACIGf_0MbwARoAAAAAAAAAAAAAAAAAAADgbbmHB0sAQAAA"
  }
}

```

</TabItem>

</Tabs>




## Revocation

Only issuers of a credential can revoke a credential.

*Get the list of credentials*
```bash
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/issue-credentials/records' \
  -H 'accept: application/json'
```
This endpoint will return the credentials issued. Every credential includes an ID.

*Revoke the credential*
```bash
curl -X 'PATCH' \
  'http://localhost:8080/cloud-agent/revoke-credential/<credential_id>' \
  -H 'accept: */*'
```

:::note
[Present proof](./didcomm/issue.md) will fail the verification if one of the credentials the holder presents a revoked credential.
:::
