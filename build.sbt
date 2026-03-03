import org.scoverage.coveralls.Imports.CoverallsKeys.*
import sbtbuildinfo.BuildInfoPlugin.autoImport.*

// externalResolvers += "ScalaLibrary packages" at "https://maven.pkg.github.com/input-output-hk/anoncreds-rs" // use plugin"sbt-github-packages"

inThisBuild(
  Seq(
    organization := "org.hyperledger",
    scalaVersion := "3.3.5",
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    fork := true,
    run / connectInput := true,
    releaseUseGlobalVersion := false,
    versionScheme := Some("semver-spec"),
  )
)

// Fixes a bug with concurrent packages download from GitHub registry
Global / concurrentRestrictions += Tags.limit(Tags.Network, 1)

coverageDataDir := target.value / "coverage"
coberturaFile := target.value / "coverage" / "coverage-report" / "cobertura.xml"
coverageExcludedPackages := "(?i).*proto.*;.*grpc.*;.*scalapb.*;.*protobuf.*;.*generated.*"

inThisBuild(
  Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8"),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
    ),
    scalacOptions += "-Wunused:all",
    scalacOptions += "-Wconf:any:error,cat=deprecation:warning", // "-Wconf:help",
    // scalacOptions += "-Yexplicit-nulls",
    // scalacOptions += "-Ysafe-init",
    // scalacOptions +=  "-Werror", // <=> "-Xfatal-warnings"
    scalacOptions += "-Dquill.macro.log=false", // disable quill macro logs // TODO https://github.com/zio/zio-protoquill/issues/470,
    scalacOptions ++= Seq("-Xmax-inlines", "50"), // increase above 32 (https://github.com/circe/circe/issues/2162)
    Test / javaOptions ++= Seq("-Dlog4j2.disable.jmx=true", "-Ddocker.api.version=1.44"),
    Test / envVars ++= Map(
      "DOCKER_API_VERSION" -> "1.44",
      "DOCKER_HOST" -> "unix:///var/run/docker.sock"
    )
  )
)

lazy val V = new {
  val munit = "1.2.1" // "0.7.29"
  val munitZio = "0.4.0"

  // https://mvnrepository.com/artifact/dev.zio/zio
  val zio = "2.1.23"
  val zioConfig = "4.0.6"
  val zioLogging = "2.5.2"
  val zioJson = "0.7.45"
  val zioHttp = "3.7.2"
  val zioCatsInterop = "3.3.0" // TODO "23.1.0.2" // https://mvnrepository.com/artifact/dev.zio/zio-interop-cats
  val zioMetricsConnector = "2.5.4"
  val zioMock = "1.0.0-RC12"
  val zioKafka = "3.2.0"
  val mockito = "3.2.18.0"
  val monocle = "3.3.0"

  val tapir = "1.11.7" // scala-steward:off // TODO "1.10.5"
  val http4sBlaze = "0.23.15" // scala-steward:off  // TODO "0.23.16"

  val typesafeConfig = "1.4.4"
  val protobuf = "3.1.9"
  val grpcOkHttp = "1.63.0"

  // align with Docker client API used by GH runners
  val testContainersScala = "0.44.1"
  val testContainersJavaKeycloak = "3.2.0" // scala-steward:off

  val doobie = "1.0.0-RC5" // scala-steward:off
  val quill = "4.8.6"
  val flyway = "9.22.3" // scala-steward:off
  val postgresDriver = "42.7.8"
  val logback = "1.5.18"
  val slf4j = "2.0.17"

  val scalaUri = "4.2.0"

  val jwtZioVersion = "11.0.2"
  val zioPreludeVersion = "1.0.0-RC44"

  val apollo = "1.3.5"

  val jsonSchemaValidator = "1.3.2" // scala-steward:off //TODO 1.3.2 need to fix:
  // [error] 	org.hyperledger.identus.credentials.core.model.schema.AnoncredSchemaTypeSpec
  // [error] 	org.hyperledger.identus.credentials.core.model.schema.CredentialSchemaSpec

  val commonsLogging = "1.3.5"
  val vaultDriver = "6.2.0"
  val micrometer = "1.15.2"

  val nimbusJwt = "9.37.3" // scala-steward:off //TODO: >=9.38 breaking change
  val keycloak = "23.0.7" // scala-steward:off //TODO 24.0.3 // update all quay.io/keycloak/keycloak

  val vdr = "0.2.1"
  val prismVdr = "0.3.0"
}

