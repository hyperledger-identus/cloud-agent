package org.hyperledger.identus.pollux.anoncreds

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

/** @see
  *   https://hyperledger.github.io/anoncreds-spec/
  */
object AnoncredLib {

  val SCHEMA_ID = "mock:uri2"
  val CRED_DEF_ID = "mock:uri2"
  val ISSUER_DID = "mock:issuer_id/path&q=bar"

  // issuer or any
  def createSchema(
      name: String, // SCHEMA_ID
      version: String, // SCHEMA_Version
      attr_names: AttributeNames,
      issuer_id: IssuerId, // ISSUER_DID
  ): AnoncredSchemaDef = uniffi.anoncreds_wrapper.Schema.apply(name, version, attr_names.toSeq.asJava, issuer_id)

  // issuer
  def createCredDefinition(
      issuer_id: String,
      schema: AnoncredSchemaDef,
      tag: String,
      supportRevocation: Boolean,
      signature_type: uniffi.anoncreds_wrapper.SignatureType.CL.type = uniffi.anoncreds_wrapper.SignatureType.CL
  ): AnoncredCreateCredentialDefinition = {
    val credentialDefinition: uniffi.anoncreds_wrapper.IssuerCreateCredentialDefinitionReturn =
      uniffi.anoncreds_wrapper
        .Issuer()
        .createCredentialDefinition(
          schema.name,
          schema: uniffi.anoncreds_wrapper.Schema,
          issuer_id,
          tag,
          signature_type,
          uniffi.anoncreds_wrapper.CredentialDefinitionConfig(supportRevocation)
        )

    AnoncredCreateCredentialDefinition(
      credentialDefinition.getCredentialDefinition(),
      credentialDefinition.getCredentialDefinitionPrivate(),
      credentialDefinition.getCredentialKeyCorrectnessProof()
    )
  }

  // issuer
  def createOffer(
      credentialDefinition: AnoncredCreateCredentialDefinition,
      credentialDefinitionId: String
  ): AnoncredCredentialOffer =
    uniffi.anoncreds_wrapper
      .Issuer()
      .createCredentialOffer(
        credentialDefinition.cd.schemaId, // string schema_id,
        credentialDefinitionId, // string cred_def_id,
        credentialDefinition.proofKey // CredentialKeyCorrectnessProof correctness_proof
      )

  // holder
  def createCredentialRequest(
      linkSecret: AnoncredLinkSecretWithId,
      credentialDefinition: AnoncredCredentialDefinition,
      credentialOffer: AnoncredCredentialOffer,
      entropy: String = {
        val tmp = scala.util.Random()
        tmp.setSeed(java.security.SecureRandom.getInstanceStrong().nextLong())
        tmp.nextString(80)
      }
  ): AnoncredCreateCrendentialRequest = {
    val credentialRequest =
      uniffi.anoncreds_wrapper
        .Prover()
        .createCredentialRequest(
          entropy, // string? entropy,
          null, // string? prover_did,
          credentialDefinition, // CredentialDefinition cred_def,
          linkSecret.secret, // LinkSecret link_secret,
          linkSecret.id, // string link_secret_id,
          credentialOffer, // CredentialOffer credential_offer
        )

    AnoncredCreateCrendentialRequest(credentialRequest.getRequest(), credentialRequest.getMetadata())
  }

  // holder
  def processCredential(
      credential: AnoncredCredential,
      metadata: AnoncredCredentialRequestMetadata,
      linkSecret: AnoncredLinkSecretWithId,
      credentialDefinition: AnoncredCredentialDefinition,
  ): AnoncredCredential = {
    uniffi.anoncreds_wrapper
      .Prover()
      .processCredential(
        credential, // Credential,
        metadata, //  CredentialRequestMetadata,
        linkSecret.secret, // LinkSecret,
        credentialDefinition, //  CredentialDefinition,
        null // evRegDef: RevocationRegistryDefinition?
      )
  }

