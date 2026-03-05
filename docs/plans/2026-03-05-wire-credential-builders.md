# Wire Credential Builders via ModuleRegistry Layers — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire the three credential builder modules into the runtime so `CredentialServiceImpl` delegates credential building to module-provided `CredentialBuilder` instances resolved via `ModuleRegistry`.

**Architecture:** Extend `Module` trait with `type Service` and `def layer: TaskLayer[Service]`. Create a `CredentialBuilderRegistry` that maps `CredentialFormat → CredentialBuilder`. `ModuleRegistry` assembles the registry from builder modules. `CredentialServiceImpl` receives the registry and delegates `generate*Credential` methods to it.

**Tech Stack:** Scala 3, ZIO 2 (ZLayer, TaskLayer), sbt multi-module build

**Design doc:** `docs/plans/2026-03-05-wire-credential-builders-design.md`

---

### Task 1: Extend Module trait with `type Service` and `def layer`

**Files:**
- Modify: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala`
- Test: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala`

**Step 1: Update Module trait**

Add `type Service` and `def layer` to the `Module` trait:

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala
package org.hyperledger.identus.shared.models

import zio.*

case class ModuleId(value: String)

case class SemVer(major: Int, minor: Int, patch: Int):
  override def toString: String = s"$major.$minor.$patch"

trait Module:
  type Config
  type Service

  def id: ModuleId
  def version: SemVer

  def implements: Set[Capability]
  def requires: Set[Capability]

  def defaultConfig: Config
  def enabled(config: Config): Boolean
  def layer: TaskLayer[Service]
```

**Step 2: Update ModuleRegistrySpec test modules**

The `SimpleModule` trait in `ModuleRegistrySpec.scala` (line 9) needs `type Service = Unit` and `def layer = ZLayer.empty`:

```scala
trait SimpleModule extends Module:
  type Config = Unit
  type Service = Unit
  def defaultConfig = ()
  def enabled(config: Unit) = true
  def version = SemVer(1, 0, 0)
  def layer = ZLayer.empty
```

**Step 3: Verify compile**

Run: `sbt shared/compile`
Expected: Success

**Step 4: Verify tests**

Run: `sbt shared/test`
Expected: All existing tests pass

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/Module.scala
git add modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala
git commit -m "feat: extend Module trait with type Service and def layer"
```

---

### Task 2: Update all non-builder Module declarations with stub layer

All module objects that don't provide a `CredentialBuilder` need `type Service = Unit` and `def layer = ZLayer.empty`.

**Files:**
- Modify: `modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/codec/Vcdm11CodecModule.scala`
- Modify: `modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/protocol/DIDCommIssuanceModule.scala`
- Modify: `modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/protocol/DIDCommPresentationModule.scala`
- Modify: `modules/oid4vci/core/src/main/scala/org/hyperledger/identus/oid4vci/OidcIssuanceModule.scala`
- Modify: `modules/oid4vci/core/src/main/scala/org/hyperledger/identus/oid4vci/OidcPresentationModule.scala`
- Modify: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/db/PostgresPersistenceModule.scala`
- Modify: `modules/shared/persistence-sqlite/src/main/scala/org/hyperledger/identus/shared/db/sqlite/SqlitePersistenceModule.scala`

**Step 1: Add to each module object**

For each of the 7 modules above, add these two lines after the existing `type Config = Unit`:

```scala
  type Service = Unit
```

And at the end of the object, add:

```scala
  def layer: TaskLayer[Unit] = ZLayer.empty
```

Each file also needs `import zio.*` added (they currently only import from `org.hyperledger.identus.shared.models.*`).

Example for `Vcdm11CodecModule.scala`:

```scala
package org.hyperledger.identus.credentials.core.codec

import org.hyperledger.identus.shared.models.*
import zio.*

