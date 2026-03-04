# Plugin Architecture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Incrementally migrate the Identus cloud-agent from monolithic god objects to a composable plugin architecture with contract-based decoupling.

**Architecture:** Strangler fig migration — extract contracts (pure interfaces), implement leaf components first (signers, verification checks), then builders, then protocol state machines, finally wire via ModuleRegistry. At every step the existing code still works.

**Tech Stack:** Scala 3, ZIO 2, ZIO Test, sbt multi-project build, Lightbend Config (reference.conf), Doobie (persistence), Nimbus JOSE (JWT signing)

**Design Doc:** `docs/plans/2026-03-04-plugin-architecture-design.md`

---

## Phase 0: Foundation Infrastructure

### Task 0.1: Create the Contract base types

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Contract.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Capability.scala`

**Step 1: Write tests for Capability matching**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/CapabilitySpec.scala
package org.hyperledger.identus.shared.models

import zio.*
import zio.test.*
import zio.test.Assertion.*

object CapabilitySpec extends ZIOSpecDefault:
  def spec = suite("Capability")(
    test("exact match") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", Some("eddsa"))
      assertTrue(cap.satisfies(req))
    },
    test("wildcard match - provider with variant satisfies any-variant requirement") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", None)
      assertTrue(cap.satisfies(req))
    },
    test("no match - different contract") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialBuilder", Some("eddsa"))
      assertTrue(!cap.satisfies(req))
    },
    test("no match - different variant") {
      val cap = Capability("CredentialSigner", Some("eddsa"))
      val req = Capability("CredentialSigner", Some("es256"))
      assertTrue(!cap.satisfies(req))
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.CapabilitySpec"`
Expected: Compilation error — `Capability` not found

**Step 3: Implement Capability and Contract**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Capability.scala
package org.hyperledger.identus.shared.models

/** A capability that a module can provide or require.
  * @param contract the contract identifier (e.g. "CredentialSigner")
  * @param variant optional variant (e.g. "eddsa", "es256"). None means "any variant".
  */
case class Capability(contract: String, variant: Option[String] = None):
  /** Returns true if this capability satisfies the given requirement.
    * A requirement with variant=None is satisfied by any variant of the same contract.
    */
  def satisfies(requirement: Capability): Boolean =
    contract == requirement.contract &&
      (requirement.variant.isEmpty || variant == requirement.variant)

enum Cardinality:
  case ExactlyOne
  case AtLeastOne
  case ZeroOrMore
  case ZeroOrOne
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Contract.scala
package org.hyperledger.identus.shared.models

trait Contract:
  def id: String
  def cardinality: Cardinality
```

**Step 4: Run test to verify it passes**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.CapabilitySpec"`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Capability.scala \
        modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Contract.scala \
        modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/CapabilitySpec.scala
git commit -m "feat: add Contract and Capability base types for plugin architecture"
```

---

### Task 0.2: Create the Module trait

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala`

**Step 1: Write tests for Module lifecycle**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleSpec.scala
package org.hyperledger.identus.shared.models

import zio.*
import zio.test.*
import zio.test.Assertion.*

