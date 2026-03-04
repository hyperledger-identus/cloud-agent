# Plugin Architecture Design

> Composable, modular architecture for the Identus Cloud Agent

## Problem Statement

The current cloud-agent architecture suffers from:

- **God objects**: `CredentialServiceImpl` (1,580 lines, 29 methods), `Modules.scala` (4 monolithic objects), `MainApp.scala` (~120 ZIO layers in a single `.provide()`)
- **Format-specific logic scattered across shared traits**: `createJWTIssueCredentialRecord`, `createSDJWTIssueCredentialRecord`, `createAnonCredsIssueCredentialRecord` all on one `CredentialService` trait
- **Protocol and credential concerns entangled**: DIDComm state machines, record CRUD, credential building, and signing all in one service
- **No way to add/remove features without modifying core code**: Adding a new credential format or protocol requires touching many files across many modules
- **Slow incremental builds**: Tight coupling means changes cascade across the dependency graph

## Goals

1. Composable plugin architecture — add features by dropping in modules
2. Proper separation of concerns across credential and protocol dimensions
3. Enable/disable features via configuration
4. Faster incremental builds through smaller, decoupled modules
5. Each module owns its config, migrations, and lifecycle

## Reference Implementations

This design draws from:

| Project | Pattern borrowed |
|---------|-----------------|
| **Credo-TS** | `Module` trait with lifecycle; `DidCommCredentialFormatService` per-phase interface |
| **Lace Platform** | `Contract` pattern — pure interface packages that modules depend on instead of each other |
| **Lightbend Config** | `reference.conf` / `application.conf` — per-module config with defaults, auto-merged at runtime |
| **Veramo** | Plugin system with capability registration |

---

## Architecture Overview

### Two Independent Axes

**Credential axis** — what we build and verify:

| Dimension | Role | Examples |
|-----------|------|----------|
| Format | Wire format / serialization | JWT, JSON-LD, SD-JWT, AnonCreds |
| Data Model | Credential structure / envelope | VCDM 1.1, VCDM 2.0, AnonCreds schema, custom |
| Builder | Steps to construct a credential | Pipeline of `BuildStep`s, varies by format x data model |
| Signer | Cryptographic signing | EdDSA, ES256, ES256K, BBS+, CL signatures |
| Verifier | Composed verification checks | Signature, expiry, claims, predicates, revocation, issuer trust, ZKP |

**Protocol axis** — how we exchange credentials:

| Dimension | Role | Examples |
|-----------|------|----------|
| Transport | Message delivery layer | DIDComm, OIDC/HTTP, KERI (future) |
| Protocol | State machine for exchange | Aries Issue Credential, Aries Present Proof, OID4VCI, OID4VP |
| Sub-protocol | Cross-cutting exchange logic | PEX (works over DIDComm or OIDC) |

These axes are independent — any credential type can flow over any protocol/transport combination.

---

## Core Infrastructure

### Module Trait

Every feature is a `Module` with a lifecycle:

```scala
trait Module:
  def id: ModuleId
  def version: SemVer

  // What this module provides and needs
  def implements: Set[Capability]
  def requires: Set[Capability]

  // Per-module configuration (from reference.conf)
  type Config
  def configDecoder: ConfigDecoder[Config]
  def enabled(config: Config): Boolean

  // Lifecycle
  def register(registry: ModuleRegistry): IO[ModuleError, Unit]
  def migrate(config: Config): IO[ModuleError, Unit]
  def initialize(config: Config): IO[ModuleError, ZLayer[Any, Nothing, ?]]
  def shutdown: IO[ModuleError, Unit]
```

### Capability & Cardinality

Modules declare what they provide and require via capabilities:

```scala
case class Capability(contract: ContractId, variant: Option[String] = None)

enum Cardinality:
  case ExactlyOne    // e.g., ModuleRegistry itself
  case AtLeastOne    // e.g., CredentialBuilder — need at least one format
  case ZeroOrMore    // e.g., VerificationCheck — optional checks
  case ZeroOrOne     // e.g., PresentationExchange
```

### Contract

A Contract is a pure interface package — no implementations, no dependencies on other modules. Modules depend on contracts, never on each other.