/** Dependencies */
lazy val D = new {
  val zio: ModuleID = "dev.zio" %% "zio" % V.zio
  val zioStreams: ModuleID = "dev.zio" %% "zio-streams" % V.zio
  val zioLog: ModuleID = "dev.zio" %% "zio-logging" % V.zioLogging
  val zioSLF4J: ModuleID = "dev.zio" %% "zio-logging-slf4j" % V.zioLogging
  val zioJson: ModuleID = "dev.zio" %% "zio-json" % V.zioJson
  val zioConcurrent: ModuleID = "dev.zio" %% "zio-concurrent" % V.zio
  val zioHttp: ModuleID = "dev.zio" %% "zio-http" % V.zioHttp
  val zioKafka: ModuleID = "dev.zio" %% "zio-kafka" % V.zioKafka excludeAll (
    ExclusionRule("dev.zio", "zio_3"),
    ExclusionRule("dev.zio", "zio-streams_3")
  )
  val zioCatsInterop: ModuleID = "dev.zio" %% "zio-interop-cats" % V.zioCatsInterop
  val zioMetricsConnectorMicrometer: ModuleID = "dev.zio" %% "zio-metrics-connectors-micrometer" % V.zioMetricsConnector
  val tapirPrometheusMetrics: ModuleID = "com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % V.tapir
  val micrometer: ModuleID = "io.micrometer" % "micrometer-registry-prometheus" % V.micrometer
  val micrometerPrometheusRegistry = "io.micrometer" % "micrometer-core" % V.micrometer
  val scalaUri = Seq(
    "com.indoorvivants" %% "scala-uri" % V.scalaUri exclude (
      "org.typelevel",
      "cats-parse_3"
    ), // Exclude cats-parse to avoid deps conflict
    "org.typelevel" % "cats-parse_3" % "1.1.0", // Replace with version 1.0.0
  )

  val zioConfig: ModuleID = "dev.zio" %% "zio-config" % V.zioConfig
  val zioConfigMagnolia: ModuleID = "dev.zio" %% "zio-config-magnolia" % V.zioConfig
  val zioConfigTypesafe: ModuleID = "dev.zio" %% "zio-config-typesafe" % V.zioConfig

  val commonsLogging = "commons-logging" % "commons-logging" % V.commonsLogging
  val networkntJsonSchemaValidator = "com.networknt" % "json-schema-validator" % V.jsonSchemaValidator
  val jwtZio = "com.github.jwt-scala" %% "jwt-zio-json" % V.jwtZioVersion
  val jsonCanonicalization: ModuleID = "io.github.erdtman" % "java-json-canonicalization" % "1.1"
  val titaniumJsonLd: ModuleID = "com.apicatalog" % "titanium-json-ld" % "1.6.0"
  val jakartaJson: ModuleID = "org.glassfish" % "jakarta.json" % "2.0.1" // used by titanium-json-ld
  val ironVC: ModuleID = "com.apicatalog" % "iron-verifiable-credentials" % "0.14.0"
  val scodecBits: ModuleID = "org.scodec" %% "scodec-bits" % "1.2.4"
  val jaywayJsonPath: ModuleID = "com.jayway.jsonpath" % "json-path" % "2.9.0"

  // https://mvnrepository.com/artifact/org.didcommx/didcomm/0.3.2
  val didcommx: ModuleID = "org.didcommx" % "didcomm" % "0.3.2"
  val peerDidcommx: ModuleID = "org.didcommx" % "peerdid" % "0.5.0"
  val didScala: ModuleID = "app.fmgp" %% "did" % "0.0.0+113-61efa271-SNAPSHOT"

  val nimbusJwt: ModuleID = "com.nimbusds" % "nimbus-jose-jwt" % V.nimbusJwt

  val typesafeConfig: ModuleID = "com.typesafe" % "config" % V.typesafeConfig
  val scalaPbRuntime: ModuleID =
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  val scalaPbGrpc: ModuleID = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
  val grpcOkHttp: ModuleID = "io.grpc" % "grpc-okhttp" % V.grpcOkHttp

  val testcontainersPostgres: ModuleID =
    "com.dimafeng" %% "testcontainers-scala-postgresql" % V.testContainersScala % Test
  val testcontainersVault: ModuleID = "com.dimafeng" %% "testcontainers-scala-vault" % V.testContainersScala % Test
  val testcontainersKeycloak: ModuleID =
    "com.github.dasniko" % "testcontainers-keycloak" % V.testContainersJavaKeycloak % Test

  val doobiePostgres: ModuleID = "org.tpolecat" %% "doobie-postgres" % V.doobie
  val doobieHikari: ModuleID = "org.tpolecat" %% "doobie-hikari" % V.doobie
  val flyway: ModuleID = "org.flywaydb" % "flyway-core" % V.flyway

  // For munit https://scalameta.org/munit/docs/getting-started.html#scalajs-setup
  val munit: ModuleID = "org.scalameta" %% "munit" % V.munit % Test
  // For munit zio https://github.com/poslegm/munit-zio
  val munitZio: ModuleID = "com.github.poslegm" %% "munit-zio" % V.munitZio % Test

  val zioTest: ModuleID = "dev.zio" %% "zio-test" % V.zio % Test
  val zioTestSbt: ModuleID = "dev.zio" %% "zio-test-sbt" % V.zio % Test
  val zioTestMagnolia: ModuleID = "dev.zio" %% "zio-test-magnolia" % V.zio % Test
  val zioMock: ModuleID = "dev.zio" %% "zio-mock" % V.zioMock
  val zioPrelude: ModuleID = "dev.zio" %% "zio-prelude" % V.zioPreludeVersion
  val mockito: ModuleID = "org.scalatestplus" %% "mockito-4-11" % V.mockito % Test
  val monocle: ModuleID = "dev.optics" %% "monocle-core" % V.monocle % Test
  val monocleMacro: ModuleID = "dev.optics" %% "monocle-macro" % V.monocle % Test
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19" % Test

  val apollo = Seq( // TODO remove exclude after fix https://github.com/hyperledger/identus-apollo/issues/192
    "io.iohk.atala.prism.apollo" % "apollo-jvm" % V.apollo exclude (
      "net.jcip",
      "jcip-annotations"
    ), // Exclude because of license
    "com.github.stephenc.jcip" % "jcip-annotations" % "1.0-1" % Runtime, // Replace for net.jcip % jcip-annotations"
  )

  // LIST of Dependencies
  val doobieDependencies: Seq[ModuleID] =
    Seq(doobiePostgres, doobieHikari, flyway)
}

lazy val D_Shared = new {
  lazy val dependencies: Seq[ModuleID] =
    Seq(
      D.typesafeConfig,
      D.scalaPbGrpc,
      D.zio,
      D.zioConcurrent,
      D.zioHttp,
      D.zioKafka,
      D.zioPrelude,
      // FIXME: split shared DB stuff as subproject?
      D.doobieHikari,
      D.doobiePostgres,
      D.zioCatsInterop,
    ) ++ D.scalaUri
}

lazy val D_SharedJson = new {
  lazy val dependencies: Seq[ModuleID] =
    Seq(
      D.zio,
      D.zioJson,
      D.jsonCanonicalization,
      D.titaniumJsonLd,
      D.jakartaJson,
      D.ironVC,
      D.scodecBits,
      D.networkntJsonSchemaValidator,
      D.jaywayJsonPath
    )
}