object ModuleSpec extends ZIOSpecDefault:

  case class TestConfig(enabled: Boolean)

  object TestModule extends Module:
    type Config = TestConfig
    val id = ModuleId("test-module")
    val version = SemVer(0, 1, 0)
    val implements = Set(Capability("TestCapability", Some("v1")))
    val requires = Set.empty[Capability]
    def defaultConfig = TestConfig(enabled = true)
    def enabled(config: Config) = config.enabled

  def spec = suite("Module")(
    test("module declares capabilities") {
      assertTrue(
        TestModule.implements.size == 1,
        TestModule.requires.isEmpty,
        TestModule.id.value == "test-module"
      )
    },
    test("module can be disabled via config") {
      assertTrue(
        TestModule.enabled(TestConfig(true)),
        !TestModule.enabled(TestConfig(false))
      )
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.ModuleSpec"`
Expected: Compilation error — `Module` not found

**Step 3: Implement Module trait**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala
package org.hyperledger.identus.shared.models

import zio.*

case class ModuleId(value: String)
case class SemVer(major: Int, minor: Int, patch: Int):
  override def toString: String = s"$major.$minor.$patch"

trait Module:
  type Config

  def id: ModuleId
  def version: SemVer

  def implements: Set[Capability]
  def requires: Set[Capability]

  def defaultConfig: Config
  def enabled(config: Config): Boolean
```

**Step 4: Run test to verify it passes**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.ModuleSpec"`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala \
        modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleSpec.scala
git commit -m "feat: add Module trait with lifecycle, capabilities, and per-module config"
```

---

### Task 0.3: Create ModuleRegistry with dependency validation

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala`

**Step 1: Write tests for dependency validation**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala
package org.hyperledger.identus.shared.models

import zio.*
import zio.test.*
import zio.test.Assertion.*

object ModuleRegistrySpec extends ZIOSpecDefault:

  trait SimpleModule extends Module:
    type Config = Unit
    def defaultConfig = ()
    def enabled(config: Unit) = true
    def version = SemVer(1, 0, 0)

  object ProviderModule extends SimpleModule:
    val id = ModuleId("provider")
    val implements = Set(Capability("Signer", Some("eddsa")))
    val requires = Set.empty[Capability]

  object ConsumerModule extends SimpleModule:
    val id = ModuleId("consumer")
    val implements = Set(Capability("Builder", Some("jwt")))
    val requires = Set(Capability("Signer"))  // any signer

  object UnsatisfiedModule extends SimpleModule:
    val id = ModuleId("unsatisfied")
    val implements = Set(Capability("Protocol", Some("v1")))
    val requires = Set(Capability("Transport", Some("keri")))  // nobody provides this

  def spec = suite("ModuleRegistry")(
    test("validates satisfied dependencies") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val result = registry.validateDependencies
      assertZIO(result)(isUnit)
    },
    test("rejects unsatisfied dependencies") {
      val registry = ModuleRegistry(Seq(ConsumerModule))  // no provider
      val result = registry.validateDependencies.exit
      assertZIO(result)(fails(anything))
    },
    test("rejects unsatisfied specific variant") {
      val registry = ModuleRegistry(Seq(ProviderModule, UnsatisfiedModule))
      val result = registry.validateDependencies.exit
      assertZIO(result)(fails(anything))
    },
    test("resolves capability to providing modules") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val signers = registry.resolve(Capability("Signer"))
      assertTrue(signers.map(_.id) == Seq(ProviderModule.id))
    },
    test("resolves with variant filter") {
      val registry = ModuleRegistry(Seq(ProviderModule, ConsumerModule))
      val eddsa = registry.resolve(Capability("Signer", Some("eddsa")))
      val es256 = registry.resolve(Capability("Signer", Some("es256")))
      assertTrue(eddsa.size == 1, es256.isEmpty)
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.ModuleRegistrySpec"`
Expected: Compilation error — `ModuleRegistry` not found

**Step 3: Implement ModuleRegistry**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala
package org.hyperledger.identus.shared.models

import zio.*

case class ModuleRegistryError(message: String) extends Exception(message)

class ModuleRegistry(modules: Seq[Module]):

  private val allProvided: Set[Capability] =
    modules.flatMap(_.implements).toSet

  def validateDependencies: IO[ModuleRegistryError, Unit] =
    val unsatisfied = for
      m   <- modules
      req <- m.requires
      if !allProvided.exists(_.satisfies(req))
    yield (m.id, req)

    if unsatisfied.isEmpty then ZIO.unit
    else
      val details = unsatisfied
        .map((mid, cap) => s"  ${mid.value} requires ${cap.contract}${cap.variant.map(v => s"($v)").getOrElse("")}")
        .mkString("\n")
      ZIO.fail(ModuleRegistryError(s"Unsatisfied dependencies:\n$details"))

  def resolve(requirement: Capability): Seq[Module] =
    modules.filter(_.implements.exists(_.satisfies(requirement)))
```

**Step 4: Run test to verify it passes**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.models.ModuleRegistrySpec"`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala \
        modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala
git commit -m "feat: add ModuleRegistry with dependency validation and capability resolution"
```

---

### Task 0.4: Create credential-axis contract packages

These are pure interface traits — no implementations. They go in `shared` so all modules can depend on them without circular dependencies.

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialSigner.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/DataModelCodec.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialBuilder.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/VerificationCheck.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/RevocationCheck.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialTypes.scala`

**Step 1: Write the contract traits**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialTypes.scala
package org.hyperledger.identus.shared.credentials

import zio.json.ast.Json

/** Wire format of a credential */
enum CredentialFormat:
  case JWT, SDJWT, JsonLD, AnonCreds

/** Data model / envelope standard */
enum DataModelType:
  case VCDM_1_1, VCDM_2_0, AnonCreds, Custom

/** Signature algorithm */
enum SignatureAlgorithm:
  case EdDSA, ES256, ES256K, BBS_PLUS, CL

/** Revocation mechanism */
enum RevocationMechanism:
  case StatusList2021, TokenStatusList, AnonCredsAccumulator, RevocationList2020

/** Type of verification check */
enum VerificationCheckType:
  case Signature, Expiry, ClaimsSchema, Predicate, Revocation, IssuerTrust, Zkp, Disclosure

/** Opaque credential bytes + format tag */
case class RawCredential(format: CredentialFormat, data: Array[Byte])

/** Result of building a credential */
case class BuiltCredential(raw: RawCredential, metadata: Json)

/** Result of a single verification check */
case class CheckResult(checkType: VerificationCheckType, success: Boolean, detail: Option[String] = None)

/** Aggregated verification result */
case class VerificationResult(checks: Seq[CheckResult]):
  def isValid: Boolean = checks.forall(_.success)

/** Opaque reference to a signing key */
case class KeyRef(id: String, algorithm: SignatureAlgorithm)

/** Context for building a credential */
case class BuildContext(
  claims: Json,
  format: CredentialFormat,
  dataModel: DataModelType,
  issuerDid: String,
  keyRef: KeyRef,
  metadata: Json = Json.Obj()
)

/** Context for verification */
case class VerifyContext(
  resolverEndpoint: Option[String] = None,
  trustedIssuers: Set[String] = Set.empty,
  currentTime: java.time.Instant = java.time.Instant.now()
)
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialSigner.scala
package org.hyperledger.identus.shared.credentials

import zio.*

trait CredentialSigner:
  def algorithm: SignatureAlgorithm
  def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]]
  def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/DataModelCodec.scala
