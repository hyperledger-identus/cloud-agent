# Create the credential schema

The PRISM platform v2.0 exposes REST API for creation, fetching, and searching the credential schema records.

The OpenAPI specification and redoc documentation describe the endpoint.

In this document, you can find step-by-step instructions for creating the credential schema.

## Step-by-step guide

The following guide demonstrates how to create a driving license credential schema.

### 1. Define the JSON Schema for the Verifiable Credential

Assume that you need a credential schema for the driving license, and the verifiable credential must have the following
fields:

- emailAddress - the email address of the driver
- givenName - the first name of the driver
- familyName - the family name of the driver
- dateOfIssuance - date of the driver's license issuance
- drivingLicenseID - ID of the driving license
- drivingClass - driving class that denotes which types of vehicles the driver is allowed to go.
  Also, let's assume that the driving license might have additional optional claims.

First of all, the JSON Schema for the given fields must be defined as:

```json
{
  "$id": "driving-license-1.0.0",
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "description": "Driving License",
  "type": "object",
  "properties": {
    "credentialSubject": {
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
          "type": "datetime"
        },
        "drivingLicenseID": {
          "type": "string"
        },
        "drivingClass": {
          "type": "integer"
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
    }
  }
}
```

Fields `$id` and `$schema` must be set to corresponding values which describe

- the identity of the given JSON Schema `driving-license-1.0.0` and
- the meta schema `https://json-schema.org/draft/2020-12/schema`

All the claims are listed under the `properties` object with corresponding `type`s and `format`s according to JSON
Specification.

`additionalProperties` is set to true, meaning other fields can also be added to the verifiable credential..

### 2. Create the credential schema record

1. Open your preferred REST API client, such as Postman or Insomnia, or use the client stub generated based on the
   OpenAPI specification.

2. In the client, create a new POST request to the `/prism-agent/schema-registry/schemas` endpoint.
   In the request body, create a JSON object:

```json
{
  "name": "driving-license",
  "version": "1.0.0",
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "schema": {
    "$id": "driving-license-1.0.0",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Driving License",
    "type": "object",
    "properties": {
      "credentialSubject": {
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
            "type": "datetime"
          },
          "drivingLicenseID": {
            "type": "string"
          },
          "drivingClass": {
            "type": "integer"
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
      }
    }
  },
  "tags": [
    "driving",
    "license"
  ]
}
```

3. Send the POST request to create the new schema. The response should contain a GUID that uniquely identifies the new
   schema.
   Curl example for the `k8s-dev.atalaprism.io` environment is the following:

```shell
curl -X 'POST' \
  'https://k8s-dev.atalaprism.io/prism-agent/schema-registry/schemas' \
  -H 'accept: application/json' \
  -H 'apikey: $APIKEY' \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "driving-license",
  "version": "1.0.0",
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "schema": {
    "$id": "driving-license-1.0.0",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Driving License",
    "type": "object",
    "properties": {
      "credentialSubject": {
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
            "type": "datetime"
          },
          "drivingLicenseID": {
            "type": "string"
          },
          "drivingClass": {
            "type": "integer"
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
      }
    }
  },
  "tags": [
    "driving",
    "license"
  ]
}'
```

...and response might be the following:

```json
{
  "guid": "3f86a73f-5b78-39c7-af77-0c16123fa9c2",
  "id": "f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea",
  "longId": "did:prism:agent/f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea?version=1.0.0",
  "name": "driving-license",
  "version": "1.0.0",
  "tags": [
    "driving",
    "license"
  ],
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "schema": {
    "$id": "driving-license-1.0.0",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Driving License",
    "type": "object",
    "properties": {
      "credentialSubject": {
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
            "type": "datetime"
          },
          "drivingLicenseID": {
            "type": "string"
          },
          "drivingClass": {
            "type": "integer"
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
      }
    }
  },
  "author": "did:prism:agent",
  "authored": "2023-03-14T14:41:46.713943Z",
  "kind": "CredentialSchema",
  "self": "/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2"
}
```

### 3. Retrieve the created schema

To retrieve the newly created schema, create a new GET request to the `/prism-agent/schema-registry/schemas/{guid}`
endpoint, where `{guid}` is the GUID returned in the response from the previous step.
Send the GET request to retrieve the schema. Curl example is the following:

```shell
curl -X 'GET' \
  'https://k8s-dev.atalaprism.io/prism-agent/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2' \
  -H 'accept: application/json' \
  -H 'apikey: $APIKEY'
```

The response should contain the JSON object representing the schema you just created.

```json
{
  "guid": "3f86a73f-5b78-39c7-af77-0c16123fa9c2",
  "id": "f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea",
  "longId": "did:prism:agent/f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea?version=1.0.0",
  "name": "driving-license",
  "version": "1.0.0",
  "tags": [
    "driving",
    "license"
  ],
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "schema": {
    "$id": "driving-license-1.0.0",
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "description": "Driving License",
    "type": "object",
    "properties": {
      "credentialSubject": {
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
            "type": "datetime"
          },
          "drivingLicenseID": {
            "type": "string"
          },
          "drivingClass": {
            "type": "integer"
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
      }
    }
  },
  "author": "did:prism:agent",
  "authored": "2023-03-14T14:41:46.713943Z",
  "kind": "CredentialSchema",
  "self": "/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2"
}

```

The triple `author`, `id`, and `version` are unique for the PRISM Agent instance.
So, having a single DID reference that the author uses, creating the credential schema with the same `id` and `version`
is impossible.

### 4. Update the credential schema

In order to upgrade the credential schema you need to perform the following steps:

1. start from the first step and change the JSON Schema
2. change the `version` according to the nature of your change
3. create a new credential schema record with a higher version