object Vcdm11CodecModule extends Module:
  type Config = Unit
  type Service = Unit

  val id: ModuleId = ModuleId("vcdm-1.1-codec")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("DataModelCodec", Some("vcdm-1.1")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer: TaskLayer[Unit] = ZLayer.empty
```

Apply the same pattern to all 7 modules.

**Step 2: Verify compile**

Run: `sbt shared/compile credentialsCore/compile oid4vciCore/compile`
Expected: Success

**Step 3: Commit**

```bash
git add modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/codec/Vcdm11CodecModule.scala
git add modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/protocol/DIDCommIssuanceModule.scala
git add modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/protocol/DIDCommPresentationModule.scala
git add modules/oid4vci/core/src/main/scala/org/hyperledger/identus/oid4vci/OidcIssuanceModule.scala
git add modules/oid4vci/core/src/main/scala/org/hyperledger/identus/oid4vci/OidcPresentationModule.scala
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/db/PostgresPersistenceModule.scala
git add modules/shared/persistence-sqlite/src/main/scala/org/hyperledger/identus/shared/db/sqlite/SqlitePersistenceModule.scala
git commit -m "chore: add stub Service type and layer to non-builder modules"
```

---

### Task 3: Update builder Module declarations with `CredentialBuilder` layer

**Files:**
- Modify: `modules/credentials/vc-jwt/src/main/scala/org/hyperledger/identus/credentials/vc/jwt/JwtBuilderModule.scala`
- Modify: `modules/credentials/sd-jwt/src/main/scala/org/hyperledger/identus/credentials/sdjwt/SdJwtBuilderModule.scala`
- Modify: `modules/credentials/anoncreds/src/main/scala/org/hyperledger/identus/credentials/anoncreds/AnonCredsBuilderModule.scala`

**Step 1: Update JwtBuilderModule**

`JwtCredentialBuilder` requires `DataModelCodec` and `CredentialSigner`. The module's layer should produce a `CredentialBuilder` from these dependencies:

```scala
package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.shared.credentials.{CredentialBuilder, CredentialSigner, DataModelCodec}
import org.hyperledger.identus.shared.models.*
import zio.*

object JwtBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("jwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("jwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true

  def layer: TaskLayer[CredentialBuilder] =
    ZLayer.fromZIO {
      for
        codec <- ZIO.service[DataModelCodec]
        signer <- ZIO.service[CredentialSigner]
      yield JwtCredentialBuilder(codec, signer)
    }.asInstanceOf[TaskLayer[CredentialBuilder]]
```

**Note:** The `asInstanceOf` cast is needed because `ZLayer.fromZIO` infers `ZLayer[DataModelCodec & CredentialSigner, Nothing, CredentialBuilder]` but `TaskLayer[CredentialBuilder]` is `ZLayer[Any, Throwable, CredentialBuilder]`. We'll address this properly — the layer will be provided its dependencies at assembly time, not from the environment. Instead, use a different approach:

```scala
  def layer: TaskLayer[CredentialBuilder] =
    // Dependencies are provided at assembly time by CredentialBuilderRegistry
    ZLayer.fail(new RuntimeException("JwtBuilderModule.layer requires DataModelCodec & CredentialSigner"))
```

Actually, the design says `Module.layer` returns `TaskLayer[Service]` which is `ZLayer[Any, Throwable, Service]`. But the builders need dependencies. The correct pattern is for the layer to receive its dependencies from the ZIO environment, and the assembly code provides them. Let's use `RLayer` instead:

**Revised approach:** Change `Module` to use `RLayer` with an existential dependency type, or provide the dependencies at construction time. Looking at the existing builder constructors:
- `JwtCredentialBuilder(codec: DataModelCodec, signer: CredentialSigner)`
- `SdJwtCredentialBuilder(sdJwtService: SDJwtService, keyResolver: IssuerKeyResolver)`
- `AnonCredsCredentialBuilder(anoncredService: AnoncredService, contextResolver: Resolver)`

These take their dependencies as constructor parameters. The module's `layer` should construct the builder using these params. Since the params come from the ZIO environment at wiring time, we need a layer that requires them. But `TaskLayer[Service]` has `Any` as its input.

**Resolution:** The builder registry assembly will manually provide dependencies to each module's layer. For now, use `ZLayer[Any, Throwable, Service]` but actually make it `ZLayer[DataModelCodec & CredentialSigner, Throwable, CredentialBuilder]` internally, and the registry assembly code will provide the dependencies. We need a way for the module to declare what its layer needs.

**Simpler approach per the design:** Keep `TaskLayer[Service]` but have the module produce a layer that doesn't need external deps — instead, the assembly code provides a pre-wired layer. We'll use a `layerFor` method that takes deps and returns `TaskLayer[Service]`:

No — let's keep it simple. The Module trait stays with `def layer: TaskLayer[Service]`. For builder modules that need deps, we add a separate method that accepts deps and returns the builder. The `CredentialBuilderRegistry` factory method handles construction directly.

**Final approach for Task 3:**

```scala
// JwtBuilderModule.scala
package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.shared.credentials.{CredentialBuilder, CredentialSigner, DataModelCodec}
import org.hyperledger.identus.shared.models.*
import zio.*

object JwtBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("jwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("jwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true

  /** Standalone layer — requires DataModelCodec & CredentialSigner in environment */
  def layer: TaskLayer[CredentialBuilder] =
    ZLayer {
      for
        codec <- ZIO.service[DataModelCodec]
        signer <- ZIO.service[CredentialSigner]
      yield JwtCredentialBuilder(codec, signer)
    }
```

Wait, `ZLayer { ... }` with `ZIO.service[DataModelCodec]` produces `ZLayer[DataModelCodec & CredentialSigner, Nothing, CredentialBuilder]`, not `TaskLayer[CredentialBuilder]` which is `ZLayer[Any, Throwable, CredentialBuilder]`. This won't compile.

**Correct solution:** Change the Module trait to use `URLayer` or make the return type more flexible. Or — simplest — the builders are constructed by the registry, not by individual module layers. The `layer` for non-builder modules remains `ZLayer.empty`. For builder modules, the registry constructs builders directly.

Let me re-read the design doc to resolve this...

The design says:
```
def layer: TaskLayer[Service]         // NEW
```
And: "Each module's `layer` produces its service instance."

And for the registry assembly:
```
def assembleBuilderRegistry: Task[CredentialBuilderRegistry] =
  val builderModules = modules.filter(_.implements.exists(_.contract == "CredentialBuilder"))
  // Instantiate each builder module's layer, collect into registry map
```

The registry needs to `provide` each layer with its required dependencies. Since we know what each builder needs, we can do this at the registry level. But the `TaskLayer[Service]` type signature says the layer takes `Any` as input.

**The pragmatic solution:** Builder module objects expose a `make` factory method that accepts deps and returns `Task[CredentialBuilder]`, while `layer` remains for ZIO layer composition later. For now, use `make`:

Actually, the simplest thing: don't force `TaskLayer[Service]`. Use `ZLayer[?, Throwable, Service]` with existential input type. Or just use `Any` and document that the layer must be provided its deps at assembly.

**OK, the truly simplest approach:** For the initial wiring, `CredentialBuilderRegistry.fromModules` constructs builders directly using their constructors, not via Module.layer. The `layer` field on builder modules returns a placeholder that's not used yet. This matches "scope: credential builders only" and avoids over-engineering the layer type system before we need it for protocol adapters.

Let me revise this task:

**Step 1: Update JwtBuilderModule**

```scala
package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.shared.credentials.CredentialBuilder
import org.hyperledger.identus.shared.models.*
import zio.*

object JwtBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("jwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("jwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer: TaskLayer[CredentialBuilder] = ZLayer.empty
```

**Step 2: Update SdJwtBuilderModule**

```scala
package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.credentials.CredentialBuilder
import org.hyperledger.identus.shared.models.*
import zio.*

object SdJwtBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("sdjwt-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("sdjwt")),
  )

  val requires: Set[Capability] = Set(
    Capability("DataModelCodec"),
  )

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer: TaskLayer[CredentialBuilder] = ZLayer.empty
```

**Step 3: Update AnonCredsBuilderModule**

```scala
package org.hyperledger.identus.credentials.anoncreds

import org.hyperledger.identus.shared.credentials.CredentialBuilder
import org.hyperledger.identus.shared.models.*
import zio.*

object AnonCredsBuilderModule extends Module:
  type Config = Unit
  type Service = CredentialBuilder

  val id: ModuleId = ModuleId("anoncreds-credential-builder")
  val version: SemVer = SemVer(0, 1, 0)

  val implements: Set[Capability] = Set(
    Capability("CredentialBuilder", Some("anoncreds")),
  )

  val requires: Set[Capability] = Set.empty

  def defaultConfig: Unit = ()
  def enabled(config: Unit): Boolean = true
  def layer: TaskLayer[CredentialBuilder] = ZLayer.empty
```

**Note:** `ZLayer.empty` for `TaskLayer[CredentialBuilder]` won't type-check — `ZLayer.empty` is `ZLayer[Any, Nothing, Any]`. Use `ZLayer.succeed(null.asInstanceOf[CredentialBuilder])` as a stub, or better yet define a dummy. Actually, the cleanest way:

```scala
  def layer: TaskLayer[CredentialBuilder] =
    ZLayer.fromZIO(ZIO.fail(new RuntimeException(s"${id.value}: use CredentialBuilderRegistry instead")))
```

This makes the type work (`ZLayer[Any, Throwable, CredentialBuilder]`) and signals clearly that the layer isn't meant to be used standalone.

**Step 4: Verify compile**

Run: `sbt credentialsVcJWT/compile credentialsSDJWT/compile credentialsAnoncreds/compile`
Expected: Success

**Step 5: Commit**

```bash
git add modules/credentials/vc-jwt/src/main/scala/org/hyperledger/identus/credentials/vc/jwt/JwtBuilderModule.scala
git add modules/credentials/sd-jwt/src/main/scala/org/hyperledger/identus/credentials/sdjwt/SdJwtBuilderModule.scala
git add modules/credentials/anoncreds/src/main/scala/org/hyperledger/identus/credentials/anoncreds/AnonCredsBuilderModule.scala
git commit -m "feat: add CredentialBuilder Service type to builder module declarations"
```

---

### Task 4: Create CredentialBuilderRegistry

**Files:**
- Create: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistry.scala`
- Test: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistrySpec.scala`

**Step 1: Write the test**

```scala
// modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistrySpec.scala
package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

object CredentialBuilderRegistrySpec extends ZIOSpecDefault:

  val stubJwtBuilder: CredentialBuilder = new CredentialBuilder:
    def format = CredentialFormat.JWT
    def supportedDataModels = Set(DataModelType.VCDM_1_1)
    def buildCredential(ctx: BuildContext) =
      ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.JWT, "jwt".getBytes), Json.Obj()))
    def steps = Seq.empty

  val stubSdJwtBuilder: CredentialBuilder = new CredentialBuilder:
    def format = CredentialFormat.SDJWT
    def supportedDataModels = Set(DataModelType.VCDM_1_1)
    def buildCredential(ctx: BuildContext) =
      ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.SDJWT, "sdjwt".getBytes), Json.Obj()))
    def steps = Seq.empty

  def spec = suite("CredentialBuilderRegistry")(
    test("resolves builder by format") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
        CredentialFormat.SDJWT -> stubSdJwtBuilder,
      ))
      assertTrue(
        registry.get(CredentialFormat.JWT).contains(stubJwtBuilder),
        registry.get(CredentialFormat.SDJWT).contains(stubSdJwtBuilder),
      )
    },
    test("returns None for unregistered format") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
      ))
      assertTrue(registry.get(CredentialFormat.AnonCreds).isEmpty)
    },
    test("formats returns all registered formats") {
      val registry = CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> stubJwtBuilder,
        CredentialFormat.SDJWT -> stubSdJwtBuilder,
      ))
      assertTrue(registry.formats == Set(CredentialFormat.JWT, CredentialFormat.SDJWT))
    },
    test("empty registry returns None for all formats") {
      val registry = CredentialBuilderRegistry.empty
      assertTrue(
        registry.get(CredentialFormat.JWT).isEmpty,
        registry.get(CredentialFormat.SDJWT).isEmpty,
        registry.get(CredentialFormat.AnonCreds).isEmpty,
      )
    },
  )
