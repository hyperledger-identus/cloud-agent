# Domain Glossary & Bounded Contexts

## Bounded Contexts Mapping

| Codename | Domain-First Name | Rationale |
| :--- | :--- | :--- |
| mercury | didcomm | DIDComm protocols, agent/resolver |
| pollux | credentials | Issuance, presentation, JWT, AnonCreds |
| castor | did | DID lifecycle and registry |
| connect | connections | Connection/session workflows |
| event-notification | notifications | Outbound events/webhooks |
| cloud-agent/service/wallet-api | wallet-management | Wallet/key/tenant/IAM ops |
| cloud-agent/service/server | api-server | Runtime composition + HTTP |
| apollo (external) | crypto | Key ops/signing facade over apollo |

## Module Layout (Target)

- `modules/core/`: stable primitives: ids, errors, JSON, testkit
- `modules/crypto/`: key generation, signing, verification — apollo-adapter
- `modules/did/`: api · domain · persistence-doobie · prism-node-adapter
- `modules/didcomm/`: api · protocols · agent · resolver
- `modules/credentials/`: api · domain · jwt · sd-jwt · anoncreds · persistence-doobie
- `modules/connections/`: api · domain · persistence-doobie
- `modules/vdr/`: api · core · memory/database/prism-node/blockfrost adapters · http
- `modules/notifications/`: api · domain · webhook-adapter
- `modules/wallet-management/`: api · domain · iam-keycloak-adapter · secrets-vault-adapter
- `modules/api-server/`: bootstrap · http (composition only)
