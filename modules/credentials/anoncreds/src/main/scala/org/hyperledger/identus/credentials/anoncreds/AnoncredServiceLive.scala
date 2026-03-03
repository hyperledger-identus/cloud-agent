package org.hyperledger.identus.credentials.anoncreds

import zio.*

class AnoncredServiceLive extends AnoncredService {

  override def createLinkSecret(): AnoncredLinkSecret =
    AnoncredLinkSecretFactory.create()

  override def getCredDefIdFromOffer(offer: AnoncredCredentialOffer): String =
    offer.credDefId

  override def getCredDefIdFromCredential(credential: AnoncredCredential): String =
    credential.credDefId

  override def getSchemaIdFromCredential(credential: AnoncredCredential): String = {
    import scala.language.implicitConversions
    import AnoncredConversions.given
    val uniffiCred: uniffi.anoncreds_wrapper.Credential = credential
    uniffiCred.getSchemaId()
  }

  override def createCredDefinition(
      issuerId: String,
      schema: AnoncredSchemaDef,
      tag: String,
      supportRevocation: Boolean,
  ): AnoncredCreateCredentialDefinition =
    AnoncredLib.createCredDefinition(issuerId, schema, tag, supportRevocation)

  override def createOffer(
      credDef: AnoncredCreateCredentialDefinition,
      credDefId: String,
  ): AnoncredCredentialOffer =
    AnoncredLib.createOffer(credDef, credDefId)

  override def createCredentialRequest(
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
      offer: AnoncredCredentialOffer,
      entropy: String,
  ): AnoncredCreateCrendentialRequest =
    AnoncredLib.createCredentialRequest(linkSecret, credDef, offer, entropy)

  override def createCredential(
      cd: AnoncredCredentialDefinition,
      cdPrivate: AnoncredCredentialDefinitionPrivate,
      offer: AnoncredCredentialOffer,
      request: AnoncredCredentialRequest,
      attrValues: Seq[(String, String)],
  ): AnoncredCredential =
    AnoncredLib.createCredential(cd, cdPrivate, offer, request, attrValues)

  override def processCredential(
      credential: AnoncredCredential,
      metadata: AnoncredCredentialRequestMetadata,
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
  ): AnoncredCredential =
    AnoncredLib.processCredential(credential, metadata, linkSecret, credDef)

  override def createPresentation(
      request: AnoncredPresentationRequest,
      credRequests: Seq[AnoncredCredentialRequests],
      selfAttested: Map[String, String],
      linkSecret: AnoncredLinkSecret,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Either[Throwable, AnoncredPresentation] =
    AnoncredLib.createPresentation(request, credRequests, selfAttested, linkSecret, schemas, credDefs)

  override def verifyPresentation(
      presentation: AnoncredPresentation,
      request: AnoncredPresentationRequest,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Boolean =
    AnoncredLib.verifyPresentation(presentation, request, schemas, credDefs)
}

object AnoncredServiceLive {
  val layer: ULayer[AnoncredService] = ZLayer.succeed(AnoncredServiceLive())
}