```

**Step 2: Run test to verify it fails**

Run: `sbt shared/testOnly *CredentialBuilderRegistrySpec`
Expected: FAIL — `CredentialBuilderRegistry` not found

**Step 3: Write CredentialBuilderRegistry**

```scala
// modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistry.scala
package org.hyperledger.identus.shared.credentials

case class CredentialBuilderRegistry(
    builders: Map[CredentialFormat, CredentialBuilder]
):
  def get(format: CredentialFormat): Option[CredentialBuilder] =
    builders.get(format)

  def formats: Set[CredentialFormat] = builders.keySet

object CredentialBuilderRegistry:
  val empty: CredentialBuilderRegistry = CredentialBuilderRegistry(Map.empty)
```

**Step 4: Run test to verify it passes**

Run: `sbt shared/testOnly *CredentialBuilderRegistrySpec`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistry.scala
git add modules/shared/core/src/test/scala/org/hyperledger/identus/shared/credentials/CredentialBuilderRegistrySpec.scala
git commit -m "feat: add CredentialBuilderRegistry with typed format lookup"
```

---

### Task 5: Add `assembleBuilderRegistry` to ModuleRegistry

**Files:**
- Modify: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala`
- Test: `modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala`

**Step 1: Write the test**

Add to `ModuleRegistrySpec.scala` (after the existing tests):

```scala
    test("assembleBuilderRegistry collects builder modules by format") {
      import org.hyperledger.identus.shared.credentials.*
      import zio.json.ast.Json

      object JwtBuilderMod extends SimpleModule:
        override type Service = CredentialBuilder
        val id = ModuleId("jwt-builder")
        val implements = Set(Capability("CredentialBuilder", Some("jwt")))
        val requires = Set.empty[Capability]
        override def layer = ZLayer.succeed[CredentialBuilder](new CredentialBuilder:
          def format = CredentialFormat.JWT
          def supportedDataModels = Set(DataModelType.VCDM_1_1)
          def buildCredential(ctx: BuildContext) =
            ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.JWT, "jwt".getBytes), Json.Obj()))
          def steps = Seq.empty
        )

      object SdJwtBuilderMod extends SimpleModule:
        override type Service = CredentialBuilder
        val id = ModuleId("sdjwt-builder")
        val implements = Set(Capability("CredentialBuilder", Some("sdjwt")))
        val requires = Set.empty[Capability]
        override def layer = ZLayer.succeed[CredentialBuilder](new CredentialBuilder:
          def format = CredentialFormat.SDJWT
          def supportedDataModels = Set(DataModelType.VCDM_1_1)
          def buildCredential(ctx: BuildContext) =
            ZIO.succeed(BuiltCredential(RawCredential(CredentialFormat.SDJWT, "sdjwt".getBytes), Json.Obj()))
          def steps = Seq.empty
        )

      val registry = ModuleRegistry(Seq(ProviderModule, JwtBuilderMod, SdJwtBuilderMod))
      for
        builderRegistry <- registry.assembleBuilderRegistry
      yield assertTrue(
        builderRegistry.formats == Set(CredentialFormat.JWT, CredentialFormat.SDJWT),
        builderRegistry.get(CredentialFormat.JWT).map(_.format) == Some(CredentialFormat.JWT),
        builderRegistry.get(CredentialFormat.SDJWT).map(_.format) == Some(CredentialFormat.SDJWT),
        builderRegistry.get(CredentialFormat.AnonCreds).isEmpty,
      )
    },