```scala
trait Contract:
  def id: ContractId
  def cardinality: Cardinality
```

### ModuleRegistry

Assembles all modules at startup:

```scala
class ModuleRegistry:
  def loadModules(config: AppConfig): IO[RegistryError, Seq[Module]]
  def validateDependencies: IO[RegistryError, Unit]   // checks cardinality + requires/implements
  def assembleLayers: IO[RegistryError, ZLayer[...]]  // collects all module layers
  def startup: IO[RegistryError, Unit]                // migrate → initialize in dependency order
```

Validation at startup:
- Every `requires` capability has at least one enabled module that `implements` it
- Cardinality constraints are satisfied (e.g., `AtLeastOne` builder exists)
- No circular dependencies between modules
- Fail fast with clear error messages

### Per-Module Configuration

Each module includes a `reference.conf` with its defaults (Lightbend Config pattern):

```hocon
# reference.conf inside jwt-builder module JAR
identus.modules.jwt-builder {
  enabled = true
  supported-data-models = ["vcdm1.1", "vcdm2.0"]
}
```

Overridden by `application.conf`:

```hocon
# application.conf
identus.modules.jwt-builder.enabled = true
identus.modules.anoncreds.enabled = false
identus.modules.oid4vp.enabled = false
```

---

## Credential Contracts

### DataModelCodec

Structure and envelope of the credential:

```scala
// Contract: data-model-codec
// Cardinality: AtLeastOne
trait DataModelCodec:
  def modelType: DataModelType          // VCDM_1_1, VCDM_2_0, AnonCreds, Custom
  def encodeClaims(claims: Json, meta: ClaimsMeta): IO[CodecError, EncodedClaims]
  def decodeClaims(raw: RawCredential): IO[CodecError, DecodedClaims]
  def validateStructure(raw: RawCredential): IO[CodecError, Unit]
```

Implementations: `VcDm11CodecModule`, `VcDm20CodecModule`, `AnonCredsCodecModule`.

### CredentialSigner

Isolated cryptographic operation:

```scala
// Contract: credential-signer
// Cardinality: AtLeastOne
trait CredentialSigner:
  def algorithm: SignatureAlgorithm     // EdDSA, ES256, ES256K, BBS_PLUS, CL
  def sign(payload: Array[Byte], keyRef: KeyRef): IO[SignError, Array[Byte]]
  def verify(payload: Array[Byte], signature: Array[Byte], pubKey: PublicKey): IO[SignError, Boolean]
```

Implementations: `EdDsaSignerModule`, `Es256SignerModule`, `Es256kSignerModule`, `BbsPlusSignerModule`, `ClSignatureModule` (part of AnonCreds).

Signers are shared across formats — JWT and JSON-LD can both use EdDSA.

### CredentialBuilder

Assembles a credential through a pipeline of steps:

```scala
// Contract: credential-builder
// Cardinality: AtLeastOne
trait CredentialBuilder:
  def format: CredentialFormat
  def supportedDataModels: Set[DataModelType]

  def buildCredential(ctx: BuildContext): IO[BuildError, BuiltCredential]
  def buildOffer(ctx: OfferBuildContext): IO[BuildError, BuiltOffer]
  def buildRequest(ctx: RequestBuildContext): IO[BuildError, BuiltRequest]

  // Introspection
  def steps: Seq[BuildStepDescriptor]
```

Internally, each builder is a pipeline of `BuildStep`s:

```scala
trait BuildStep:
  def name: String
  def execute(state: BuildState): IO[BuildError, BuildState]
```

#### Builder Pipelines by Format

**JWT:**
`ValidateClaims` -> `AssemblePayload` -> `AddStatusList` -> `Sign`

**SD-JWT:**
`ValidateClaims` -> `AssemblePayload` -> `SelectDisclosures` -> `HashDisclosures` -> `AddStatusList` -> `Sign`

**AnonCreds (issuer side):**
`FetchCredDef` -> `ValidateAttributes` -> `ComputeCredValues` -> `ProcessBlindedRequest` -> `CLSign` -> `AttachRevocation`

**AnonCreds (holder request):**
`ParseOffer` -> `FetchCredDef` -> `ResolveLinkSecret` -> `BlindLinkSecret` -> `SerializeRequest`