lazy val D_SharedCrypto = new {
  lazy val dependencies: Seq[ModuleID] =
    Seq(
      D.zioJson,
      D.nimbusJwt,
      D.zioTest,
      D.zioTestSbt,
      D.zioTestMagnolia,
    ) ++ D.apollo
}

lazy val D_SharedTest = new {
  lazy val dependencies: Seq[ModuleID] =
    D_Shared.dependencies ++ Seq(
      D.testcontainersPostgres,
      D.testcontainersVault,
      D.testcontainersKeycloak,
      D.zioCatsInterop,
      D.zioJson,
      D.zioHttp,
      D.zioTest,
      D.zioTestSbt,
      D.zioTestMagnolia,
      D.zioMock
    )
}

lazy val D_Connections = new {

  private lazy val logback = "ch.qos.logback" % "logback-classic" % V.logback % Test

  // Dependency Modules
  private lazy val baseDependencies: Seq[ModuleID] =
    Seq(D.zio, D.zioTest, D.zioTestSbt, D.zioTestMagnolia, D.zioMock, D.testcontainersPostgres, logback)

  // Project Dependencies
  lazy val coreDependencies: Seq[ModuleID] =
    baseDependencies
  lazy val sqlDoobieDependencies: Seq[ModuleID] =
    baseDependencies ++ D.doobieDependencies ++ Seq(D.zioCatsInterop)
}

lazy val D_DID = new {
  // Dependency Modules
  val baseDependencies: Seq[ModuleID] =
    Seq(
      D.zio,
      D.zioTest,
      D.zioMock,
      D.zioTestSbt,
      D.zioTestMagnolia,
      D.zioHttp,
    )

  // Project Dependencies
  val coreDependencies: Seq[ModuleID] = baseDependencies
}

lazy val D_Credentials = new {
  val logback = "ch.qos.logback" % "logback-classic" % V.logback % Test
  val slf4jApi = "org.slf4j" % "slf4j-api" % V.slf4j % Test
  val slf4jSimple = "org.slf4j" % "slf4j-simple" % V.slf4j % Test

  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % V.doobie
  val doobieHikari = "org.tpolecat" %% "doobie-hikari" % V.doobie

  val flyway = "org.flywaydb" % "flyway-core" % V.flyway

  val quillJdbcZio = "io.getquill" %% "quill-jdbc-zio" %
    V.quill exclude ("org.scala-lang.modules", "scala-java8-compat_3")

  val quillDoobie = "io.getquill" %% "quill-doobie" %
    V.quill exclude ("org.scala-lang.modules", "scala-java8-compat_3")

  // Dependency Modules
  val baseDependencies: Seq[ModuleID] = Seq(
    D.zio,
    D.zioJson,
    D.zioHttp,
    D.zioTest,
    D.zioTestSbt,
    D.zioTestMagnolia,
    D.zioMock,
    D.munit,
    D.munitZio,
    // shared,
    logback,
    slf4jApi,
    slf4jSimple
  )

  val doobieDependencies: Seq[ModuleID] = Seq(
    D.zioCatsInterop,
    D.doobiePostgres,
    D.doobieHikari,
    D.testcontainersPostgres,
    flyway,
    quillDoobie,
    quillJdbcZio,
  )

  // Project Dependencies
  val coreDependencies: Seq[ModuleID] = baseDependencies
  val sqlDoobieDependencies: Seq[ModuleID] = baseDependencies ++ doobieDependencies
}

lazy val D_Credentials_VC_JWT = new {

  val zio = "dev.zio" %% "zio" % V.zio
  val zioPrelude = "dev.zio" %% "zio-prelude" % V.zioPreludeVersion

  val zioTest = "dev.zio" %% "zio-test" % V.zio % Test
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % V.zio % Test
  val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % V.zio % Test

  // Dependency Modules
  val zioDependencies: Seq[ModuleID] = Seq(zio, zioPrelude, zioTest, zioTestSbt, zioTestMagnolia)
  val baseDependencies: Seq[ModuleID] =
    zioDependencies :+ D.jwtZio :+ D.networkntJsonSchemaValidator :+ D.nimbusJwt :+ D.scalaTest

  // Project Dependencies
  lazy val credentialsVcJwtDependencies: Seq[ModuleID] = baseDependencies
}

lazy val D_Notifications = new {
  val zio = "dev.zio" %% "zio" % V.zio
  val zioTest = "dev.zio" %% "zio-test" % V.zio % Test
  val zioTestSbt = "dev.zio" %% "zio-test-sbt" % V.zio % Test
  val zioTestMagnolia = "dev.zio" %% "zio-test-magnolia" % V.zio % Test

  val zioDependencies: Seq[ModuleID] = Seq(zio, zioTest, zioTestSbt, zioTestMagnolia)
  val baseDependencies: Seq[ModuleID] = zioDependencies
}

lazy val D_Credentials_AnonCreds = new {
  val baseDependencies: Seq[ModuleID] = Seq(D.zio, D.zioJson)
}