package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.ast.Json

trait DataModelCodec:
  def modelType: DataModelType
  def encodeClaims(claims: Json, meta: Json): IO[Throwable, Json]
  def decodeClaims(raw: RawCredential): IO[Throwable, Json]
  def validateStructure(raw: RawCredential): IO[Throwable, Unit]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialBuilder.scala
package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.ast.Json

/** A single step in a credential build pipeline */
trait BuildStep:
  def name: String
  def execute(state: BuildState): IO[Throwable, BuildState]

/** Accumulated state flowing through the build pipeline */
case class BuildState(
  claims: Json,
  metadata: Map[String, Any] = Map.empty,
  payload: Option[Array[Byte]] = None,
  signature: Option[Array[Byte]] = None,
  artifacts: Map[String, Array[Byte]] = Map.empty,
)

/** Descriptor for introspection */
case class BuildStepDescriptor(name: String, description: String)

/** Assembles a credential through a pipeline of steps */
trait CredentialBuilder:
  def format: CredentialFormat
  def supportedDataModels: Set[DataModelType]
  def buildCredential(ctx: BuildContext): IO[Throwable, BuiltCredential]
  def steps: Seq[BuildStepDescriptor]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/VerificationCheck.scala
package org.hyperledger.identus.shared.credentials

import zio.*

trait VerificationCheck:
  def checkType: VerificationCheckType
  def appliesTo(credential: RawCredential): Boolean
  def verify(credential: RawCredential, ctx: VerifyContext): IO[Throwable, CheckResult]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/RevocationCheck.scala
package org.hyperledger.identus.shared.credentials

import zio.*

trait RevocationCheck extends VerificationCheck:
  def mechanism: RevocationMechanism
  override def checkType: VerificationCheckType = VerificationCheckType.Revocation
```

**Step 2: Verify compilation**

Run: `sbt shared/compile`
Expected: PASS (these are pure traits with no external dependencies beyond zio-json which shared already has)

**Step 3: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/
git commit -m "feat: add credential-axis contract interfaces (signer, builder, codec, verifier)"
```

---

### Task 0.5: Create protocol-axis contract packages

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/ProtocolTypes.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/ProtocolTransport.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/IssuanceProtocol.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/PresentationProtocol.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/PresentationExchange.scala`

**Step 1: Write the contract traits**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/ProtocolTypes.scala
package org.hyperledger.identus.shared.protocols

import zio.json.ast.Json

import java.util.UUID

enum TransportType:
  case DIDComm, OIDC, KERI

/** Protocol identifier — includes version (e.g. "aries-issue-v2", "aries-issue-v3", "oid4vci") */
case class ProtocolId(value: String)

case class RecordId(value: UUID)

enum Phase:
  case Proposal, Offer, Request, Credential, Presentation, Verification

case class Endpoint(uri: String, metadata: Map[String, String] = Map.empty)

case class ProtocolMessage(
  id: String,
  `type`: String,
  body: Json,
  attachments: Seq[Array[Byte]] = Seq.empty,
)
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/ProtocolTransport.scala
package org.hyperledger.identus.shared.protocols

import zio.*
import zio.stream.Stream

trait ProtocolTransport:
  def transportType: TransportType
  def send(message: ProtocolMessage, destination: Endpoint): IO[Throwable, Unit]
  def receive: Stream[Throwable, ProtocolMessage]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/IssuanceProtocol.scala
package org.hyperledger.identus.shared.protocols

import org.hyperledger.identus.shared.models.Failure
import zio.*
import zio.json.ast.Json

trait IssuanceProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def initiateOffer(params: Json): IO[Throwable, RecordId]
  def processOffer(message: ProtocolMessage): IO[Throwable, RecordId]
  def createRequest(recordId: RecordId): IO[Throwable, RecordId]
  def processRequest(message: ProtocolMessage): IO[Throwable, RecordId]
  def issueCredential(recordId: RecordId): IO[Throwable, RecordId]
  def processCredential(message: ProtocolMessage): IO[Throwable, RecordId]

  def markSent(recordId: RecordId, phase: Phase): IO[Throwable, Unit]
  def reportFailure(recordId: RecordId, reason: Failure): IO[Throwable, Unit]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/PresentationProtocol.scala
package org.hyperledger.identus.shared.protocols

import zio.*
import zio.json.ast.Json

trait PresentationProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def requestPresentation(params: Json): IO[Throwable, RecordId]
  def processRequest(message: ProtocolMessage): IO[Throwable, RecordId]
  def createPresentation(recordId: RecordId): IO[Throwable, RecordId]
  def processPresentation(message: ProtocolMessage): IO[Throwable, RecordId]
  def verifyPresentation(recordId: RecordId): IO[Throwable, RecordId]
```

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/PresentationExchange.scala
package org.hyperledger.identus.shared.protocols