```

**Step 2: Run test to verify it fails**

Run: `sbt shared/testOnly *ModuleRegistrySpec`
Expected: FAIL — `assembleBuilderRegistry` not found

**Step 3: Implement assembleBuilderRegistry**

In `ModuleRegistry.scala`, add the method to the `ModuleRegistry` class:

```scala
import org.hyperledger.identus.shared.credentials.{CredentialBuilder, CredentialBuilderRegistry}

class ModuleRegistry(val modules: Seq[Module]):

  // ... existing methods ...

  def assembleBuilderRegistry: Task[CredentialBuilderRegistry] =
    val builderModules = modules.filter(_.implements.exists(_.contract == "CredentialBuilder"))
    val builderEffects = builderModules.map { m =>
      val typedModule = m.asInstanceOf[Module { type Service = CredentialBuilder }]
      typedModule.layer.build.map(env => env.get[CredentialBuilder])
        .map(builder => builder.format -> builder)
    }
    ZIO.collectAll(builderEffects.map(_.provideSomeLayer(zio.Scope.default)))
      .map(pairs => CredentialBuilderRegistry(pairs.toMap))
```

**Note:** The `asInstanceOf` cast is needed because Scala's path-dependent types don't let us generically extract `Service = CredentialBuilder` from `Module`. This is safe because we filter on `CredentialBuilder` capability.

The `Scope.default` is needed because `ZLayer.build` returns a `ZIO[Scope, ...]`.

**Step 4: Run test to verify it passes**

Run: `sbt shared/testOnly *ModuleRegistrySpec`
Expected: PASS

**Step 5: Commit**

```bash
git add modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala
git add modules/shared/core/src/test/scala/org/hyperledger/identus/shared/models/ModuleRegistrySpec.scala
git commit -m "feat: add assembleBuilderRegistry to ModuleRegistry"
```

---

### Task 6: Wire CredentialBuilderRegistry into AllModules and CloudAgentApp

**Files:**
- Modify: `modules/api-server/core/src/main/scala/org/hyperledger/identus/server/AllModules.scala`
- Modify: `modules/api-server/core/src/main/scala/org/hyperledger/identus/server/CloudAgentApp.scala`

**Step 1: Add assembleBuilderRegistry to AllModules**

```scala
// AllModules.scala — add method
object AllModules:

  val all: Seq[Module] = Seq(
    // ... unchanged ...
  )

  def registry(disabled: Set[ModuleId] = Set.empty): ModuleRegistry =
    ModuleRegistry.fromAll(all, disabled)

  def builderRegistry(registry: ModuleRegistry): Task[CredentialBuilderRegistry] =
    registry.assembleBuilderRegistry
