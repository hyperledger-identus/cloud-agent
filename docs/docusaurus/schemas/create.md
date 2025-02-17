# Create the credential schema

The Identus Platform exposes REST API for creation, fetching, and searching the [credential schema](/home/concepts/glossary#credential-schema) records.

The OpenAPI specification and ReDoc documentation describe the endpoint.

In this document, you can find step-by-step instructions for creating the credential schema.

## Step-by-step guide

The following guide demonstrates how to create a driving license credential schema.

### 1. Define the JSON Schema for the Verifiable Credential

Assume that you need a credential schema for the driving license, and the [verifiable credential](/home/concepts/glossary#verifiable-credential) must have the following
fields:

- emailAddress - the email address of the driver
- givenName - the first name of the driver
- familyName - the family name of the driver
- dateOfIssuance - date of the driver's license issuance
- drivingLicenseID - ID of the driving license
- drivingClass - driving class that denotes which types of vehicles the driver is allowed to go.
  Also, let's assume that the driving license might have additional optional claims.

For the above fields, the JSON Schema definition must be:

```json
{
  "$id": "https://example.com/driving-license-1.0.0",
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

The fields `$id` and `$schema` must correspond values that describe

- the identity of the given JSON Schema as a **correctly formatted URL** `https://example.com/driving-license-1.0.0` and
- the meta schema fixed to `https://json-schema.org/draft/2020-12/schema` which is the only supported value

All the claims are listed under the `properties` object with corresponding `type`s and `format`s according to JSON
Specification.

`additionalProperties` is set to true, meaning adding other fields to the verifiable credential is possible.

### 2. Create the credential schema record

1. Open your preferred REST API client, such as Postman or Insomnia, or use the client stub generated based on the
   OpenAPI specification.

2. In the client, create a new POST request to either `/cloud-agent/schema-registry/schemas` or `/cloud-agent/schema-registry/schemas/did-url` endpoints. They both take the same payload.
    1. `/cloud-agent/schema-registry/schemas` creates a schema that can later be resolved via HTTP URL
    2. `/cloud-agent/schema-registry/schemas/did-url` creates a schema that can later be resolved via [DID URL](/home/concepts/glossary#did-url), the DID includes a service endpoint with the location of the schema registry.

Note that the value of the `author` field must match the short form of a PRISM DID that has been created using the same agent. An unpublished DID is sufficient. Please refer to the [Create DID](../dids/create.md) documentation page for more details on how to create a PRISM DID.  

In the request body, create a JSON object:

```json
{
  "name": "driving-license",
  "version": "1.0.0",
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "author": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
  "tags": [
    "driving",
    "license"
  ],
  "schema": {
    "$id": "https://example.com/driving-license-1.0.0",
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
}
```

3. Send the POST request to create the new schema. The response should contain a GUID that uniquely identifies the new
   schema.
   Curl example is the following:

```shell
curl -X 'POST' \
  'http://localhost:8080/cloud-agent/schema-registry/schemas' \
  -H 'accept: application/json' \
  -H "apikey: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{
  "name": "driving-license",
  "version": "1.0.0",
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "author": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
  "tags": [
    "driving",
    "license"
  ],
  "schema": {
    "$id": "https://example.com/driving-license-1.0.0",
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
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "author": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
  "authored": "2023-03-14T14:41:46.713943Z",
  "tags": [
    "driving",
    "license"
  ],
  "schema": {
    "$id": "https://example.com/driving-license-1.0.0",
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
  },
  "kind": "CredentialSchema",
  "self": "/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2"
}
```

or in case of DID url, the response will be created schema wrapped in [Prism Envelope](/home/concepts/glossary#prism-envelope)

```json
{
   "resource":"eyJhdXRob3IiOiJkaWQ6cHJpc206ZTAyNjZlZThkODBhMDAxNjNlNWY5MjJkYzI1NjdhYjk2MTE3MjRhMDBkYjkyNDIzMzAxMTU0MjgyMTY5ZGZmOSIsImF1dGhvcmVkIjoiMjAyNC0wOS0yNVQxMDozNzoxNi4wOTM2MDlaIiwiZGVzY3JpcHRpb24iOiJEcml2aW5nIExpY2Vuc2UgU2NoZW1hIiwiZ3VpZCI6IjVjOTNmYTAwLWUwM2UtMzlkZC05NDdmLTI2NWI4YzFlYWQ4YiIsImlkIjoiNjhmMGQ4MDctYTcyYi00OTY2LTg1NWItMmIzNGJjMjYzNzAyIiwibmFtZSI6ImRyaXZpbmctbGljZW5zZSIsInJlc29sdXRpb25NZXRob2QiOiJkaWQiLCJzY2hlbWEiOnsiJGlkIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9kcml2aW5nLWxpY2Vuc2UtMS4wLjAiLCIkc2NoZW1hIjoiaHR0cHM6Ly9qc29uLXNjaGVtYS5vcmcvZHJhZnQvMjAyMC0xMi9zY2hlbWEiLCJhZGRpdGlvbmFsUHJvcGVydGllcyI6dHJ1ZSwiZGVzY3JpcHRpb24iOiJEcml2aW5nIExpY2Vuc2UiLCJwcm9wZXJ0aWVzIjp7ImRhdGVPZklzc3VhbmNlIjp7ImZvcm1hdCI6ImRhdGUtdGltZSIsInR5cGUiOiJzdHJpbmcifSwiZHJpdmluZ0NsYXNzIjp7InR5cGUiOiJpbnRlZ2VyIn0sImRyaXZpbmdMaWNlbnNlSUQiOnsidHlwZSI6InN0cmluZyJ9LCJlbWFpbEFkZHJlc3MiOnsiZm9ybWF0IjoiZW1haWwiLCJ0eXBlIjoic3RyaW5nIn0sImZhbWlseU5hbWUiOnsidHlwZSI6InN0cmluZyJ9LCJnaXZlbk5hbWUiOnsidHlwZSI6InN0cmluZyJ9fSwicmVxdWlyZWQiOlsiZW1haWxBZGRyZXNzIiwiZmFtaWx5TmFtZSIsImRhdGVPZklzc3VhbmNlIiwiZHJpdmluZ0xpY2Vuc2VJRCIsImRyaXZpbmdDbGFzcyJdLCJ0eXBlIjoib2JqZWN0In0sInRhZ3MiOlsiZHJpdmluZyIsImxpY2Vuc2UiXSwidHlwZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtanNvbi1zY2hlbWFzL3NjaGVtYS8yLjAvc2NoZW1hLmpzb24iLCJ2ZXJzaW9uIjoiMS4wLjAifQ==",
   "url":"did:prism:e0266ee8d80a00163e5f922dc2567ab9611724a00db92423301154282169dff9?resourceService=agent-base-url&resourcePath=schema-registry/schemas/did-url/5c93fa00-e03e-39dd-947f-265b8c1ead8b?resourceHash=d1557ede168f0f91097933aa2080edaf2f14fddd8a7362a22add97e431c4efe2"
}
```

### 3. Retrieve the created schema

To retrieve the newly created schema, create a new GET request to the `/cloud-agent/schema-registry/schemas/{guid}`
endpoint, where `{guid}` is the GUID returned in the response from the previous step.
Send the GET request to retrieve the schema. Curl example is the following:

```shell
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2' \
  -H 'accept: application/json' \
  -H "apikey: $API_KEY"
```

or if you need to resolve a schema created via DID url, the endpoint will look like this `/cloud-agent/schema-registry/schemas/did-url/{guid}`

```schell
curl -X 'GET' \
  'http://localhost:8080/cloud-agent/schema-registry/schemas/did-url/3f86a73f-5b78-39c7-af77-0c16123fa9c2' \
  -H 'accept: application/json' \
  -H "apikey: $API_KEY"

```

The response for HTTP URL request should contain the JSON object representing the schema you just created.

```json
{
  "guid": "3f86a73f-5b78-39c7-af77-0c16123fa9c2",
  "id": "f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea",
  "longId": "did:prism:agent/f2bfbf78-8bd6-4cc6-8b39-b3a25e01e8ea?version=1.0.0",
  "name": "driving-license",
  "version": "1.0.0",
  "description": "Driving License Schema",
  "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
  "author": "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
  "authored": "2023-03-14T14:41:46.713943Z",
  "tags": [
    "driving",
    "license"
  ],
  "schema": {
    "$id": "https://example.com/driving-license-1.0.0",
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
  },
  "kind": "CredentialSchema",
  "self": "/schema-registry/schemas/3f86a73f-5b78-39c7-af77-0c16123fa9c2"
}
```

and for DID URL request, response will include the same schema wrapped in [Prism envelope](/home/concepts/glossary#prism-envelope) response

```json
{
   "resource":"eyJhdXRob3IiOiJkaWQ6cHJpc206ZTAyNjZlZThkODBhMDAxNjNlNWY5MjJkYzI1NjdhYjk2MTE3MjRhMDBkYjkyNDIzMzAxMTU0MjgyMTY5ZGZmOSIsImF1dGhvcmVkIjoiMjAyNC0wOS0yNVQxMDozNzoxNi4wOTM2MDlaIiwiZGVzY3JpcHRpb24iOiJEcml2aW5nIExpY2Vuc2UgU2NoZW1hIiwiZ3VpZCI6IjVjOTNmYTAwLWUwM2UtMzlkZC05NDdmLTI2NWI4YzFlYWQ4YiIsImlkIjoiNjhmMGQ4MDctYTcyYi00OTY2LTg1NWItMmIzNGJjMjYzNzAyIiwibmFtZSI6ImRyaXZpbmctbGljZW5zZSIsInJlc29sdXRpb25NZXRob2QiOiJkaWQiLCJzY2hlbWEiOnsiJGlkIjoiaHR0cHM6Ly9leGFtcGxlLmNvbS9kcml2aW5nLWxpY2Vuc2UtMS4wLjAiLCIkc2NoZW1hIjoiaHR0cHM6Ly9qc29uLXNjaGVtYS5vcmcvZHJhZnQvMjAyMC0xMi9zY2hlbWEiLCJhZGRpdGlvbmFsUHJvcGVydGllcyI6dHJ1ZSwiZGVzY3JpcHRpb24iOiJEcml2aW5nIExpY2Vuc2UiLCJwcm9wZXJ0aWVzIjp7ImRhdGVPZklzc3VhbmNlIjp7ImZvcm1hdCI6ImRhdGUtdGltZSIsInR5cGUiOiJzdHJpbmcifSwiZHJpdmluZ0NsYXNzIjp7InR5cGUiOiJpbnRlZ2VyIn0sImRyaXZpbmdMaWNlbnNlSUQiOnsidHlwZSI6InN0cmluZyJ9LCJlbWFpbEFkZHJlc3MiOnsiZm9ybWF0IjoiZW1haWwiLCJ0eXBlIjoic3RyaW5nIn0sImZhbWlseU5hbWUiOnsidHlwZSI6InN0cmluZyJ9LCJnaXZlbk5hbWUiOnsidHlwZSI6InN0cmluZyJ9fSwicmVxdWlyZWQiOlsiZW1haWxBZGRyZXNzIiwiZmFtaWx5TmFtZSIsImRhdGVPZklzc3VhbmNlIiwiZHJpdmluZ0xpY2Vuc2VJRCIsImRyaXZpbmdDbGFzcyJdLCJ0eXBlIjoib2JqZWN0In0sInRhZ3MiOlsiZHJpdmluZyIsImxpY2Vuc2UiXSwidHlwZSI6Imh0dHBzOi8vdzNjLWNjZy5naXRodWIuaW8vdmMtanNvbi1zY2hlbWFzL3NjaGVtYS8yLjAvc2NoZW1hLmpzb24iLCJ2ZXJzaW9uIjoiMS4wLjAifQ==",
   "url":"did:prism:e0266ee8d80a00163e5f922dc2567ab9611724a00db92423301154282169dff9?resourceService=agent-base-url&resourcePath=schema-registry/schemas/did-url/5c93fa00-e03e-39dd-947f-265b8c1ead8b?resourceHash=d1557ede168f0f91097933aa2080edaf2f14fddd8a7362a22add97e431c4efe2"
}
```

Schemas created for HTTP URL (`/cloud-agent/schema-registry/schemas`) will not be resolvable by endpoint that returns schemas created for DID URL (`/cloud-agent/schema-registry/schemas/did-url`) and vice verca.


The Cloud Agent instance's triple `author`, `id`, and `version` are unique.
So, having a single [DID](/home/concepts/glossary#decentralized-identifier) reference that the author uses, creating the credential schema with the same `id` and `version`
is impossible.

### 4. Update the credential schema

To upgrade the credential schema, you need to perform the following steps:

1. Start from the first step and change the JSON Schema
2. Change the `version` according to the nature of your change
3. Create a new credential schema record with a higher version