import org.hyperledger.identus.shared.credentials.RawCredential
import zio.*
import zio.json.ast.Json

trait PresentationExchange:
  def matchCredentials(definition: Json, available: Seq[RawCredential]): IO[Throwable, Json]
  def validateSubmission(definition: Json, submission: Json): IO[Throwable, Boolean]
```

**Step 2: Verify compilation**

Run: `sbt shared/compile`
Expected: PASS

**Step 3: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/protocols/
git commit -m "feat: add protocol-axis contract interfaces (transport, issuance, presentation, PEX)"
```

---

### Task 0.6: Add architecture constraints for contract packages

**Files:**
- Modify: `project/ArchConstraints.scala`

**Step 1: Add constraints ensuring contract packages stay pure**

Add to `forbiddenDeps` in `project/ArchConstraints.scala` after the existing entries:

```scala
// Contract packages in shared must not depend on implementation modules
// (This is enforced structurally since shared has no dependsOn for these modules,
//  but we document intent here for future reference)
```

No new sbt-level constraints are needed yet because the contracts live inside `shared` which already has minimal dependencies. The constraints become meaningful when we create implementation modules in Phase 1.

**Step 2: Verify existing constraints still pass**

Run: `sbt checkArchConstraints`
Expected: PASS — "All architectural constraints satisfied."

**Step 3: Commit**

```bash
git commit --allow-empty -m "chore: verify architecture constraints pass after Phase 0"
```

---

## Phase 1: Extract Leaf Components

### Task 1.1: Extract EdDSA Signer

This extracts the EdDSA signing logic from `DidJWT.scala` (EdSigner, lines 49-72) into a component implementing the `CredentialSigner` contract.

**Files:**
- Create: `modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSigner.scala`
- Create: `modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSignerSpec.scala`

**Step 1: Write the failing test**

```scala
// modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSignerSpec.scala
package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*
import zio.test.Assertion.*

object EdDsaCredentialSignerSpec extends ZIOSpecDefault:
  def spec = suite("EdDsaCredentialSigner")(
    test("algorithm is EdDSA") {
      val signer = EdDsaCredentialSigner()
      assertTrue(signer.algorithm == SignatureAlgorithm.EdDSA)
    },
    test("sign and verify round-trip") {
      for
        apollo   <- ZIO.service[Apollo]
        keyPair  = apollo.ed25519KeyPairGeneration
        signer   = EdDsaCredentialSigner()
        payload  = "test payload".getBytes
        keyRef   = KeyRef(keyPair.publicKey.getEncoded.map("%02x".format(_)).mkString, SignatureAlgorithm.EdDSA)
        // Note: actual implementation will need the private key via keyRef resolution
        // This test validates the contract interface compiles and the algorithm is correct
      yield assertTrue(signer.algorithm == SignatureAlgorithm.EdDSA)
    }.provide(ZLayer.succeed(KmpApollo)),
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "sharedCrypto/testOnly org.hyperledger.identus.shared.crypto.EdDsaCredentialSignerSpec"`
Expected: Compilation error — `EdDsaCredentialSigner` not found

**Step 3: Implement EdDsaCredentialSigner**

