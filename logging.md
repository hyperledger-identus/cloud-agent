# Logging

We want traceability from HTTP calls.

- Each HTTP call needs to generate a call ID (preferable on the proxy). This ID must be should be pass to the scope of the ZIO application. So every log will mention this ID. This ID must also be returned to the user as a HTTP header.
- In case of errors and problem reports those IDs can be use by the user to get support.

Level 3 support can be provided to user during the logging retention policies. We user need to provide the value in the header `X-Request-Id` to get this support.

## Annotates

We have special annotations in the log so there is traceability between the different logs.
Here is the list of annotations and their meaning that we currently have:

- `request-id` - Is the HTTP header `X-Request-Id` from the caller.
  - If this header is missing, it is create by the APISIX <https://apisix.apache.org/docs/apisix/plugins/request-id/>. See the configuration how to enable in the file [apisixroute.yaml](./infrastructure/charts/agent/templates/apisixroute.yaml)

## Code

To have a concise code we have created a Middleware that modifies the Annotations in the Scope before each execution of that endpoint, to include the trace ID of the request.
See code in file `LogUtils.scala`.

## Logging Backend

We use the Simple Logging Facade for Java (SLF4J) API to call the logging backend is determined at runtime.

### Logging Pattern

`%d{yyyy-MM-dd_HH:mm:ss.SSS} %highlight(%-5level) %cyan(%logger{5}@L%line:[%-4.30thread]) {%mdc} - %msg%xException%n`

- `%d{yyyy-MM-dd_HH:mm:ss.SSS}` is the date/timestamp of the log in the human-readable way
- `%highlight(%-5level)` the log level
- `%cyan(%logger{5}@L%line:[%-4.30thread])`
  - `%cyan` - Is just modify the color to make easy to read
  - `%logger{5}` - class name that originated the log
  - `@L%line` - line of the code that originated the log
  - `%-4.30thread` - the id of the thread. The ZIO fiber name
- `%mdc` - the mapped diagnostic context [MDC](https://logback.qos.ch/manual/layouts.html#mdc)
- `%msg` - the log message
- `%xException` - exception info

## APIs with logging

- Credential Definition Registry
  - GET - /credential-definition-registry/definitions
  - POST - /credential-definition-registry/definitions
  - GET - /credential-definition-registry/definitions/{guid}
  - GET - /credential-definition-registry/definitions/{guid}/definition
- Schema Registry
  - GET - /schema-registry/schemas
  - PPOST - /schema-registry/schemas
  - PUT - /schema-registry/{author}/{id}
  - GET - /schema-registry/schemas/{guid}
  - ??? TODO ??? GET - /schema-registry/test (this endpoint is in the list but I can't find the code for it)
- Verification
  - GET - /verification/policies
  - POST - /verification/policies
  - GET - /verification/policies/{id}
  - PUT - /verification/policies/{id}
  - DEL - /verification/policies/{id}
- Connections Management
  - GET - /connections
  - POST - /connections
  - GET - /connections/{connectionId}
  - POST -  /connection-invitations
- DID
  - /dids/{didRef}
- DID Registrar
  - GET - /did-registrar/dids
  - POST - /did-registrar/dids
  - GET - /did-registrar/dids/{didRef}
  - POST - /did-registrar/dids/{didRef}/publications
  - POST - /did-registrar/dids/{didRef}/updates
  - POST - /did-registrar/dids/{didRef}/deactivations
- Issue Credentials Protocol
  - POST - /issue-credentials/credential-offers
  - GET - /issue-credentials/records
  - GET - /issue-credentials/records/{recordId}
  - POST - /issue-credentials/records/{recordId}/accept-offer
  - POST - /issue-credentials/records/{recordId}/issue-credential
- Present Proof
  - GET - /present-proof/presentations
  - POST - /present-proof/presentations
  - GET - /present-proof/presentations/{presentationId}
  - PATH - /present-proof/presentations/{presentationId}
- System
  - GET - /_system/health
  - GET - /_system/metrics
- Identity and Access Management
  - GET - /iam/entities
  - POST - /iam/entities
  - PUT - /iam/entities/{id}/name
  - PUT - /iam/entities/{id}/walletId
  - GET - /iam/entities/{id}
  - DEL - /iam/entities/{id}
  - POST - /iam/apikey-authentication
  - DEL - /iam/apikey-authentication
- Wallet Management
  - GET - /wallets
  - POST - /wallets
  - GET - /wallets/{walletId}
  - POST - /wallets/{walletId}/uma-permissions
  - DEL - /wallets/{walletId}/uma-permissions
- Events
  - GET - /events/webhooks
  - POST - /events/webhooks
  - DEL - /events/webhooks/{id}