```

Add import: `import org.hyperledger.identus.shared.credentials.CredentialBuilderRegistry`

**Step 2: Add to CloudAgentApp.validateModuleRegistry**

Update `validateModuleRegistry` to also assemble and log the builder registry:

```scala
  private def validateModuleRegistry: Task[Unit] =
    val registry = AllModules.registry()
    for
      _ <- ZIO.log(s"Plugin architecture: ${registry.report}")
      _ <- registry.validateDependencies.mapError(e => new Exception(e.message))
      _ <- ZIO.log("Module dependency graph validated successfully")
      builderRegistry <- registry.assembleBuilderRegistry
      _ <- ZIO.log(s"CredentialBuilderRegistry: ${builderRegistry.formats.mkString(", ")}")
    yield ()
```

**Note:** At this point we log the registry but don't provide it as a ZLayer yet. That comes in Task 8 when CredentialServiceImpl is refactored.

**Step 3: Verify compile**

Run: `sbt apiServer/compile`
Expected: Success

**Step 4: Commit**

```bash
git add modules/api-server/core/src/main/scala/org/hyperledger/identus/server/AllModules.scala
git add modules/api-server/core/src/main/scala/org/hyperledger/identus/server/CloudAgentApp.scala
git commit -m "feat: wire CredentialBuilderRegistry assembly into startup"
```

---

### Task 7: Update builder module layers to produce real builders

Now that the registry infrastructure is in place, update the builder module `layer` methods to actually produce `CredentialBuilder` instances. Since `TaskLayer[Service]` requires `Any` input, the builders must be constructed without ZIO service dependencies. Each builder module will construct its builder using a factory method that receives deps.

**Files:**
- Modify: `modules/credentials/vc-jwt/src/main/scala/org/hyperledger/identus/credentials/vc/jwt/JwtBuilderModule.scala`
- Modify: `modules/credentials/sd-jwt/src/main/scala/org/hyperledger/identus/credentials/sdjwt/SdJwtBuilderModule.scala`
- Modify: `modules/credentials/anoncreds/src/main/scala/org/hyperledger/identus/credentials/anoncreds/AnonCredsBuilderModule.scala`
- Modify: `modules/shared/core/src/main/scala/org/hyperledger/identus/shared/models/ModuleRegistry.scala` (if assembly approach changes)

**Design decision:** The `TaskLayer[Service]` type is `ZLayer[Any, Throwable, Service]`. Builders need deps (`DataModelCodec`, `CredentialSigner`, `SDJwtService`, etc.). Two options:

**Option A:** Keep `layer` as placeholder; `assembleBuilderRegistry` constructs builders via explicit factory methods on each module.

**Option B:** Relax the type to `ZLayer[Any, Throwable, Any]` and have the registry provide deps when building.

**Chosen: Option A** — simplest, no type gymnastics. Each builder module gets a `make` factory.

**Step 1: Add factory methods to builder modules**

For `JwtBuilderModule`:
```scala
  def make(codec: DataModelCodec, signer: CredentialSigner): CredentialBuilder =
    JwtCredentialBuilder(codec, signer)
```

For `SdJwtBuilderModule`:
```scala
  def make(sdJwtService: SDJwtService, keyResolver: SdJwtCredentialBuilder.IssuerKeyResolver): CredentialBuilder =
    SdJwtCredentialBuilder(sdJwtService, keyResolver)
```

For `AnonCredsBuilderModule`:
```scala
  def make(
      anoncredService: AnoncredService,
      contextResolver: AnonCredsCredentialBuilder.CredentialContext.Resolver,
  ): CredentialBuilder =
    AnonCredsCredentialBuilder(anoncredService, contextResolver)