Steps are reusable across builders (e.g., `ValidateClaims`, `AddStatusList`). New formats = compose a new pipeline from existing + new steps.

### VerificationCheck

A single verification concern:

```scala
// Contract: verification-check
// Cardinality: ZeroOrMore
trait VerificationCheck:
  def checkType: VerificationCheckType
  def appliesTo(credential: RawCredential): Boolean
  def verify(credential: RawCredential, ctx: VerifyContext): IO[VerifyError, CheckResult]
```

| Check | Applies to | What it does |
|-------|-----------|-------------|
| `SignatureCheck` | All formats | Verifies cryptographic signature (delegates to `CredentialSigner.verify`) |
| `ExpiryCheck` | JWT, SD-JWT, JSON-LD | Checks exp/nbf timestamps |
| `ClaimsSchemaCheck` | All formats | Validates claims against credential schema |
| `PredicateCheck` | AnonCreds | Evaluates ZKP predicates (>, <, =) |
| `RevocationCheck` | Per mechanism | Checks revocation status (see below) |
| `IssuerTrustCheck` | All formats | Verifies issuer DID is trusted |
| `ZkpCheck` | AnonCreds, BBS+ | Verifies zero-knowledge proofs |
| `DisclosureCheck` | SD-JWT | Verifies selective disclosure hashes |

**CredentialVerifier** is a combinator (not a separate contract):

```scala
class CredentialVerifier(checks: Seq[VerificationCheck]):
  def verify(
    credential: RawCredential,
    requestedChecks: Set[VerificationCheckType] = Set.all
  ): IO[VerifyError, VerificationResult] =
    for
      applicable <- ZIO.succeed(checks.filter(c => requestedChecks(c.checkType) && c.appliesTo(credential)))
      results    <- ZIO.foreach(applicable)(_.verify(credential, ctx))
    yield VerificationResult(results)
```

At runtime, only applicable checks fire — determined by `appliesTo`. A JWT with StatusList2021 runs `SignatureCheck("es256") + ExpiryCheck + RevocationCheck("status-list-2021") + IssuerTrustCheck`. An AnonCreds credential runs `SignatureCheck("cl") + RevocationCheck("anoncreds-accumulator") + PredicateCheck`.

### RevocationCheck (polymorphic)

Revocation is itself a contract with multiple implementations per spec:

```scala
// Contract: revocation-check
// Cardinality: ZeroOrMore (one per revocation mechanism)
trait RevocationCheck extends VerificationCheck:
  def mechanism: RevocationMechanism
  def appliesTo(credential: RawCredential): Boolean  // inspects credentialStatus field
  def checkRevocation(credential: RawCredential, ctx: RevocationContext): IO[RevocationError, RevocationStatus]
```

| Revocation Mechanism | Spec | How verification works |
|---|---|---|
| StatusList2021 | W3C StatusList2021 | Fetch bitstring, check index |
| RevocationList2020 | W3C CredentialStatus RL2020 | Fetch list, check membership |
| AnonCreds Revocation | Hyperledger AnonCreds | Cryptographic accumulator + non-revocation proof |
| Token Status List | IETF draft (OAuth/OID4VCI) | JWT-encoded bitstring, check index |
| KERI-based (future) | KERI TEL | Transaction event log lookup |

Implementations: `StatusList2021Module`, `AnonCredsRevocationModule`, `TokenStatusListModule`.

---

## Protocol Contracts

### ProtocolTransport

Message delivery layer:

```scala
// Contract: protocol-transport
// Cardinality: AtLeastOne
trait ProtocolTransport:
  def transportType: TransportType    // DIDComm, OIDC, KERI
  def send(message: ProtocolMessage, destination: Endpoint): IO[TransportError, Unit]
  def receive: Stream[TransportError, ProtocolMessage]
```

### IssuanceProtocol

State machine for credential issuance:

