# Domain Glossary

This document maps the legacy codename-based module names to their domain-first equivalents.

## Module Naming Table

| Codename | Domain Name | SBT Alias | Package |
|----------|------------|-----------|---------|
| castor / castorCore | did / did-core | `didCore` | `org.hyperledger.identus.did` |
| mercury/models | didcomm-models | `didcommModels` | `org.hyperledger.identus.didcomm` |
| mercury/agent | didcomm-agent | `didcommAgent` | `org.hyperledger.identus.didcomm` |
| connect / connectCore | connections / connections-core | `connectionsCore` | `org.hyperledger.identus.connections` |
| pollux / polluxCore | credentials / credentials-core | `credentialsCore` | `org.hyperledger.identus.credentials` |
| event-notification | notifications | `notifications` | `org.hyperledger.identus.notifications` |
| cloud-agent/wallet-api | wallet-management | `walletManagement` | `org.hyperledger.identus.wallet` |
| cloud-agent/server | api-server | `apiServer` | `org.hyperledger.identus.server` |
| vdr/core | vdr-core | `vdrApi` | `org.hyperledger.identus.vdr` |

## Bounded Contexts

### DID Management (`did`)
Manages Decentralized Identifiers — creation, resolution, updates, deactivation via PRISM node.

### DIDComm Messaging (`didcomm`)
Handles DIDComm v2 message models, protocol implementations, and agent orchestration.

### Verifiable Credentials (`credentials`)
Issues, verifies, and manages Verifiable Credentials in JWT, AnonCreds, and SD-JWT formats.

### Connections (`connections`)
Manages DIDComm connection establishment and lifecycle via connection protocols.

### Notifications (`notifications`)
Event notification infrastructure for cross-context domain events.

### Wallet Management (`wallet-management`)
Manages wallets, keys, managed DIDs, entities, and secret storage.

### VDR (`vdr`)
Verifiable Data Registry — abstraction over PRISM node, blockfrost, and other resolution backends.

### API Server (`api-server`)
HTTP API composition layer — controllers, routing, and application bootstrap.