lazy val D_Server = new {
  val logback = "ch.qos.logback" % "logback-classic" % V.logback

  val tapirSwaggerUiBundle = "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % V.tapir
  val tapirJsonZio = "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % V.tapir

  val tapirZioHttpServer = "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % V.tapir
  val tapirHttp4sServerZio = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server-zio" % V.tapir
  val http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % V.http4sBlaze

  val tapirRedocBundle = "com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle" % V.tapir

  val tapirSttpStubServer =
    "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % V.tapir % Test
  val sttpClient3ZioJson = "com.softwaremill.sttp.client3" %% "zio-json" % "3.11.0" % Test

  val quillDoobie =
    "io.getquill" %% "quill-doobie" % V.quill exclude ("org.scala-lang.modules", "scala-java8-compat_3")
  val postgresql = "org.postgresql" % "postgresql" % V.postgresDriver
  val quillJdbcZio =
    "io.getquill" %% "quill-jdbc-zio" % V.quill exclude ("org.scala-lang.modules", "scala-java8-compat_3")

  val flyway = "org.flywaydb" % "flyway-core" % V.flyway

  val vaultDriver = "io.github.jopenlibs" % "vault-java-driver" % V.vaultDriver
  val keycloakAuthz = "org.keycloak" % "keycloak-authz-client" % V.keycloak

  val vdr = "org.hyperledger.identus" % "vdr" % V.vdr
  val prismVdr = "org.hyperledger.identus" %% "prism-vdr-driver" % V.prismVdr

  // Dependency Modules
  val baseDependencies: Seq[ModuleID] = Seq(
    D.zio,
    D.zioTest,
    D.zioTestSbt,
    D.zioTestMagnolia,
    D.zioConfig,
    D.zioConfigMagnolia,
    D.zioConfigTypesafe,
    D.zioJson,
    logback,
    D.zioHttp,
    D.zioMetricsConnectorMicrometer,
    D.tapirPrometheusMetrics,
    D.micrometer,
    D.micrometerPrometheusRegistry
  )
  val tapirDependencies: Seq[ModuleID] =
    Seq(
      tapirSwaggerUiBundle,
      tapirJsonZio,
      tapirRedocBundle,
      tapirSttpStubServer,
      sttpClient3ZioJson,
      tapirZioHttpServer,
      tapirHttp4sServerZio,
      http4sBlazeServer
    )

  val postgresDependencies: Seq[ModuleID] =
    Seq(quillDoobie, quillJdbcZio, postgresql, flyway, D.testcontainersPostgres, D.zioCatsInterop)

  // Project Dependencies
  lazy val keyManagementDependencies: Seq[ModuleID] =
    baseDependencies ++ D.doobieDependencies ++ Seq(D.zioCatsInterop, D.zioMock, vaultDriver)

  lazy val iamDependencies: Seq[ModuleID] = Seq(keycloakAuthz, D.jwtZio, D.commonsLogging)

  lazy val vdrDependencies: Seq[ModuleID] = Seq(vdr, prismVdr)

  lazy val serverDependencies: Seq[ModuleID] =
    baseDependencies ++ tapirDependencies ++ postgresDependencies ++ Seq(
      D.zioMock,
      D.mockito,
      D.monocle,
      D.monocleMacro
    )
}

publish / skip := true

// Architectural tooling
DependencyGraph.settings
ArchConstraints.settings

val commonSetttings = Seq(
  testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
  libraryDependencies ++= Seq(D.zioTest, D.zioTestSbt, D.zioTestMagnolia),
  resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  // Needed for Kotlin coroutines that support new memory management mode
  resolvers += "JetBrains Space Maven Repository" at "https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven",
  // Needed for com.github.multiformats:java-multibase
  resolvers += "jitpack" at "https://jitpack.io",
)

lazy val commonConfigure: Project => Project = _.settings(
  Compile / scalacOptions += "-Yimports:java.lang,scala,scala.Predef,org.hyperledger.identus.Predef",
  Test / scalacOptions -= "-Yimports:java.lang,scala,scala.Predef,org.hyperledger.identus.Predef",
).dependsOn(predef)

// #####################
// #####  shared  ######
// #####################

lazy val predef = (project in file("modules/shared/predef"))