```scala
// Contract: issuance-protocol
// Cardinality: AtLeastOne
trait IssuanceProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  // Protocol phases — format-agnostic, delegates to CredentialBuilder
  def initiateOffer(params: OfferParams): IO[ProtocolError, IssuanceRecord]
  def processOffer(message: ProtocolMessage): IO[ProtocolError, IssuanceRecord]
  def createRequest(recordId: RecordId): IO[ProtocolError, IssuanceRecord]
  def processRequest(message: ProtocolMessage): IO[ProtocolError, IssuanceRecord]
  def issueCredential(recordId: RecordId): IO[ProtocolError, IssuanceRecord]
  def processCredential(message: ProtocolMessage): IO[ProtocolError, IssuanceRecord]

  // State machine transitions (no format involvement)
  def markSent(recordId: RecordId, phase: Phase): IO[ProtocolError, IssuanceRecord]
  def reportFailure(recordId: RecordId, reason: Failure): IO[ProtocolError, Unit]
```

Each protocol owns its own record type. DIDComm uses `IssueCredentialRecord`. OID4VCI uses `IssuanceSession`. They don't share state.

The protocol dispatches format-specific work to the matching `CredentialBuilder`:

```scala
// Inside DIDCommIssuanceProtocol
def issueCredential(recordId: RecordId) =
  for
    record  <- getById(recordId)
    builder <- registry.resolve[CredentialBuilder](record.credentialFormat)
    result  <- builder.buildCredential(BuildContext.from(record))
    _       <- updateRecord(record, result)
    _       <- transport.send(issueMessage(result), record.destination)
  yield record
```

### PresentationProtocol

State machine for presentation/verification:

```scala
// Contract: presentation-protocol
// Cardinality: AtLeastOne
trait PresentationProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def requestPresentation(params: PresentationParams): IO[ProtocolError, PresentationRecord]
  def processRequest(message: ProtocolMessage): IO[ProtocolError, PresentationRecord]
  def createPresentation(recordId: RecordId): IO[ProtocolError, PresentationRecord]
  def processPresentation(message: ProtocolMessage): IO[ProtocolError, PresentationRecord]
  def verifyPresentation(recordId: RecordId): IO[ProtocolError, PresentationRecord]
```

### PresentationExchange (cross-cutting)

PEX is a sub-protocol that works over both DIDComm and OIDC:

```scala
// Contract: presentation-exchange
// Cardinality: ZeroOrOne
trait PresentationExchange:
  def matchCredentials(
    definition: PresentationDefinition,
    available: Seq[RawCredential]
  ): IO[PEXError, PresentationSubmission]
  def validateSubmission(
    definition: PresentationDefinition,
    submission: PresentationSubmission
  ): IO[PEXError, ValidationResult]
```

---

## Module Composition

### Module dependency graph

```
JwtBuilderModule:
  implements: [CredentialBuilder("jwt")]
  requires: [CredentialSigner(any), DataModelCodec(any)]

SdJwtBuilderModule:
  implements: [CredentialBuilder("sd-jwt")]
  requires: [CredentialSigner(any), DataModelCodec(any)]

AnonCredsModule:
  implements: [CredentialBuilder("anoncreds"), CredentialSigner("cl"), DataModelCodec("anoncreds")]
  // Self-contained — AnonCreds has its own signing + data model

VcDm11CodecModule:
  implements: [DataModelCodec("vcdm1.1")]

VcDm20CodecModule:
  implements: [DataModelCodec("vcdm2.0")]

EdDsaSignerModule:
  implements: [CredentialSigner("eddsa")]

Es256SignerModule:
  implements: [CredentialSigner("es256")]

SignatureCheckModule:
  implements: [VerificationCheck("signature")]

ExpiryCheckModule:
  implements: [VerificationCheck("expiry")]

StatusList2021Module:
  implements: [RevocationCheck("status-list-2021")]

AnonCredsRevocationModule:
  implements: [RevocationCheck("anoncreds-accumulator")]
  requires: [CredentialSigner("cl")]

TokenStatusListModule:
  implements: [RevocationCheck("token-status-list")]

PredicateCheckModule:
  implements: [VerificationCheck("predicate")]

IssuerTrustCheckModule:
  implements: [VerificationCheck("issuer-trust")]

DIDCommIssuanceModule:
  implements: [IssuanceProtocol("aries-issue-v2")]
  requires: [CredentialBuilder(any), ProtocolTransport("didcomm")]

DIDCommPresentationModule:
  implements: [PresentationProtocol("aries-present-v2")]
  requires: [VerificationCheck(any), ProtocolTransport("didcomm")]

DIDCommTransportModule:
  implements: [ProtocolTransport("didcomm")]

OIDCTransportModule:
  implements: [ProtocolTransport("oidc")]

OID4VCIModule:
  implements: [IssuanceProtocol("oid4vci")]
  requires: [CredentialBuilder(any), ProtocolTransport("oidc")]

OID4VPModule:
  implements: [PresentationProtocol("oid4vp")]
  requires: [VerificationCheck(any), ProtocolTransport("oidc"), PresentationExchange]

PEXModule:
  implements: [PresentationExchange]
```

