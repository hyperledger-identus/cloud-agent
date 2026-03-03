package org.hyperledger.identus.credentials.anoncreds

trait AnoncredService {

  def createLinkSecret(): AnoncredLinkSecret

  def getCredDefIdFromOffer(offer: AnoncredCredentialOffer): String

  def getCredDefIdFromCredential(credential: AnoncredCredential): String

  def getSchemaIdFromCredential(credential: AnoncredCredential): String

  def createCredDefinition(
      issuerId: String,
      schema: AnoncredSchemaDef,
      tag: String,
      supportRevocation: Boolean,
  ): AnoncredCreateCredentialDefinition

  def createOffer(
      credDef: AnoncredCreateCredentialDefinition,
      credDefId: String,
  ): AnoncredCredentialOffer

  def createCredentialRequest(
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
      offer: AnoncredCredentialOffer,
      entropy: String = {
        val tmp = scala.util.Random()
        tmp.setSeed(java.security.SecureRandom.getInstanceStrong().nextLong())
        tmp.nextString(80)
      },
  ): AnoncredCreateCrendentialRequest

  def createCredential(
      cd: AnoncredCredentialDefinition,
      cdPrivate: AnoncredCredentialDefinitionPrivate,
      offer: AnoncredCredentialOffer,
      request: AnoncredCredentialRequest,
      attrValues: Seq[(String, String)],
  ): AnoncredCredential

  def processCredential(
      credential: AnoncredCredential,
      metadata: AnoncredCredentialRequestMetadata,
      linkSecret: AnoncredLinkSecretWithId,
      credDef: AnoncredCredentialDefinition,
  ): AnoncredCredential

  def createPresentation(
      request: AnoncredPresentationRequest,
      credRequests: Seq[AnoncredCredentialRequests],
      selfAttested: Map[String, String],
      linkSecret: AnoncredLinkSecret,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Either[Throwable, AnoncredPresentation]

  def verifyPresentation(
      presentation: AnoncredPresentation,
      request: AnoncredPresentationRequest,
      schemas: Map[String, AnoncredSchemaDef],
      credDefs: Map[String, AnoncredCredentialDefinition],
  ): Boolean
}