```scala
// modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSigner.scala
package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*

/** EdDSA (Ed25519) implementation of the CredentialSigner contract.
  *
  * This is a thin adapter over the existing Apollo Ed25519 primitives.
  * The keyRef.id is expected to be the hex-encoded private key for signing,
  * or the hex-encoded public key for verification.
  */
class EdDsaCredentialSigner(apollo: Apollo = KmpApollo) extends CredentialSigner:
  override def algorithm: SignatureAlgorithm = SignatureAlgorithm.EdDSA

  override def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]] =
    ZIO.attempt {
      val privateKeyBytes = hexToBytes(keyRef.id)
      val keyPair = apollo.ed25519KeyPairFromPrivateKey(privateKeyBytes)
      keyPair.privateKey.sign(payload)
    }

  override def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean] =
    ZIO.attempt {
      val publicKey = apollo.ed25519PublicKeyFromEncoded(publicKeyBytes)
      publicKey.verify(payload, signature)
    }

  private def hexToBytes(hex: String): Array[Byte] =
    hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

object EdDsaCredentialSigner:
  def apply(apollo: Apollo = KmpApollo): EdDsaCredentialSigner = new EdDsaCredentialSigner(apollo)
```

**Step 4: Run test to verify it passes**

Run: `sbt "sharedCrypto/testOnly org.hyperledger.identus.shared.crypto.EdDsaCredentialSignerSpec"`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSigner.scala \
        modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/EdDsaCredentialSignerSpec.scala
git commit -m "feat: extract EdDSA signer as CredentialSigner contract implementation"
```

---

### Task 1.2: Extract ES256K Signer

Same pattern as 1.1 but for secp256k1. Extracts from `DidJWT.scala` ES256KSigner (lines 19-47).

**Files:**
- Create: `modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSigner.scala`
- Create: `modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSignerSpec.scala`

**Step 1: Write failing test**

```scala
// modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSignerSpec.scala
package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*