### Runtime composition example

Issuing a JWT credential over DIDComm:

```
DIDCommIssuanceProtocol
  -> resolves CredentialBuilder("jwt")
    -> JwtBuilder uses DataModelCodec("vcdm2.0") + CredentialSigner("es256")
    -> Pipeline: ValidateClaims -> AssemblePayload -> AddStatusList -> Sign
  -> sends IssueCredential message via ProtocolTransport("didcomm")
```

Verifying an SD-JWT presentation over OID4VP:

```
OID4VPPresentationProtocol
  -> uses PresentationExchange to match credentials against definition
  -> runs CredentialVerifier composed of:
    -> SignatureCheck (delegates to CredentialSigner("eddsa").verify)
    -> DisclosureCheck (SD-JWT hash verification)
    -> ExpiryCheck
    -> RevocationCheck("status-list-2021")
    -> IssuerTrustCheck
```

---

## How This Replaces the Current Architecture

### CredentialService decomposition

| Current (god trait) | New (decomposed) |
|---|---|
| `createJWTIssueCredentialRecord` | `DIDCommIssuanceProtocol.initiateOffer(JWT, params)` -> `JwtBuilder.buildOffer` |
| `createSDJWTIssueCredentialRecord` | `DIDCommIssuanceProtocol.initiateOffer(SDJWT, params)` -> `SdJwtBuilder.buildOffer` |
| `createAnonCredsIssueCredentialRecord` | `DIDCommIssuanceProtocol.initiateOffer(AnonCreds, params)` -> `AnonCredsBuilder.buildOffer` |
| `generateJWTCredentialRequest` | `DIDCommIssuanceProtocol.createRequest(recordId)` -> dispatches by record format |
| `generateJWTCredential` | `DIDCommIssuanceProtocol.issueCredential(recordId)` -> dispatches by record format |
| `receiveCredentialOffer` | `DIDCommIssuanceProtocol.processOffer(message)` -> dispatches by attachment format |
| `markOfferSent` / `markRequestSent` | `DIDCommIssuanceProtocol.markSent(recordId, phase)` — pure state machine |
| `getJwtIssuer` | Internal to `JwtBuilderModule` — not exposed |
| `reportProcessingFailure` | `DIDCommIssuanceProtocol.reportFailure(recordId, reason)` |

### Modules.scala decomposition

| Current | New |
|---|---|
| `SystemModule` (monolithic) | Split across infrastructure modules (config, logging, metrics) |
| `AppModule` (monolithic) | Each domain module contributes its own ZIO layers via `Module.initialize` |
| `GrpcModule` (monolithic) | Part of DIDComm transport module |
| `RepoModule` (monolithic) | Each module owns its own repository layers |
| `MainApp.provide(~120 layers)` | `ModuleRegistry.assembleLayers` collects from all modules |

---

## Migration Strategy

### Principles

- **Strangler fig pattern**: Extract, delegate, verify, remove. Old code works at every step.
- **Each phase is one or more PR-sized changes** that compile and pass tests.
- **Rollback safety**: Old code exists alongside new until verified.

### Phase 0: Foundation (pure addition, zero risk)

| Step | Deliverable |
|------|------------|
| 0.1 | `Module` trait with lifecycle, config, capabilities |
| 0.2 | `ModuleRegistry` with dependency resolution and startup validation |
| 0.3 | `Capability` / `Contract` types with cardinality constraints |
| 0.4 | `reference.conf` infrastructure — per-module config loading |
| 0.5 | Contract packages (empty traits) for all contracts listed above |