  // issuer
  def createCredential(
      credentialDefinition: AnoncredCredentialDefinition,
      credentialDefinitionPrivate: AnoncredCredentialDefinitionPrivate,
      credentialOffer: AnoncredCredentialOffer,
      credentialRequest: AnoncredCredentialRequest,
      attributeValues: Seq[(String, String)]
      //  java.util.List[AttributeValues] : java.util.List[AttributeValues]
      //  revocationRegistryId : String
      //  revocationStatusList : RevocationStatusList
      //  credentialRevocationConfig : CredentialRevocationConfig
  ): AnoncredCredential = {
    uniffi.anoncreds_wrapper
      .Issuer()
      .createCredential(
        credentialDefinition, // CredentialDefinition cred_def,
        credentialDefinitionPrivate, // CredentialDefinitionPrivate cred_def_private,
        credentialOffer, // CredentialOffer cred_offer,
        credentialRequest, // CredentialRequest cred_request,
        attributeValues
          .map(e => uniffi.anoncreds_wrapper.AttributeValues(e._1, e._2))
          .asJava, // sequence<AttributeValues> cred_values,
        null, // RevocationRegistryId? rev_reg_id,
        null, // RevocationStatusList? rev_status_list,
        null, // CredentialRevocationConfig? revocation_config
      )
  }

  type SchemaId = String
  type CredentialDefinitionId = String

  // TODO FIX
  // [info] uniffi.anoncreds.AnoncredsException$CreatePresentationException: Create Presentation: Error: Error: Invalid structure
  // [info] Caused by: Predicate is not satisfied

  def createPresentation(
      presentationRequest: AnoncredPresentationRequest,
      credentialRequests: Seq[AnoncredCredentialRequests],
      selfAttested: Map[String, String],
      linkSecret: AnoncredLinkSecret,
      schemas: Map[SchemaId, AnoncredSchemaDef],
      credentialDefinitions: Map[CredentialDefinitionId, AnoncredCredentialDefinition],
  ): Either[uniffi.anoncreds_wrapper.AnoncredsException.CreatePresentationException, AnoncredPresentation] = {
    try {
      Right(
        uniffi.anoncreds_wrapper
          .Prover()
          .createPresentation(
            presentationRequest, // uniffi.anoncreds
            credentialRequests
              .map(i => i: uniffi.anoncreds_wrapper.CredentialRequests)
              .asJava, // sequence<Credential> credentials,
            selfAttested.asJava, // record<string, string>? self_attested,
            linkSecret, // LinkSecret link_secret,
            schemas.view
              .mapValues(i => i: uniffi.anoncreds_wrapper.Schema)
              .toMap
              .asJava, // record<SchemaId, Schema> schemas,
            credentialDefinitions.view.mapValues(i => i: uniffi.anoncreds_wrapper.CredentialDefinition).toMap.asJava
            // record<CredentialDefinitionId, CredentialDefinition> credential_definitions
          )
      )
    } catch {
      case ex: uniffi.anoncreds_wrapper.AnoncredsException.CreatePresentationException => Left(ex)
    }
  }

  // TODO FIX
  // uniffi.anoncreds.AnoncredsException$ProcessCredentialException: Verify Presentation: Error:
  // Requested restriction validation failed for "{"sex": Some("M")}" attributes [$and operator validation failed.
  // [$eq operator validation failed for tag: "attr::sex::value", value: "F" [Proof rejected: "attr::sex::value" values are different: expected: "F", actual: "M"]]]

  // TODO FIX
  // uniffi.anoncreds.AnoncredsException$ProcessCredentialException: Verify Presentation: Error: Requested restriction validation failed for "{"sex": Some("M")}" attributes [$and operator validation failed. [$eq operator validation failed for tag: "cred_def_id", value: "CRED_DEF_ID" [Proof rejected: "cred_def_id" values are different: expected: "CRED_DEF_ID", actual: "mock:uri3"]]]

  // FIXME its always return false ....
  def verifyPresentation(
      presentation: AnoncredPresentation,
      presentationRequest: AnoncredPresentationRequest,
      schemas: Map[SchemaId, AnoncredSchemaDef],
      credentialDefinitions: Map[CredentialDefinitionId, AnoncredCredentialDefinition],
  ): Boolean = {
    uniffi.anoncreds_wrapper
      .Verifier()
      .verifyPresentation(
        presentation, // Presentation presentation,
        presentationRequest, // PresentationRequest presentation_request,
        schemas.view
          .mapValues(i => i: uniffi.anoncreds_wrapper.Schema)
          .toMap
          .asJava, // record<SchemaId, Schema> schemas,
        credentialDefinitions.view.mapValues(i => i: uniffi.anoncreds_wrapper.CredentialDefinition).toMap.asJava
        // record<CredentialDefinitionId, CredentialDefinition> credential_definitions
      )
  }
}