```

**Step 2: Update assembleBuilderRegistry**

The registry assembly can't use `make` directly (it doesn't know about concrete module types). Instead, keep the current approach from Task 5 but have the builder modules provide real layers.

**Revised approach:** Actually, the simplest viable approach is to NOT use `Module.layer` for builder construction at all in this iteration. Instead, create a `CredentialBuilderRegistry.layer` ZLayer that takes builder dependencies from the environment and constructs the registry directly:

```scala
// CredentialBuilderRegistry.scala — add companion method
object CredentialBuilderRegistry:
  val empty: CredentialBuilderRegistry = CredentialBuilderRegistry(Map.empty)

  /** ZLayer that constructs a registry from available builder dependencies.
    * Dependencies are the same ones CredentialServiceImpl currently uses.
    */
  val layer: URLayer[VcJwtService & SDJwtService & AnoncredService & DataModelCodec & CredentialSigner, CredentialBuilderRegistry] =
    ZLayer.fromZIO {
      for
        // ... build all 3 builders from their deps ...
      yield CredentialBuilderRegistry(Map(...))
    }
```

**Problem:** `CredentialBuilderRegistry` is in `shared/core` which can't depend on `credentialsVcJWT`, `credentialsSDJWT`, or `credentialsAnoncreds`. The layer can't be in `shared/core`.

**Final approach:** Put the `CredentialBuilderRegistry.layer` in `AllModules.scala` (in `apiServer/core`) which has access to all modules:

```scala
// AllModules.scala
def builderRegistryLayer: TaskLayer[CredentialBuilderRegistry] =
  ZLayer.fromZIO {
    val registry = AllModules.registry()
    registry.assembleBuilderRegistry
  }
```

And the `assembleBuilderRegistry` in `ModuleRegistry` uses the test approach from Task 5 (cast + build module layers).

For the 3 builder modules, update their `layer` to produce real instances when their deps are pre-wired:

```scala
// JwtBuilderModule — update layer to use ZLayer.succeed with a pre-built instance
// This requires the instance to be built BEFORE the layer is evaluated.
```

This is getting circular. Let me simplify radically:

**SIMPLEST APPROACH:** Skip `Module.layer` for now. The `CredentialBuilderRegistry` layer lives in `apiServer/core` (where `AllModules.scala` is) and constructs builders directly from ZIO services already in the environment:

```scala
// modules/api-server/core/src/main/scala/org/hyperledger/identus/server/CredentialBuilderRegistryLive.scala
package org.hyperledger.identus.server

import org.hyperledger.identus.credentials.anoncreds.{AnonCredsCredentialBuilder, AnoncredService}
import org.hyperledger.identus.credentials.sdjwt.{SdJwtCredentialBuilder, SDJwtService}
import org.hyperledger.identus.credentials.vc.jwt.JwtCredentialBuilder
import org.hyperledger.identus.shared.credentials.*
import zio.*

object CredentialBuilderRegistryLive:

  val layer: URLayer[DataModelCodec & CredentialSigner & SDJwtService & SdJwtCredentialBuilder.IssuerKeyResolver & AnoncredService & AnonCredsCredentialBuilder.CredentialContext.Resolver, CredentialBuilderRegistry] =
    ZLayer.fromZIO {
      for
        codec <- ZIO.service[DataModelCodec]
        signer <- ZIO.service[CredentialSigner]
        sdJwtService <- ZIO.service[SDJwtService]
        sdJwtKeyResolver <- ZIO.service[SdJwtCredentialBuilder.IssuerKeyResolver]
        anoncredService <- ZIO.service[AnoncredService]
        contextResolver <- ZIO.service[AnonCredsCredentialBuilder.CredentialContext.Resolver]
      yield CredentialBuilderRegistry(Map(
        CredentialFormat.JWT -> JwtCredentialBuilder(codec, signer),
        CredentialFormat.SDJWT -> SdJwtCredentialBuilder(sdJwtService, sdJwtKeyResolver),
        CredentialFormat.AnonCreds -> AnonCredsCredentialBuilder(anoncredService, contextResolver),
      ))
    }
```

**Wait** — `DataModelCodec`, `CredentialSigner`, `SdJwtCredentialBuilder.IssuerKeyResolver`, and `AnonCredsCredentialBuilder.CredentialContext.Resolver` don't exist as ZIO services yet in the application. They're new abstractions from the plugin architecture that haven't been wired into the runtime. The existing `CredentialServiceImpl` uses `VcJwtService`, `SDJwtService`, and `AnoncredService` directly, not via these contracts.

**This is exactly the gap the design is trying to bridge.** The builders exist but aren't called because their dependencies (DataModelCodec, CredentialSigner, etc.) aren't provided. The existing CredentialServiceImpl has its own dependencies (VcJwtService, SDJwtService, AnoncredService).

**Pragmatic path:** For the initial wiring, `CredentialServiceImpl` keeps its existing deps AND gets a `CredentialBuilderRegistry`. The registry is initially empty. We replace the generate methods one at a time, only when we can provide the builder's deps. This is the strangler fig approach.

**REVISED PLAN:** This task is actually more complex than the design anticipated. Rather than trying to wire all builders at once, we should:

1. Add `CredentialBuilderRegistry` as a constructor parameter to `CredentialServiceImpl` (defaulting to empty)
2. NOT replace the generate methods yet — that requires solving the dependency bridging problem
3. Log the registry at startup for validation
4. Follow-up iterations wire individual builders by implementing the bridge (e.g., implementing `CredentialSigner` using `VcJwtService`)

Let me adjust Tasks 7-8 accordingly.

**Step 1: Keep builder module layers as placeholders**

The 3 builder modules keep their `layer` as `ZLayer.fromZIO(ZIO.fail(...))` from Task 3. No changes needed.

**Step 2: Create `CredentialBuilderRegistryLive` in apiServer**

Create a new file that constructs the registry from existing services:

```scala
// modules/api-server/core/src/main/scala/org/hyperledger/identus/server/CredentialBuilderRegistryLive.scala
package org.hyperledger.identus.server