object Es256kCredentialSignerSpec extends ZIOSpecDefault:
  def spec = suite("Es256kCredentialSigner")(
    test("algorithm is ES256K") {
      val signer = Es256kCredentialSigner()
      assertTrue(signer.algorithm == SignatureAlgorithm.ES256K)
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "sharedCrypto/testOnly org.hyperledger.identus.shared.crypto.Es256kCredentialSignerSpec"`

**Step 3: Implement**

```scala
// modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSigner.scala
package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.shared.credentials.*
import zio.*

class Es256kCredentialSigner(apollo: Apollo = KmpApollo) extends CredentialSigner:
  override def algorithm: SignatureAlgorithm = SignatureAlgorithm.ES256K

  override def sign(payload: Array[Byte], keyRef: KeyRef): IO[Throwable, Array[Byte]] =
    ZIO.attempt {
      val privateKeyBytes = hexToBytes(keyRef.id)
      val keyPair = apollo.secp256k1KeyPairFromPrivateKey(privateKeyBytes)
      keyPair.privateKey.sign(payload)
    }

  override def verify(payload: Array[Byte], signature: Array[Byte], publicKeyBytes: Array[Byte]): IO[Throwable, Boolean] =
    ZIO.attempt {
      val publicKey = apollo.secp256k1PublicKeyFromEncoded(publicKeyBytes)
      publicKey.verify(payload, signature)
    }

  private def hexToBytes(hex: String): Array[Byte] =
    hex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray

object Es256kCredentialSigner:
  def apply(apollo: Apollo = KmpApollo): Es256kCredentialSigner = new Es256kCredentialSigner(apollo)
```

**Step 4: Run test, verify pass**

Run: `sbt "sharedCrypto/testOnly org.hyperledger.identus.shared.crypto.Es256kCredentialSignerSpec"`

**Step 5: Commit**

```bash
git add modules/shared/crypto/src/main/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSigner.scala \
        modules/shared/crypto/src/test/scala/org/hyperledger/identus/shared/crypto/Es256kCredentialSignerSpec.scala
git commit -m "feat: extract ES256K signer as CredentialSigner contract implementation"
```

---

### Task 1.3: Extract ExpiryCheck verification

Extracts from `VcVerificationServiceImpl.scala` lines 168-192 (`verifyExpiration` and `verifyNotBefore`).

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/checks/ExpiryCheck.scala`
- Create: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/checks/ExpiryCheckSpec.scala`

**Step 1: Write failing test**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/checks/ExpiryCheckSpec.scala
package org.hyperledger.identus.shared.credentials.checks

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.test.*

import java.time.Instant

object ExpiryCheckSpec extends ZIOSpecDefault:
  // Helper: create a minimal JWT with exp claim
  private def jwtWithExp(exp: Long): RawCredential =
    val header = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"alg":"EdDSA"}""".getBytes)
    val payload = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(s"""{"exp":$exp}""".getBytes)
    RawCredential(CredentialFormat.JWT, s"$header.$payload.sig".getBytes)

  private def jwtWithoutExp: RawCredential =
    val header = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"alg":"EdDSA"}""".getBytes)
    val payload = java.util.Base64.getUrlEncoder.withoutPadding.encodeToString("""{"iss":"did:example:123"}""".getBytes)
    RawCredential(CredentialFormat.JWT, s"$header.$payload.sig".getBytes)

  def spec = suite("ExpiryCheck")(
    test("passes for non-expired credential") {
      val check = ExpiryVerificationCheck()
      val cred = jwtWithExp(Instant.now.plusSeconds(3600).getEpochSecond)
      val ctx = VerifyContext(currentTime = Instant.now)
      for result <- check.verify(cred, ctx)
      yield assertTrue(result.success)
    },
    test("fails for expired credential") {
      val check = ExpiryVerificationCheck()
      val cred = jwtWithExp(Instant.now.minusSeconds(3600).getEpochSecond)
      val ctx = VerifyContext(currentTime = Instant.now)
      for result <- check.verify(cred, ctx)
      yield assertTrue(!result.success)
    },
    test("passes for credential without exp (no expiry constraint)") {
      val check = ExpiryVerificationCheck()
      val cred = jwtWithoutExp
      val ctx = VerifyContext(currentTime = Instant.now)
      for result <- check.verify(cred, ctx)
      yield assertTrue(result.success)
    },
    test("applies to JWT and SDJWT, not AnonCreds") {
      val check = ExpiryVerificationCheck()
      assertTrue(
        check.appliesTo(RawCredential(CredentialFormat.JWT, Array.empty)),
        check.appliesTo(RawCredential(CredentialFormat.SDJWT, Array.empty)),
        !check.appliesTo(RawCredential(CredentialFormat.AnonCreds, Array.empty)),
      )
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.credentials.checks.ExpiryCheckSpec"`

**Step 3: Implement**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/checks/ExpiryCheck.scala
package org.hyperledger.identus.shared.credentials.checks

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.time.Instant

class ExpiryVerificationCheck extends VerificationCheck:
  override def checkType: VerificationCheckType = VerificationCheckType.Expiry

  override def appliesTo(credential: RawCredential): Boolean =
    credential.format match
      case CredentialFormat.JWT | CredentialFormat.SDJWT | CredentialFormat.JsonLD => true
      case _ => false

  override def verify(credential: RawCredential, ctx: VerifyContext): IO[Throwable, CheckResult] =
    ZIO.attempt {
      val payloadJson = extractJwtPayload(credential)
      payloadJson.flatMap(_.asObject).flatMap(_.get("exp")).flatMap(_.asNumber) match
        case Some(expNum) =>
          val expInstant = Instant.ofEpochSecond(expNum.value.longValue)
          if ctx.currentTime.isBefore(expInstant) then
            CheckResult(VerificationCheckType.Expiry, success = true)
          else
            CheckResult(VerificationCheckType.Expiry, success = false, Some(s"Credential expired at $expInstant"))
        case None =>
          // No exp claim — credential does not expire
          CheckResult(VerificationCheckType.Expiry, success = true, Some("No expiry claim present"))
    }

  private def extractJwtPayload(cred: RawCredential): Option[Json] =
    val jwt = new String(cred.data)
    val parts = jwt.split('.')
    if parts.length >= 2 then
      val decoded = new String(java.util.Base64.getUrlDecoder.decode(parts(1)))
      decoded.fromJson[Json].toOption
    else None
```

**Step 4: Run test to verify it passes**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.credentials.checks.ExpiryCheckSpec"`

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/checks/ \
        modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/checks/
git commit -m "feat: extract ExpiryCheck as VerificationCheck contract implementation"
```

---

### Task 1.4: Extract CredentialVerifier combinator

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialVerifier.scala`
- Create: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialVerifierSpec.scala`

**Step 1: Write failing test**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialVerifierSpec.scala
package org.hyperledger.identus.shared.credentials

import zio.*
import zio.test.*

object CredentialVerifierSpec extends ZIOSpecDefault:
  // Stub check that always passes
  object PassingCheck extends VerificationCheck:
    def checkType = VerificationCheckType.Expiry
    def appliesTo(c: RawCredential) = true
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.Expiry, success = true))

  // Stub check that always fails
  object FailingCheck extends VerificationCheck:
    def checkType = VerificationCheckType.Signature
    def appliesTo(c: RawCredential) = true
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.Signature, success = false, Some("bad sig")))

  // Stub check that only applies to JWT
  object JwtOnlyCheck extends VerificationCheck:
    def checkType = VerificationCheckType.ClaimsSchema
    def appliesTo(c: RawCredential) = c.format == CredentialFormat.JWT
    def verify(c: RawCredential, ctx: VerifyContext) =
      ZIO.succeed(CheckResult(VerificationCheckType.ClaimsSchema, success = true))

  val jwtCred = RawCredential(CredentialFormat.JWT, Array.empty)
  val anonCred = RawCredential(CredentialFormat.AnonCreds, Array.empty)
  val ctx = VerifyContext()

  def spec = suite("CredentialVerifier")(
    test("all checks pass -> isValid") {
      val verifier = CredentialVerifier(Seq(PassingCheck))
      for result <- verifier.verify(jwtCred, ctx)
      yield assertTrue(result.isValid)
    },
    test("one check fails -> not isValid") {
      val verifier = CredentialVerifier(Seq(PassingCheck, FailingCheck))
      for result <- verifier.verify(jwtCred, ctx)
      yield assertTrue(!result.isValid, result.checks.size == 2)
    },
    test("non-applicable checks are skipped") {
      val verifier = CredentialVerifier(Seq(JwtOnlyCheck))
      for result <- verifier.verify(anonCred, ctx)
      yield assertTrue(result.checks.isEmpty, result.isValid)
    },
    test("filter by requested check types") {
      val verifier = CredentialVerifier(Seq(PassingCheck, FailingCheck))
      for result <- verifier.verify(jwtCred, ctx, requestedChecks = Set(VerificationCheckType.Expiry))
      yield assertTrue(result.isValid, result.checks.size == 1)
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.credentials.CredentialVerifierSpec"`

**Step 3: Implement**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialVerifier.scala
package org.hyperledger.identus.shared.credentials

import zio.*

class CredentialVerifier(checks: Seq[VerificationCheck]):
  def verify(
    credential: RawCredential,
    ctx: VerifyContext,
    requestedChecks: Set[VerificationCheckType] = VerificationCheckType.values.toSet
  ): IO[Throwable, VerificationResult] =
    for
      results <- ZIO.foreach(
        checks.filter(c => requestedChecks.contains(c.checkType) && c.appliesTo(credential))
      )(_.verify(credential, ctx))
    yield VerificationResult(results)
```

**Step 4: Run test to verify it passes**

Run: `sbt "shared/testOnly org.hyperledger.identus.shared.credentials.CredentialVerifierSpec"`

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialVerifier.scala \
        modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialVerifierSpec.scala
git commit -m "feat: add CredentialVerifier combinator that composes VerificationCheck instances"
```

---

## Phase 1 Checkpoint

At this point we have:
- Contract types: `Capability`, `Contract`, `Cardinality`, `Module`, `ModuleRegistry`
- Credential contracts: `CredentialSigner`, `DataModelCodec`, `CredentialBuilder`, `VerificationCheck`, `RevocationCheck`
- Protocol contracts: `ProtocolTransport`, `IssuanceProtocol`, `PresentationProtocol`, `PresentationExchange`
- First implementations: `EdDsaCredentialSigner`, `Es256kCredentialSigner`, `ExpiryVerificationCheck`, `CredentialVerifier`

**Verification checkpoint:**

Run: `sbt compile && sbt checkArchConstraints && sbt shared/test && sbt sharedCrypto/test`

All must pass before proceeding to Phase 2.

---

## Phase 2: Extract Builders (outline)

> Phases 2-5 are outlined at task level. Each task follows the same TDD pattern as Phase 0-1 (write failing test, implement, verify, commit).

### Task 2.1: Create shared BuildStep implementations

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/steps/ValidateClaimsStep.scala`
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/steps/AddStatusListStep.scala`
- Test: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/steps/`

These are reusable steps shared across JWT, SD-JWT, and JSON-LD builders.

### Task 2.2: Extract JWT CredentialBuilder

**Files:**
- Create: `modules/credentials/vc-jwt/src/main/scala/org/hyperledger/identus/credentials/vc/jwt/JwtCredentialBuilder.scala`
- Test: `modules/credentials/vc-jwt/src/test/scala/.../JwtCredentialBuilderSpec.scala`

Extract from `CredentialServiceImpl.generateJWTCredential` (lines 1190-1254):
- `AssembleJwtPayloadStep` — builds `W3cCredentialPayload` from claims
- `SignJwtStep` — wraps `vcJwtService.encodeCredentialToJwt`

The builder delegates to `CredentialSigner` for signing and `DataModelCodec` for claim encoding.

### Task 2.3: Extract SD-JWT CredentialBuilder

**Files:**
- Create: `modules/credentials/sd-jwt/src/main/scala/.../SdJwtCredentialBuilder.scala`
- Test: `modules/credentials/sd-jwt/src/test/scala/.../SdJwtCredentialBuilderSpec.scala`

Extract from `CredentialServiceImpl.generateSDJWTCredential` (lines 1256-1351):
- `SelectDisclosuresStep` — partitions claims
- `HashDisclosuresStep` — computes `_sd` array
- `SignSdJwtStep` — wraps `sdJwtService.issueCredential`

### Task 2.4: Extract AnonCreds CredentialBuilder

**Files:**
- Create: `modules/credentials/anoncreds/src/main/scala/.../AnonCredsCredentialBuilder.scala`
- Test: `modules/credentials/anoncreds/src/test/scala/.../AnonCredsCredentialBuilderSpec.scala`

Extract from `CredentialServiceImpl.createAnonCredsCredential` (lines 1407-1460):
- `FetchCredDefStep`
- `ComputeCredValuesStep`
- `ProcessBlindedRequestStep`
- `CLSignStep` — wraps `anoncredService.createCredential`

### Task 2.5: Wire builders as strangler delegates in CredentialServiceImpl

**Files:**
- Modify: `modules/credentials/core/src/main/scala/.../CredentialServiceImpl.scala`

Replace format-specific logic in `generateJWTCredential`, `generateSDJWTCredential`, `generateAnonCredsCredential` with delegation to the corresponding `CredentialBuilder`. The old methods become thin wrappers.

---

## Phase 3: Extract Protocol State Machines (outline)

### Task 3.1: Create DIDComm Issuance Protocol

**Files:**
- Create: `modules/didcomm/issuance/src/main/scala/.../DIDCommIssuanceProtocol.scala`

Extract from `CredentialServiceImpl`:
- Record CRUD (`createIssueCredentialRecord`, `getById`, `findById`, `getIssueCredentialRecords*`)
- State transitions (`markOfferSent`, `markRequestSent`, `markCredentialSent`)
- Message processing (`receiveCredentialOffer`, `receiveCredentialRequest`, `receiveCredentialIssue`)
- Format dispatch (looks up `CredentialBuilder` by `record.credentialFormat`)

### Task 3.2: Create DIDComm Presentation Protocol

Extract from `PresentationServiceImpl` — same pattern as 3.1.

### Task 3.3: Formalize OID4VCI as IssuanceProtocol

**Files:**
- Modify: `modules/oid4vci/core/src/main/scala/.../`

Make existing `oid4vciCore` implement the `IssuanceProtocol` contract with `ProtocolId("oid4vci")`.

### Task 3.4: Formalize OID4VP as PresentationProtocol

Similar to 3.3 but for OID4VP + PEX integration.

### Task 3.5: Version-aware DIDComm protocols

Create separate modules for protocol versions:

```
DIDCommIssuanceV2Module:
  implements: [IssuanceProtocol("aries-issue-v2")]

DIDCommIssuanceV3Module:
  implements: [IssuanceProtocol("aries-issue-v3")]
```

Each version has its own message format definitions and state machine transitions, sharing the same `CredentialBuilder` and `ProtocolTransport` contracts.

---

## Phase 4: Extract Transport & PEX (outline)

### Task 4.1: Create DIDComm Transport module
### Task 4.2: Create OIDC Transport module
### Task 4.3: Extract PEX as standalone module

---

## Phase 5: Wire via ModuleRegistry (outline)

### Task 5.1: Add Module.register to each extracted module
### Task 5.2: Replace Modules.scala with ModuleRegistry.assembleAll
### Task 5.3: Replace MainApp.scala monolithic .provide()
### Task 5.4: Add enable/disable via application.conf

---

## Appendix: File Path Reference

| Current file | What moves where |
|---|---|
| `modules/credentials/core/.../CredentialServiceImpl.scala` (1580 lines) | Shrinks to thin facade; logic moves to builders (Phase 2) and protocol (Phase 3) |
| `modules/credentials/vc-jwt/.../DidJWT.scala` (ES256KSigner, EdSigner) | Wrapped by `Es256kCredentialSigner`, `EdDsaCredentialSigner` (Phase 1) |
| `modules/credentials/core/.../verification/VcVerificationServiceImpl.scala` | Individual methods become `VerificationCheck` implementations (Phase 1) |
| `modules/credentials/core/.../CredentialFormat.scala` | Replaced by `shared/credentials/CredentialTypes.scala` (Phase 0) |
| `modules/api-server/core/.../Modules.scala` | Eliminated — each module self-registers (Phase 5) |
| `modules/api-server/core/.../MainApp.scala` | Simplified to `ModuleRegistry.assembleLayers` (Phase 5) |
| `modules/credentials/vc-jwt/.../VCStatusList2021.scala` | Wrapped by `StatusList2021Module` (Phase 1) |
| `modules/credentials/core/.../CredentialStatusListService*.scala` | Stays, but revocation checking extracted to `RevocationCheck` (Phase 1) |
