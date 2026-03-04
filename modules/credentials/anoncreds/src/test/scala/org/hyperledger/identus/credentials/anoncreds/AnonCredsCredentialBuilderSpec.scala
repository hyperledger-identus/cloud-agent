package org.hyperledger.identus.credentials.anoncreds

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object AnonCredsCredentialBuilderSpec extends ZIOSpecDefault:

  private val stubCredDef = AnoncredCredentialDefinition("{}")
  private val stubCredDefPrivate = AnoncredCredentialDefinitionPrivate("{}")
  private val stubOffer = AnoncredCredentialOffer("{}")
  private val stubRequest = AnoncredCredentialRequest("{}")

  /** Stub AnoncredService that returns a fixed credential */
  private object StubAnoncredService extends AnoncredService:
    def createLinkSecret(): AnoncredLinkSecret = AnoncredLinkSecret("stub")
    def getCredDefIdFromOffer(offer: AnoncredCredentialOffer): String = "cred-def-1"
    def getCredDefIdFromCredential(credential: AnoncredCredential): String = "cred-def-1"
    def getSchemaIdFromCredential(credential: AnoncredCredential): String = "schema-1"
    def createCredDefinition(
        issuerId: String,
        schema: AnoncredSchemaDef,
        tag: String,
        supportRevocation: Boolean,
    ): AnoncredCreateCredentialDefinition =
      AnoncredCreateCredentialDefinition(stubCredDef, stubCredDefPrivate, AnoncredCredentialKeyCorrectnessProof("{}"))
    def createOffer(
        credDef: AnoncredCreateCredentialDefinition,
        credDefId: String,
    ): AnoncredCredentialOffer = stubOffer
    def createCredentialRequest(
        linkSecret: AnoncredLinkSecretWithId,
        credDef: AnoncredCredentialDefinition,
        offer: AnoncredCredentialOffer,
        entropy: String,
    ): AnoncredCreateCrendentialRequest =
      AnoncredCreateCrendentialRequest(stubRequest, AnoncredCredentialRequestMetadata("", "", ""))
    def createCredential(
        cd: AnoncredCredentialDefinition,
        cdPrivate: AnoncredCredentialDefinitionPrivate,
        offer: AnoncredCredentialOffer,
        request: AnoncredCredentialRequest,
        attrValues: Seq[(String, String)],
    ): AnoncredCredential =
      val attrsJson = attrValues.map((k, v) => s""""$k":"$v"""").mkString("{", ",", "}")
      AnoncredCredential(attrsJson)
    def processCredential(
        credential: AnoncredCredential,
        metadata: AnoncredCredentialRequestMetadata,
        linkSecret: AnoncredLinkSecretWithId,
        credDef: AnoncredCredentialDefinition,
    ): AnoncredCredential = credential
    def createPresentation(
        request: AnoncredPresentationRequest,
        credRequests: Seq[AnoncredCredentialRequests],
        selfAttested: Map[String, String],
        linkSecret: AnoncredLinkSecret,
        schemas: Map[String, AnoncredSchemaDef],
        credDefs: Map[String, AnoncredCredentialDefinition],
    ): Either[Throwable, AnoncredPresentation] = Right(AnoncredPresentation("{}"))
    def verifyPresentation(
        presentation: AnoncredPresentation,
        request: AnoncredPresentationRequest,
        schemas: Map[String, AnoncredSchemaDef],
        credDefs: Map[String, AnoncredCredentialDefinition],
    ): Boolean = true

  private object StubContextResolver extends AnonCredsCredentialBuilder.CredentialContext.Resolver:
    def resolve(keyRef: KeyRef): IO[Throwable, AnonCredsCredentialBuilder.CredentialContext] =
      ZIO.succeed(AnonCredsCredentialBuilder.CredentialContext(
        credentialDefinition = stubCredDef,
        credentialDefinitionPrivate = stubCredDefPrivate,
        offer = stubOffer,
        request = stubRequest,
      ))

  private val claims: Json = """{"name":"Alice","age":"31"}""".fromJson[Json].toOption.get
  private val keyRef = KeyRef("test-key-id", SignatureAlgorithm.EdDSA)

  private val builder = AnonCredsCredentialBuilder(
    anoncredService = StubAnoncredService,
    contextResolver = StubContextResolver,
  )

  override def spec = suite("AnonCredsCredentialBuilder")(
    test("format is AnonCreds") {
      assertTrue(builder.format == CredentialFormat.AnonCreds)
    },
    test("supports AnonCreds data model") {
      assertTrue(builder.supportedDataModels.contains(DataModelType.AnonCreds))
    },
    test("steps are non-empty") {
      assertTrue(builder.steps.nonEmpty)
    },
    test("buildCredential produces AnonCreds output") {
      val ctx = BuildContext(
        claims = claims,
        format = CredentialFormat.AnonCreds,
        dataModel = DataModelType.AnonCreds,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
      )
      for built <- builder.buildCredential(ctx)
      yield
        val credStr = new String(built.raw.data, "UTF-8")
        assertTrue(
          built.raw.format == CredentialFormat.AnonCreds,
          credStr.contains("Alice"),
          credStr.contains("31"),
        )
    },
    test("extracts attributes from claims JSON") {
      val ctx = BuildContext(
        claims = """{"name":"Bob","active":"true"}""".fromJson[Json].toOption.get,
        format = CredentialFormat.AnonCreds,
        dataModel = DataModelType.AnonCreds,
        issuerDid = "did:example:issuer",
        keyRef = keyRef,
      )
      for built <- builder.buildCredential(ctx)
      yield
        val credStr = new String(built.raw.data, "UTF-8")
        assertTrue(
          credStr.contains("Bob"),
          credStr.contains("true"),
        )
    },
  )