import org.hyperledger.identus.shared.credentials.CredentialBuilderRegistry
import zio.*

/** Initial wiring — produces an empty registry.
  * Builders will be wired in incrementally as bridge adapters are implemented.
  */
object CredentialBuilderRegistryLive:
  val layer: ULayer[CredentialBuilderRegistry] =
    ZLayer.succeed(CredentialBuilderRegistry.empty)
```

**Step 3: Verify compile**

Run: `sbt apiServer/compile`
Expected: Success

**Step 4: Commit**

```bash
git add modules/api-server/core/src/main/scala/org/hyperledger/identus/server/CredentialBuilderRegistryLive.scala
git commit -m "feat: add CredentialBuilderRegistryLive (initially empty)"
```

---

### Task 8: Add CredentialBuilderRegistry to CredentialServiceImpl

**Files:**
- Modify: `modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/service/CredentialServiceImpl.scala`
- Modify: `modules/credentials/core/src/test/scala/org/hyperledger/identus/credentials/core/service/CredentialServiceSpecHelper.scala`

**Step 1: Add `CredentialBuilderRegistry` as constructor parameter**

In `CredentialServiceImpl.scala`, add the parameter to the constructor (line 101, after `vcJwtService`):

```scala
class CredentialServiceImpl(
    credentialRepository: CredentialRepository,
    credentialStatusListRepository: CredentialStatusListRepository,
    didResolver: DidResolver,
    uriResolver: UriResolver,
    genericSecretStorage: GenericSecretStorage,
    credentialDefinitionService: CredentialDefinitionService,
    linkSecretService: LinkSecretService,
    didService: DIDService,
    managedDIDService: ManagedDIDService,
    maxRetries: Int = 5,
    messageProducer: Producer[UUID, WalletIdAndRecordId],
    sdJwtService: SDJwtService,
    anoncredService: AnoncredService,
    vcJwtService: VcJwtService,
    builderRegistry: CredentialBuilderRegistry = CredentialBuilderRegistry.empty,
) extends CredentialService {
```

Add import: `import org.hyperledger.identus.shared.credentials.CredentialBuilderRegistry`

**Step 2: Update the companion object layer**

In `CredentialServiceImpl.scala` object (line 43), add `CredentialBuilderRegistry` to the environment:

```scala
object CredentialServiceImpl {
  val layer: URLayer[
    CredentialRepository & CredentialStatusListRepository & DidResolver & UriResolver & GenericSecretStorage &
      CredentialDefinitionService & LinkSecretService & DIDService & ManagedDIDService &
      Producer[UUID, WalletIdAndRecordId] & SDJwtService & AnoncredService & VcJwtService & CredentialBuilderRegistry,
    CredentialService
  ] = {
    ZLayer.fromZIO {
      for {
        credentialRepo <- ZIO.service[CredentialRepository]
        credentialStatusListRepo <- ZIO.service[CredentialStatusListRepository]
        didResolver <- ZIO.service[DidResolver]
        uriResolver <- ZIO.service[UriResolver]
        genericSecretStorage <- ZIO.service[GenericSecretStorage]
        credDefenitionService <- ZIO.service[CredentialDefinitionService]
        linkSecretService <- ZIO.service[LinkSecretService]
        didService <- ZIO.service[DIDService]
        manageDidService <- ZIO.service[ManagedDIDService]
        messageProducer <- ZIO.service[Producer[UUID, WalletIdAndRecordId]]
        sdJwtService <- ZIO.service[SDJwtService]
        anoncredService <- ZIO.service[AnoncredService]
        vcJwtService <- ZIO.service[VcJwtService]
        builderRegistry <- ZIO.service[CredentialBuilderRegistry]
      } yield CredentialServiceImpl(
        credentialRepo,
        credentialStatusListRepo,
        didResolver,
        uriResolver,
        genericSecretStorage,
        credDefenitionService,
        linkSecretService,
        didService,
        manageDidService,
        5,
        messageProducer,
        sdJwtService,
        anoncredService,
        vcJwtService,
        builderRegistry
      )
    }
  }
```

**Step 3: Update CredentialServiceSpecHelper**

In `CredentialServiceSpecHelper.scala`, add `CredentialBuilderRegistry` to the layer composition (after `AnoncredServiceStub.layer`):

```scala
  protected val credentialServiceLayer
      : URLayer[DIDService & ManagedDIDService & UriResolver, CredentialService & CredentialDefinitionService] =
    ZLayer.makeSome[DIDService & ManagedDIDService & UriResolver, CredentialService & CredentialDefinitionService](
      CredentialRepositoryInMemory.layer,
      VcJwtServiceStub.layer,
      CredentialStatusListRepositoryInMemory.layer,
      didResolverLayer,
      credentialDefinitionServiceLayer,
      GenericSecretStorageInMemory.layer,
      LinkSecretServiceImpl.layer,
      (MessagingServiceConfig.inMemoryLayer >>> MessagingService.serviceLayer >>>
        (zio.Scope.default >>> MessagingService.producerLayer[UUID, WalletIdAndRecordId])).orDie,
      SDJwtServiceStub.layer,
      AnoncredServiceStub.layer,
      ZLayer.succeed(CredentialBuilderRegistry.empty),
      CredentialServiceImpl.layer
    )
```

Add import: `import org.hyperledger.identus.shared.credentials.CredentialBuilderRegistry`

**Step 4: Verify compile**

Run: `sbt credentialsCore/compile`
Expected: Success

**Step 5: Verify tests**

Run: `sbt credentialsCore/test`
Expected: All existing tests pass

**Step 6: Commit**

```bash
git add modules/credentials/core/src/main/scala/org/hyperledger/identus/credentials/core/service/CredentialServiceImpl.scala
git add modules/credentials/core/src/test/scala/org/hyperledger/identus/credentials/core/service/CredentialServiceSpecHelper.scala
git commit -m "feat: add CredentialBuilderRegistry to CredentialServiceImpl constructor"
```

---

### Task 9: Provide CredentialBuilderRegistry layer in application wiring

**Files:**
- Modify: Application layer wiring (the file that composes all ZLayers for the server)

**Step 1: Find the application wiring**

Search for where `CredentialServiceImpl.layer` is provided in the application composition. This is likely in `MainApp.scala` or a Modules file. We need to also provide `CredentialBuilderRegistryLive.layer`.

Run: `grep -r "CredentialServiceImpl.layer" --include="*.scala" -l`

**Step 2: Add CredentialBuilderRegistryLive.layer**

In the file that composes layers, add `CredentialBuilderRegistryLive.layer` alongside the existing service layers.

**Step 3: Verify full compile**

Run: `sbt apiServer/compile`
Expected: Success

**Step 4: Commit**

```bash
git add <modified-wiring-file>
git commit -m "feat: provide CredentialBuilderRegistry layer in application wiring"
```

---

### Task 10: Add architecture constraints

**Files:**
- Modify: `project/ArchConstraints.scala`

**Step 1: Add constraint ensuring shared doesn't depend on builder implementations**

The `CredentialBuilderRegistry` is in `shared/core`. It must NOT depend on builder implementation modules. Add:

```scala
// shared should not depend on credential builder implementations
("shared", "credentialsAnoncreds", "shared should not depend on credentialsAnoncreds", Direct),
("shared", "credentialsSDJWT", "shared should not depend on credentialsSDJWT", Direct),
```

**Note:** `shared -> credentialsVcJWT` constraint already exists.

**Step 2: Verify constraints**

Run: `sbt checkArchConstraints`
Expected: All constraints pass

**Step 3: Commit**

```bash
git add project/ArchConstraints.scala
git commit -m "chore: add arch constraints for CredentialBuilderRegistry"
```

---

### Task 11: Final verification

**Step 1: Full compile**

Run: `sbt compile`
Expected: Success

**Step 2: Run all tests**

Run: `sbt test`
Expected: All tests pass (any failures should be pre-existing)

**Step 3: Architecture constraints**

Run: `sbt checkArchConstraints`
Expected: All constraints satisfied

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Extend Module trait with `type Service` + `def layer` | Module.scala, ModuleRegistrySpec.scala |
| 2 | Stub `Service = Unit` on 7 non-builder modules | 7 module files |
| 3 | Set `Service = CredentialBuilder` on 3 builder modules | 3 module files |
| 4 | Create `CredentialBuilderRegistry` | New file + test |
| 5 | Add `assembleBuilderRegistry` to ModuleRegistry | ModuleRegistry.scala + test |
| 6 | Wire into AllModules + CloudAgentApp startup | AllModules.scala, CloudAgentApp.scala |
| 7 | Create CredentialBuilderRegistryLive (empty) | New file |
| 8 | Add registry to CredentialServiceImpl constructor | CredentialServiceImpl.scala + test helper |
| 9 | Provide registry layer in app wiring | Wiring file |
| 10 | Architecture constraints | ArchConstraints.scala |
| 11 | Final verification | N/A |

## Next Iterations (out of scope)

After this phase completes, follow-up work includes:
1. **Bridge adapters**: Implement `CredentialSigner` using `VcJwtService`, `IssuerKeyResolver` using `ManagedDIDService`
2. **Wire real builders**: Update `CredentialBuilderRegistryLive` to construct real builders using bridge adapters
3. **Delegate generate methods**: Replace `generateJWTCredential`/`generateSDJWTCredential`/`generateAnonCredsCredential` with calls to `builderRegistry.get(format).buildCredential(ctx)`
4. **Remove inline logic**: Delete the 300 lines of format-specific code from CredentialServiceImpl
