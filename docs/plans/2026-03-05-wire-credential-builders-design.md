# Wire Credential Builders via ModuleRegistry Layers

**Date:** 2026-03-05
**Status:** Approved
**Depends on:** Plugin architecture Phases 0-6 (complete), Phase 37 dependency decoupling (complete)

---

## Problem

The plugin architecture has contracts, module declarations, and capability validation — but modules don't participate in the runtime. `CredentialServiceImpl` still contains ~300 lines of hardcoded format-specific credential building logic across three methods (`generateJWTCredential`, `generateSDJWTCredential`, `generateAnonCredsCredential`). The `JwtCredentialBuilder`, `SdJwtCredentialBuilder`, and `AnonCredsCredentialBuilder` exist but are never called.

## Goal

Wire the three credential builder modules into the runtime so `CredentialServiceImpl` delegates credential building to module-provided `CredentialBuilder` instances resolved via `ModuleRegistry`.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| How modules contribute services | `Module.layer` returns ZLayer | Idiomatic ZIO, type-safe, composes with existing layer system |
| How to handle type variance across contracts | Typed registries per contract | `CredentialBuilderRegistry`, `IssuanceProtocolRegistry`, etc. — typed lookup without casting |
| Cutover strategy | Direct replacement | Replace inline code with builder calls. Tests validate correctness. No feature flags. |
| Scope | Credential builders only | Protocol adapters and Modules.scala replacement come in later iterations |

## Architecture

### Module Trait Extension

```scala
trait Module:
  type Config
  type Service                          // NEW
  def id: ModuleId
  def version: SemVer
  def implements: Set[Capability]
  def requires: Set[Capability]
  def defaultConfig: Config
  def enabled(config: Config): Boolean
  def layer: TaskLayer[Service]         // NEW
```

Each module's `layer` produces its service instance. For credential builders, `Service = CredentialBuilder`.

### Typed Contract Registry

```scala
// modules/shared/core — new file
case class CredentialBuilderRegistry(
  builders: Map[CredentialFormat, CredentialBuilder]
)

object CredentialBuilderRegistry:
  // Assembles registry from all builder modules
  def fromModules(modules: Seq[Module]): Task[CredentialBuilderRegistry]
```

The registry is a ZIO service provided at startup. It holds instantiated builder instances keyed by credential format.

### ModuleRegistry Layer Assembly

`ModuleRegistry` gains the ability to assemble typed registries:

```scala
// ModuleRegistry — new method
def assembleBuilderRegistry: Task[CredentialBuilderRegistry] =
  val builderModules = modules.filter(_.implements.exists(_.contract == "CredentialBuilder"))
  // Instantiate each builder module's layer, collect into registry map
```

### CredentialServiceImpl Refactoring

Current format-specific methods become thin delegates:

```scala
// Before: 100 lines of JWT-specific logic inline
override def generateJWTCredential(recordId: DidCommID, ...): ZIO[...] = {
  // ... extract claims, build W3C payload, sign with vcJwtService ...
}

// After: delegates to builder
override def generateJWTCredential(recordId: DidCommID, ...): ZIO[...] =
  generateCredentialViaBuilder(recordId, CredentialFormat.JWT, ...)

private def generateCredentialViaBuilder(
    recordId: DidCommID,
    format: CredentialFormat,
    ...
): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
  for
    builder <- ZIO.fromOption(builderRegistry.builders.get(format))
      .orElseFail(UnsupportedFormat(format))
    record <- getRecordWithState(recordId, ProtocolState.CredentialPending)
    context <- buildContext(record)
    result <- builder.buildCredential(context)
    record <- markCredentialGenerated(record, result)
  yield record
```

### BuildContext

Standardized input for all builders, extracted from `IssueCredentialRecord`:

```scala
case class BuildContext(
  claims: Json,
  issuingDID: PrismDID,
  subjectDID: Option[String],
  validityPeriod: Option[Duration],
  schemaId: Option[String],
  credentialDefinitionId: Option[URI],
  signer: CredentialSigner,
  extras: Map[String, Json]     // format-specific data (e.g., AnonCreds request metadata)
)

case class BuildResult(
  format: IssueCredentialIssuedFormat,
  payload: Array[Byte]
)
```

### Startup Sequence

```
CloudAgentApp.run()
  ├─ AllModules.registry()
  │   ├─ validateDependencies          (existing)
  │   └─ assembleBuilderRegistry       (NEW)
  │       ├─ JwtBuilderModule.layer    → JwtCredentialBuilder
  │       ├─ SdJwtBuilderModule.layer  → SdJwtCredentialBuilder
  │       └─ AnonCredsBuilderModule.layer → AnonCredsCredentialBuilder
  ├─ Provide CredentialBuilderRegistry as ZLayer
  ├─ CredentialServiceImpl receives registry via constructor
  └─ ... rest of startup
```

## Scope

### In scope
- Extend `Module` trait with `type Service` and `def layer`
- Create `CredentialBuilderRegistry` service
- Extend `ModuleRegistry` with `assembleBuilderRegistry`
- Update all 9 module declarations with `Service` type and `layer` method
- Refactor `CredentialServiceImpl` to delegate to builders
- Wire builder registry in `AllModules` / `CloudAgentApp` startup
- Update `CredentialServiceImpl` constructor and ZLayer
- Update tests

### Out of scope
- Protocol adapter wiring
- Replacing `Modules.scala` / `MainApp.scala`
- VerificationCheck / RevocationCheck extraction
- PersistenceProvider switching
- Per-module `reference.conf` configuration

## Files Modified

| File | Action |
|------|--------|
| `Module.scala` | Add `type Service`, `def layer` |
| `ModuleRegistry.scala` | Add `assembleBuilderRegistry` |
| `CredentialBuilderRegistry.scala` | New file |
| `BuildContext.scala` / `BuildResult.scala` | New or extend existing |
| `CredentialBuilder.scala` | Update trait if `buildCredential` signature needs adjustment |
| `JwtCredentialBuilder.scala` | Implement `buildCredential(BuildContext)` |
| `SdJwtCredentialBuilder.scala` | Implement `buildCredential(BuildContext)` |
| `AnonCredsCredentialBuilder.scala` | Implement `buildCredential(BuildContext)` |
| `JwtBuilderModule.scala` | Add `type Service`, `def layer` |
| `SdJwtBuilderModule.scala` | Add `type Service`, `def layer` |
| `AnonCredsBuilderModule.scala` | Add `type Service`, `def layer` |
| All other Module objects | Add `type Service = Unit`, `def layer = ZLayer.unit` (stub) |
| `CredentialServiceImpl.scala` | Add `CredentialBuilderRegistry` dependency, delegate generate methods |
| `AllModules.scala` | Wire `assembleBuilderRegistry` |
| `CloudAgentApp.scala` | Provide `CredentialBuilderRegistry` layer |
| Test files | Update for new constructor dependencies |

## Verification

1. `sbt shared/compile` — Module trait compiles with new members
2. `sbt credentialsCore/compile` — CredentialServiceImpl compiles with builder delegation
3. `sbt credentialsCore/test` — existing tests pass
4. `sbt checkArchConstraints` — no constraint violations
5. `sbt apiServer/compile` — full server compiles with wired registry