**Unlocks:** All subsequent phases can begin. Nothing existing changes.

**Verification:** `sbt compile` + `sbt checkArchConstraints`.

### Phase 1: Extract leaf components (low risk, parallelizable)

**1a — Credential Signers**

Extract from `CredentialServiceImpl`, `JwtCredentialIssuer`, crypto utilities.

Modules: `EdDsaSignerModule`, `Es256SignerModule`, `Es256kSignerModule`.

**1b — Verification Checks**

Extract from `VcVerificationServiceImpl`.

Modules: `SignatureCheckModule`, `ExpiryCheckModule`, `ClaimsSchemaCheckModule`, `IssuerTrustCheckModule`.

**1c — Revocation Checks**

Extract from `CredentialStatusServiceImpl`, AnonCreds revocation logic.

Modules: `StatusList2021Module`, `AnonCredsRevocationModule`, `TokenStatusListModule`.

**1d — Data Model Codecs**

Extract claim encoding/decoding logic from `CredentialServiceImpl`.

Modules: `VcDm11CodecModule`, `VcDm20CodecModule`, `AnonCredsCodecModule`.

**Unlocks:** Independently testable components. New signing algorithms or revocation mechanisms = one new module, zero changes to existing code.

### Phase 2: Extract builders (medium risk)

**2a** — Create `BuildStep` trait, `BuildState`, shared steps (`ValidateClaims`, `AddStatusList`).

**2b** — `JwtBuilderModule` extracted from `CredentialServiceImpl`.

**2c** — `SdJwtBuilderModule` extracted from `CredentialServiceImpl`.

**2d** — `AnonCredsBuilderModule` extracted from `CredentialServiceImpl`.

`CredentialServiceImpl` methods become thin delegators to builders (strangler fig).

**Unlocks:** `CredentialServiceImpl` shrinks dramatically. New formats = new builder module.

### Phase 3: Extract protocol state machines (higher risk)

**3a** — `DIDCommIssuanceModule`: record CRUD + state transitions + DIDComm message handling. Extracted from `CredentialService`.

**3b** — `DIDCommPresentationModule`: extracted from `PresentationService`.

**3c** — `OID4VCIModule`: formalize existing `oid4vciCore` as a module.

**3d** — `OID4VPModule`: formalize as a module.

Old `CredentialService` becomes a facade, then removed.

**Unlocks:** Each protocol evolves independently. Protocol-specific record types don't leak across boundaries.

### Phase 4: Extract transport & PEX (medium risk)

**4a** — `DIDCommTransportModule`

**4b** — `OIDCTransportModule`

**4c** — `PEXModule` — works across DIDComm and OIDC.

**Unlocks:** New transports = one module. PEX reusable across protocols.

### Phase 5: Wire via ModuleRegistry (final step)

**5a** — Modules self-register ZIO layers.

**5b** — Replace `Modules.scala` with `ModuleRegistry.assembleAll`.

**5c** — Replace `MainApp.scala` monolithic `.provide(...)` with `ModuleRegistry.assembleLayers`.

**5d** — Enable/disable modules via `application.conf`.

**Unlocks:** Feature toggling via config. Monolithic wiring files eliminated.

### Phase ordering

```
Phase 0 --> Phase 1a, 1b, 1c, 1d (parallel)
                      |
                      v
              Phase 2a --> Phase 2b, 2c, 2d (parallel)
                                  |
                                  v
                          Phase 3a, 3b (parallel) --> Phase 3c, 3d
                                  |
                                  v
                          Phase 4a, 4b, 4c (parallel)
                                  |
                                  v
                          Phase 5a --> 5b --> 5c --> 5d
```

Each sub-phase is a single PR. Phases 1a-1d, 2b-2d, 3a-3b, and 4a-4c are independent and can run in parallel.

---

## Architecture Constraints

New constraints to enforce during migration (added to `project/ArchConstraints.scala`):

- Contract packages must have zero implementation dependencies
- Modules must not depend on other modules directly — only on contracts
- Protocol modules must not import credential builder internals
- Builder modules must not import protocol state types
- Signer modules must not depend on any format-specific code
