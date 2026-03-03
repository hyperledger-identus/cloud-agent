package org.hyperledger.identus.credentials.anoncreds

import zio.*

class AnoncredServiceStub extends AnoncredService {

  override def createLinkSecret(): AnoncredLinkSecret =
    throw UnsupportedOperationException("AnoncredServiceStub: createLinkSecret not supported in tests")

  override def getCredDefIdFromOffer(offer: AnoncredCredentialOffer): String =
    throw UnsupportedOperationException("AnoncredServiceStub: getCredDefIdFromOffer not supported in tests")

  override def getCredDefIdFromCredential(credential: AnoncredCredential): String =
    throw UnsupportedOperationException("AnoncredServiceStub: getCredDefIdFromCredential not supported in tests")

  override def getSchemaIdFromCredential(credential: AnoncredCredential): String =
    throw UnsupportedOperationException("AnoncredServiceStub: getSchemaIdFromCredential not supported in tests")

  override def createCredDefinition(
      issuerId: String,
      schema: AnoncredSchemaDef,
      tag: String,
      supportRevocation: Boolean,
  ): AnoncredCreateCredentialDefinition =
    throw UnsupportedOperationException("AnoncredServiceStub: createCredDefinition not supported in tests")

  override def createOffer(
      credDef: AnoncredCreateCredentialDefinition,
      credDefId: String,
  ): AnoncredCredentialOffer =
    throw UnsupportedOperationException("AnoncredServiceStub: createOffer not supported in tests")

  override def createCredentialRequest(
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
      offer: AnoncredCredentialOffer,
      entropy: String,
  ): AnoncredCreateCrendentialRequest =
    throw UnsupportedOperationException("AnoncredServiceStub: createCredentialRequest not supported in tests")

  override def createCredential(
      cd: AnoncredCredentialDefinition,
      cdPrivate: AnoncredCredentialDefinitionPrivate,
      offer: AnoncredCredentialOffer,
      request: AnoncredCredentialRequest,
      attrValues: Seq[(String, String)],
  ): AnoncredCredential =
    throw UnsupportedOperationException("AnoncredServiceStub: createCredential not supported in tests")

  override def processCredential(
      credential: AnoncredCredential,
      metadata: AnoncredCredentialRequestMetadata,
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
  ): AnoncredCredential =
    throw UnsupportedOperationException("AnoncredServiceStub: processCredential not supported in tests")

  override def createPresentation(
      request: AnoncredPresentationRequest,
      credRequests: Seq[AnoncredCredentialRequests],
      selfAttested: Map[String, String],
      linkSecret: AnoncredLinkSecret,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Either[Throwable, AnoncredPresentation] =
    throw UnsupportedOperationException("AnoncredServiceStub: createPresentation not supported in tests")

  override def verifyPresentation(
      presentation: AnoncredPresentation,
      request: AnoncredPresentationRequest,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Boolean =
    throw UnsupportedOperationException("AnoncredServiceStub: verifyPresentation not supported in tests")
}

object AnoncredServiceStub {
  val layer: ULayer[AnoncredService] = ZLayer.succeed(AnoncredServiceStub())
}