lazy val shared = (project in file("modules/shared/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "shared",
    crossPaths := false,
    libraryDependencies ++= D_Shared.dependencies
  )

lazy val sharedJson = (project in file("modules/shared/json"))
  .settings(commonSetttings)
  .settings(
    name := "shared-json",
    crossPaths := false,
    libraryDependencies ++= D_SharedJson.dependencies
  )
  .dependsOn(shared)

lazy val sharedCrypto = (project in file("modules/shared/crypto"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "shared-crypto",
    crossPaths := false,
    libraryDependencies ++= D_SharedCrypto.dependencies
  )
  .dependsOn(shared)

lazy val sharedTest = (project in file("modules/shared/test"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "shared-test",
    crossPaths := false,
    libraryDependencies ++= D_SharedTest.dependencies
  )
  .dependsOn(shared)

// #########################
// ###     DIDComm       ###
// #########################

/** Just data models and interfaces of service.
  *
  * This module must not depend on external libraries!
  */
lazy val didcommModels = project
  .in(file("modules/didcomm/models"))
  .configure(commonConfigure)
  .settings(name := "didcomm-models")
  .settings(
    libraryDependencies ++= Seq(D.zio)
  )
  .settings(libraryDependencies += D.nimbusJwt) // FIXME just for the DidAgent
  .dependsOn(shared)

lazy val didcommApi = project
  .in(file("modules/didcomm/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "didcomm-api")
  .dependsOn(shared, didcommModels)

/* TODO move code from didcommAgentDidcommx to here
models implementation for didcommx () */
// lazy val modelsDidcommx = project
//   .in(file("models-didcommx"))
//   .settings(name := "didcomm-models-didcommx")
//   .settings(libraryDependencies += D.didcommx)
//   .dependsOn(didcommModels)

// #################
// ### Protocols ###
// #################

lazy val protocolConnection = project
  .in(file("modules/didcomm/protocol-connection"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-connection")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels, protocolInvitation)

lazy val protocolCoordinateMediation = project
  .in(file("modules/didcomm/protocol-coordinate-mediation"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-coordinate-mediation")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels)

lazy val protocolDidExchange = project
  .in(file("modules/didcomm/protocol-did-exchange"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-did-exchange")
  .settings(libraryDependencies += D.zio)
  .dependsOn(didcommModels, protocolInvitation)

lazy val protocolInvitation = project
  .in(file("modules/didcomm/protocol-invitation"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-invitation")
  .settings(libraryDependencies += D.zio)
  .settings(
    libraryDependencies ++= Seq(
      D.munit,
      D.munitZio
    )
  )
  .dependsOn(didcommModels)

// lazy val protocolMailbox = project
//   .in(file("modules/didcomm/protocol-mailbox"))
//   .settings(predefSetttings)
//   .settings(name := "didcomm-protocol-mailbox")
//   .settings(libraryDependencies += D.zio)
//   .dependsOn(didcommModels, protocolInvitation, protocolRouting)

lazy val protocolLogin = project
  .in(file("modules/didcomm/protocol-outofband-login"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-outofband-login")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels)

lazy val protocolReportProblem = project
  .in(file("modules/didcomm/protocol-report-problem"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-report-problem")
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels)

lazy val protocolRouting = project
  .in(file("modules/didcomm/protocol-routing"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-routing-2-0")
  .settings(libraryDependencies += D.zio)
  .dependsOn(didcommModels)

lazy val protocolIssueCredential = project
  .in(file("modules/didcomm/protocol-issue-credential"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-issue-credential")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels, protocolInvitation)

lazy val protocolRevocationNotification = project
  .in(file("modules/didcomm/protocol-revocation-notification"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-revocation-notification")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels)

lazy val protocolPresentProof = project
  .in(file("modules/didcomm/protocol-present-proof"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-present-proof")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels, protocolInvitation)

lazy val didcommVC = project
  .in(file("modules/didcomm/vc"))
  .configure(commonConfigure)
  .settings(name := "didcomm-verifiable-credentials")
  .dependsOn(protocolIssueCredential, protocolPresentProof) //TODO merge those two modules into this one

lazy val protocolTrustPing = project
  .in(file("modules/didcomm/protocol-trust-ping"))
  .configure(commonConfigure)
  .settings(name := "didcomm-protocol-trust-ping")
  .settings(libraryDependencies += D.zio)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommModels)

// ################
// ### Resolver ###
// ################

// TODO move stuff to the models module
lazy val didcommResolver = project
  .in(file("modules/didcomm/resolver"))
  .configure(commonConfigure)
  .settings(name := "didcomm-resolver")
  .settings(
    libraryDependencies ++= Seq(
      D.didcommx,
      D.peerDidcommx,
      D.munit,
      D.munitZio,
      D.nimbusJwt,
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .dependsOn(didcommModels)

// ##############
// ### Agents ###
// ##############

lazy val didcommAgent = project
  .in(file("modules/didcomm/agent"))
  .configure(commonConfigure)
  .settings(name := "didcomm-agent")
  .settings(libraryDependencies ++= Seq(D.zioLog, D.zioSLF4J))
  .dependsOn(
    didcommModels,
    didcommResolver,
    protocolCoordinateMediation,
    protocolInvitation,
    protocolRouting,
    // protocolMercuryMailbox,
    protocolLogin,
    protocolIssueCredential,
    protocolRevocationNotification,
    protocolPresentProof,
    didcommVC,
    protocolConnection,
    protocolReportProblem,
    protocolTrustPing,
  )

/** agents implementation with didcommx */
lazy val didcommAgentDidcommx = project
  .in(file("modules/didcomm/agent-didcommx"))
  .configure(commonConfigure)
  .settings(name := "didcomm-agent-didcommx")
  .settings(libraryDependencies += D.didcommx)
  .settings(libraryDependencies += D.munitZio)
  .dependsOn(didcommAgent) //modelsDidcommx

// ///** TODO Demos agents and services implementation with did-scala */
// lazy val agentDidScala =
//   project
//     .in(file("modules/didcomm/agent-did-scala"))
//     .settings(name := "didcomm-agent-didscala")
//     .settings(skip / publish := true)
//     .dependsOn(didcommAgent)

// ####################
// ###  Prism Node ####
// ####################
val prismNodeClient = project
  .in(file("modules/prism-node/client"))
  .configure(commonConfigure)
  .settings(
    name := "prism-node-client",
    libraryDependencies ++= Seq(D.scalaPbGrpc, D.scalaPbRuntime, D.grpcOkHttp),
    coverageEnabled := false,
    // gRPC settings
    Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"),
    Compile / PB.protoSources := Seq(
      baseDirectory.value / "api" / "grpc",
      (Compile / resourceDirectory).value // includes scalapb codegen package wide config
    )
  )

// #####################
// #####   DID    ######
// #####################

lazy val didApi = project
  .in(file("modules/did/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "did-api", libraryDependencies ++= Seq(D.zioMock))
  .dependsOn(shared, prismNodeClient)
  .dependsOn(sharedCrypto % "compile->compile;test->test")

lazy val didCore = project
  .in(file("modules/did/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "did-core",
    libraryDependencies ++= D_DID.coreDependencies
  )
  .dependsOn(didApi, prismNodeClient)

// #####################
// ### Credentials  ####
// #####################

lazy val credentialsVcJWT = project
  .in(file("modules/credentials/vc-jwt"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credentials-vc-jwt",
    libraryDependencies ++= D_Credentials_VC_JWT.credentialsVcJwtDependencies
  )
  .dependsOn(didApi, sharedJson)

lazy val credentialsCore = project
  .in(file("modules/credentials/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credentials-core",
    libraryDependencies ++= D_Credentials.coreDependencies
  )
  .dependsOn(
    shared,
    didApi % "compile->compile;test->test", // Test is for MockDIDService
    walletManagementApi % "compile->compile;test->test", // lightweight types (Entity, GenericSecretStorage)
    walletManagement % "compile->compile;test->test", // test is for MockManagedDIDService
    didcommResolver,
    protocolIssueCredential,
    protocolPresentProof,
    didcommAgentDidcommx % "test->compile", // Test is for PeerDID/AgentPeerService
    notifications,
    credentialsAnoncreds,
    credentialsVcJWT,
    sharedJson, // for PresentationDefinition (JsonPath, JsonSchema)
  )

lazy val credentialsApi = project
  .in(file("modules/credentials/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "credentials-api")
  .dependsOn(shared, credentialsCore, didcommApi, didApi)

lazy val credentialsPersistenceDoobie = project
  .in(file("modules/credentials/persistence-doobie"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credentials-persistence-doobie",
    libraryDependencies ++= D_Credentials.sqlDoobieDependencies
  )
  .dependsOn(credentialsCore % "compile->compile;test->test")
  .dependsOn(shared)
  .dependsOn(sharedTest % "test->test")

lazy val credentialsPreX = project
  .in(file("modules/credentials/prex"))
  .settings(commonSetttings)
  .settings(name := "credentials-prex")
  .dependsOn(credentialsCore, shared, sharedJson, credentialsVcJWT)

// ###############################
// ### Credentials Anoncreds  ###
// ###############################

lazy val credentialsAnoncreds = project
  .in(file("modules/credentials/anoncreds"))
  .configure(commonConfigure)
  .settings(
    name := "credentials-anoncreds",
    Compile / unmanagedJars += baseDirectory.value / "anoncreds-jvm-1.0-SNAPSHOT.jar",
    Compile / unmanagedResourceDirectories ++= Seq(
      baseDirectory.value / "native-lib" / "NATIVE"
    ),
    libraryDependencies ++= D_Credentials_AnonCreds.baseDependencies
  )

lazy val credentialsAnoncredsTest = project
  .in(file("modules/credentials/anoncredsTest"))
  .configure(commonConfigure)
  .settings(libraryDependencies += D.scalaTest)
  .dependsOn(credentialsAnoncreds % "compile->test")

lazy val credentialsSDJWT = project
  .in(file("modules/credentials/sd-jwt"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credentials-sd-jwt",
    libraryDependencies += "io.iohk.atala" % "sd-jwt-kmp-jvm" % "0.1.2"
  )
  .dependsOn(sharedCrypto, credentialsCore)

// #####################
// ### Connections  ####
// #####################

lazy val connectionsCore = project
  .in(file("modules/connections/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "connections-core",
    libraryDependencies ++= D_Connections.coreDependencies,
    Test / publishArtifact := true
  )
  .dependsOn(shared)
  .dependsOn(protocolConnection, protocolReportProblem, notifications)

lazy val connectionsPersistenceDoobie = project
  .in(file("modules/connections/persistence-doobie"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "connections-persistence-doobie",
    libraryDependencies ++= D_Connections.sqlDoobieDependencies
  )
  .dependsOn(shared)
  .dependsOn(sharedTest % "test->test")
  .dependsOn(connectionsCore % "compile->compile;test->test")

lazy val connectionsApi = project
  .in(file("modules/connections/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "connections-api")
  .dependsOn(shared, connectionsCore, didcommApi)

// #####################
// ### Notifications ###
// #####################

lazy val notificationsApi = project
  .in(file("modules/notifications/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "notifications-api")
  .dependsOn(shared)

lazy val notifications = project
  .in(file("modules/notifications/core"))
  .configure(commonConfigure)
  .settings(
    name := "notifications",
    libraryDependencies ++= D_Notifications.baseDependencies
  )
  .dependsOn(notificationsApi)

lazy val notificationsHttp = project
  .in(file("modules/notifications/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "notifications-http",
    libraryDependencies ++= Seq(
      D_Server.tapirJsonZio,
      D_Server.tapirZioHttpServer,
      D_Server.tapirSwaggerUiBundle,
      D.zio,
      D.zioJson
    )
  )
  .dependsOn(apiServerHttpCore, notifications, walletManagement)

lazy val notificationsWebhook = project
  .in(file("modules/notifications/webhook"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "notifications-webhook",
    libraryDependencies ++= Seq(D.zioHttp)
  )
  .dependsOn(
    apiServerConfig,
    notificationsApi,
    connectionsCore,
    credentialsCore,
    walletManagement,
    shared,
  )

// ##########################
// ### Wallet Management ###
// ##########################

lazy val walletManagement = project
  .in(file("modules/wallet-management/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "wallet-management",
    libraryDependencies ++=
      D_Server.keyManagementDependencies ++
        D_Server.iamDependencies ++
        D_Server.postgresDependencies ++
        Seq(D.zioMock)
  )
  .dependsOn(
    walletManagementApi,
    didcommResolver,
    didApi,
    notifications
  )
  .dependsOn(sharedTest % "test->test")
  .dependsOn(sharedCrypto % "compile->compile;test->test")

lazy val walletManagementApi = project
  .in(file("modules/wallet-management/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "wallet-management-api")
  .dependsOn(shared)

lazy val walletPersistenceDoobie = project
  .in(file("modules/wallet-management/persistence-doobie"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "wallet-management-persistence-doobie")
  .dependsOn(walletManagement)

lazy val walletSecretsVault = project
  .in(file("modules/wallet-management/secrets-vault"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "wallet-management-secrets-vault")
  .dependsOn(walletManagement)

lazy val vdrService = project
  .in(file("modules/vdr/service"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-service",
    libraryDependencies ++= D_Server.baseDependencies ++ D_Server.vdrDependencies,
  )
  .dependsOn(shared, prismNodeClient, vdrCore, vdrPrismNode, vdrDatabase, vdrMemory, vdrProxy)

lazy val vdrCore = project
  .in(file("modules/vdr/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-core",
    libraryDependencies ++= D_Server.vdrDependencies,
  )
  .dependsOn(shared, prismNodeClient)

lazy val vdrApi = project
  .in(file("modules/vdr/api"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "vdr-api")
  .dependsOn(shared, vdrCore)

lazy val vdrMemory = project
  .in(file("modules/vdr/memory"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-memory",
    libraryDependencies ++= D_Server.vdrDependencies,
  )
  .dependsOn(vdrCore)

lazy val vdrPrismNode = project
  .in(file("modules/vdr/prism-node"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-prism-node",
    libraryDependencies ++= D_Server.vdrDependencies,
  )
  .dependsOn(vdrCore, prismNodeClient, didApi, shared % "compile->compile;test->test")

lazy val vdrDatabase = project
  .in(file("modules/vdr/database"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-database",
    libraryDependencies ++= D_Server.vdrDependencies ++ D_Server.postgresDependencies,
    Test / libraryDependencies ++= Seq(
      "com.dimafeng" %% "testcontainers-scala-postgresql" % V.testContainersScala % Test
    ),
  )
  .dependsOn(vdrCore, shared)

lazy val vdrBlockfrost = project
  .in(file("modules/vdr/blockfrost"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-blockfrost",
    libraryDependencies ++= D_Server.vdrDependencies,
  )
  .dependsOn(vdrCore, shared)

lazy val vdrProxy = project
  .in(file("modules/vdr/proxy"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-proxy",
    libraryDependencies ++= D_Server.vdrDependencies ++ Seq(
      "com.h2database" % "h2" % "2.2.224"
    ),
    Test / libraryDependencies += "com.h2database" % "h2" % "2.2.224" % Test
  )
  .dependsOn(vdrCore, vdrPrismNode, vdrMemory, vdrDatabase, vdrBlockfrost, shared % "compile->compile;test->test")

lazy val apiServer = project
  .in(file("modules/api-server/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "identus-cloud-agent",
    fork := true,
    libraryDependencies ++= D_Server.serverDependencies,
    excludeDependencies ++= Seq(
      // Exclude `protobuf-javalite` from all dependencies since we're using scalapbRuntime which already include `protobuf-java`
      // Having both may introduce conflict on some api https://github.com/protocolbuffers/protobuf/issues/8104
      ExclusionRule("com.google.protobuf", "protobuf-javalite")
    ),
    Compile / mainClass := Some("org.hyperledger.identus.server.MainApp"),
    Docker / maintainer := "atala-coredid@iohk.io", // TODO: clarify the contact emale of the project
    Docker / dockerUsername := Some("hyperledgeridentus"), // https://hub.docker.com/u/hyperledgeridentus
    Docker / dockerRepository := Some("docker.io"),
    dockerExposedPorts := Seq(8085, 8090),
    dockerBaseImage := "eclipse-temurin:22-jdk-ubi9-minimal",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "org.hyperledger.identus.server.buildinfo",
    Compile / packageDoc / publishArtifact := false
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(walletManagement % "compile->compile;test->test")
  .dependsOn(
    apiServerConfig,
    apiServerHttpCore,
    apiServerJobsConnect,
    apiServerJobsIssue,
    apiServerJobsPresent,
    apiServerJobsStatusList,
    apiServerJobsDidSync,
    didCore,
    notificationsHttp,
    notificationsWebhook,
    credentialStatusHttp,
    verificationHttp,
    vdrHttp,
    connectionsHttp,
    didHttp,
    systemHttp,
    didcommHttp,
    credentialSchemaHttp,
    credentialDefinitionHttp,
    prexHttp,
    apiServerControllerCommons,
    issueHttp,
    presentProofHttp,
    oid4vciHttp,
    oid4vciCore,
    iamCore,
    iamEntityHttp,
    iamWalletHttp,
    sharedTest % "test->test",
    credentialsCore % "compile->compile;test->test",
    credentialsPersistenceDoobie,
    connectionsCore % "compile->compile;test->test", // Test is for MockConnectionService
    connectionsPersistenceDoobie,
    vdrService,
  )

// ############################
// ####  Release process  #####
// ############################
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations.*
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  ReleaseStep(releaseStepTask(apiServer / Docker / stage)),
  setNextVersion
)

// ################################
// ### Server sub-modules       ###
// ################################

lazy val apiServerConfig = project
  .in(file("modules/api-server/config"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-config")
  .dependsOn(apiServerHttpCore, iamCore)

lazy val apiServerHttpCore = project
  .in(file("modules/api-server/http-core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "api-server-http-core",
    libraryDependencies ++= Seq(
      D_Server.tapirJsonZio,
      D_Server.tapirZioHttpServer,
      D_Server.tapirSwaggerUiBundle,
      D_Server.tapirRedocBundle,
      D.zio,
      D.zioJson
    )
  )
  .dependsOn(shared, walletManagementApi)

// Domain HTTP modules
lazy val credentialStatusHttp = project
  .in(file("modules/credentials/credential-status-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credential-status-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, credentialsCore)

lazy val verificationHttp = project
  .in(file("modules/credentials/verification-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "verification-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, credentialsCore)

lazy val vdrHttp = project
  .in(file("modules/vdr/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "vdr-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, vdrCore)

lazy val oid4vciCore = project
  .in(file("modules/oid4vci/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "oid4vci-core",
    libraryDependencies ++= Seq(D.zio, D.nimbusJwt)
  )
  .dependsOn(credentialsVcJWT, didApi, sharedCrypto)

lazy val connectionsHttp = project
  .in(file("modules/connections/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "connections-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, connectionsApi, walletManagement)

lazy val didHttp = project
  .in(file("modules/did/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "did-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, didApi, walletManagement)

lazy val systemHttp = project
  .in(file("modules/api-server/system-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "system-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson, D.micrometer)
  )
  .dependsOn(apiServerHttpCore)

lazy val didcommHttp = project
  .in(file("modules/didcomm/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "didcomm-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, didcommAgent, didcommAgentDidcommx, connectionsApi, credentialsApi, walletManagement)

lazy val credentialSchemaHttp = project
  .in(file("modules/credentials/credential-schema-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credential-schema-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, credentialsCore, walletManagement)

lazy val credentialDefinitionHttp = project
  .in(file("modules/credentials/credential-definition-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "credential-definition-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, credentialsCore, walletManagement)

lazy val prexHttp = project
  .in(file("modules/credentials/prex-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "prex-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, credentialsPreX, credentialsCore)

lazy val oid4vciHttp = project
  .in(file("modules/oid4vci/http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "oid4vci-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson, D.nimbusJwt)
  )
  .dependsOn(apiServerHttpCore, iamCore, oid4vciCore, credentialsCore, walletManagement)

lazy val apiServerControllerCommons = project
  .in(file("modules/api-server/controller-commons"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-controller-commons")
  .dependsOn(apiServerHttpCore, connectionsCore, credentialsCore, didApi, didcommModels, walletManagement)

lazy val issueHttp = project
  .in(file("modules/credentials/issue-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "issue-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerControllerCommons, credentialsCore)

lazy val presentProofHttp = project
  .in(file("modules/credentials/presentproof-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "presentproof-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerControllerCommons, credentialsCore)

lazy val apiServerJobsCore = project
  .in(file("modules/api-server/jobs"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-core")
  .dependsOn(
    apiServerConfig,
    credentialsCore,
    credentialsVcJWT,
    didApi,
    didcommAgent,
    didcommAgentDidcommx,
    walletManagement,
    shared
  )

lazy val apiServerJobsConnect = project
  .in(file("modules/api-server/jobs-connect"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-connect")
  .dependsOn(apiServerJobsCore, connectionsCore)

lazy val apiServerJobsIssue = project
  .in(file("modules/api-server/jobs-issue"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-issue")
  .dependsOn(apiServerJobsCore, credentialsCore, credentialsVcJWT, credentialsSDJWT, credentialsAnoncreds)

lazy val apiServerJobsPresent = project
  .in(file("modules/api-server/jobs-present"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-present")
  .dependsOn(apiServerJobsCore, credentialsCore, credentialsVcJWT, credentialsSDJWT, credentialsAnoncreds, didApi)

lazy val apiServerJobsStatusList = project
  .in(file("modules/api-server/jobs-status-list"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-status-list")
  .dependsOn(apiServerJobsCore, credentialsCore, credentialsVcJWT)

lazy val apiServerJobsDidSync = project
  .in(file("modules/api-server/jobs-did-sync"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "api-server-jobs-did-sync")
  .dependsOn(apiServerJobsCore)

lazy val iamCore = project
  .in(file("modules/iam/core"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(name := "iam-core")
  .dependsOn(apiServerHttpCore, walletManagement)

lazy val iamEntityHttp = project
  .in(file("modules/iam/entity-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "iam-entity-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, iamCore, walletManagement)

lazy val iamWalletHttp = project
  .in(file("modules/iam/wallet-http"))
  .configure(commonConfigure)
  .settings(commonSetttings)
  .settings(
    name := "iam-wallet-http",
    libraryDependencies ++= Seq(D_Server.tapirJsonZio, D_Server.tapirZioHttpServer, D_Server.tapirSwaggerUiBundle, D.zio, D.zioJson)
  )
  .dependsOn(apiServerHttpCore, iamCore, walletManagement)

// Server controller grouping (by domain):
// - DID controllers: did/controller/
// - Connections controllers: connections/controller/
// - Credential issuance controllers: issue/controller/
// - Credential presentation controllers: presentproof/controller/
// - Credential schema/definition controllers: credentials/credentialschema/, credentials/credentialdefinition/
// - Credential status controllers: credentialstatus/controller/
// - DIDComm controllers: didcomm/controller/
// - Event controllers: event/controller/
// - IAM controllers: iam/entity/, iam/wallet/
// - OID4VCI controllers: oid4vci/
// - System controllers: system/controller/
// - VDR controllers: vdr/controller/
// - Verification controllers: verification/controller/


lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  // Shared
  shared,
  sharedJson,
  sharedCrypto,
  sharedTest,
  // DID
  didApi,
  didCore,
  didHttp,
  // DIDComm
  didcommApi,
  didcommModels,
  didcommResolver,
  didcommAgent,
  didcommAgentDidcommx,
  didcommVC,
  protocolConnection,
  protocolCoordinateMediation,
  protocolDidExchange,
  protocolInvitation,
  protocolLogin,
  protocolReportProblem,
  protocolRouting,
  protocolIssueCredential,
  protocolRevocationNotification,
  protocolPresentProof,
  protocolTrustPing,
  // Credentials
  credentialsApi,
  credentialsCore,
  credentialsPersistenceDoobie,
  credentialsVcJWT,
  credentialsSDJWT,
  credentialsAnoncreds,
  credentialsAnoncredsTest,
  credentialsPreX,
  credentialStatusHttp,
  verificationHttp,
  // Connections
  connectionsApi,
  connectionsCore,
  connectionsHttp,
  connectionsPersistenceDoobie,
  // Notifications
  notificationsApi,
  notifications,
  notificationsHttp,
  notificationsWebhook,
  // Wallet Management
  walletManagementApi,
  walletManagement,
  walletPersistenceDoobie,
  walletSecretsVault,
  // VDR
  vdrApi,
  vdrCore,
  vdrService,
  vdrPrismNode,
  vdrDatabase,
  vdrMemory,
  vdrBlockfrost,
  vdrProxy,
  vdrHttp,
  // DIDComm HTTP
  didcommHttp,
  // System HTTP
  systemHttp,
  // Credential Schema/Definition/PreX HTTP
  credentialSchemaHttp,
  credentialDefinitionHttp,
  prexHttp,
  // Controller commons + Issue/PresentProof HTTP
  apiServerControllerCommons,
  issueHttp,
  presentProofHttp,
  // Prism Node
  prismNodeClient,
  // OID4VCI
  oid4vciCore,
  oid4vciHttp,
  // API Server
  apiServerConfig,
  apiServerHttpCore,
  apiServer,
  apiServerJobsCore,
  apiServerJobsConnect,
  apiServerJobsIssue,
  apiServerJobsPresent,
  apiServerJobsStatusList,
  apiServerJobsDidSync,
  iamCore,
  iamEntityHttp,
  iamWalletHttp,
)

lazy val root = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

Global / excludeLintKeys ++= Set(
  vdrDatabase / Test / libraryDependencies,
  vdrProxy / Test / libraryDependencies
)
