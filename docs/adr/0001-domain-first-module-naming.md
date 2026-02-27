# ADR 0001: Domain-First Module Naming

## Status

Accepted

## Context

The cloud-agent codebase uses codename-based module names (mercury, pollux, castor, connect) that obscure domain meaning. New contributors must learn an arbitrary mapping before understanding module boundaries. The `shared/*` module has grown too broad, `polluxCore` has a reverse dependency on `cloudAgentWalletAPI`, and the server module is a kitchen-sink aggregator.

### Current Problems

1. **Opaque naming**: `mercury`, `pollux`, `castor` convey no domain semantics
2. **Reverse dependency**: `polluxCore` depends on `cloudAgentWalletAPI` (a higher-level module depending on a lower-level one)
3. **Shared module bloat**: `shared/*` contains too many concerns
4. **Server aggregation**: `cloudAgentServer` directly depends on all domain modules

## Decision

Introduce domain-first naming with explicit bounded context boundaries:

| Codename | Domain Name | Bounded Context |
|----------|------------|-----------------|
| mercury | didcomm | DIDComm messaging |
| pollux | credentials | Verifiable Credentials |
| castor | did | DID management |
| connect | connections | Connection protocols |
| event-notification | notifications | Event notifications |
| wallet-api | wallet-management | Wallet & key management |
| server | api-server | HTTP API composition |

### Target Module Layout

```
modules/
  did/
    api/          # Traits, models, errors (no implementations)
    core/         # Business logic (current castorCore)
  didcomm/
    api/          # Core DIDComm types
    models/       # Extended models (current mercury/models)
    protocols/    # Protocol implementations
    agent/        # Agent orchestration
  credentials/
    api/          # Traits, models, errors
    core/         # Business logic (current polluxCore)
    persistence-doobie/
  connections/
    api/          # Traits, models, errors
    core/         # Business logic (current connectCore)
    persistence-doobie/
  notifications/
    api/          # Traits, event types
    core/         # Current eventNotification
  wallet-management/
    api/          # Traits, models, storage ports
    core/         # Current cloudAgentWalletAPI
    persistence-doobie/
    iam-keycloak/
    secrets-vault/
  vdr/
    api/          # VDR service traits
    core/         # Current vdrCore
```

### Migration Strategy

1. Extract thin `-api` modules with only traits, models, and error types
2. Add SBT aliases mapping new names to old modules
3. Gradually move implementations behind API boundaries
4. Break reverse dependency: `polluxCore` → `walletManagementApi` (instead of `cloudAgentWalletAPI`)
5. Physically relocate source directories in final phase

## Consequences

### Positive
- Module names immediately communicate domain purpose
- Clean dependency direction enforced via API modules
- Each bounded context has an explicit public API surface
- Reverse dependency cycle broken
- Easier onboarding for new contributors

### Negative
- Migration period with both naming conventions
- Build file complexity during transition
- Package rename requires updating all imports (deferred to Phase 5)

### Risks
- Incomplete migration leaving hybrid state — mitigated by phased approach with verification at each step
- Breaking downstream consumers — mitigated by backward-compatible type aliases during transition
