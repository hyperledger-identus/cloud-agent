package org.hyperledger.identus.credentials.anoncreds

import uniffi.anoncreds_wrapper.{
  Credential as UniffiCredential,
  CredentialDefinition as UniffiCredentialDefinition,
  CredentialDefinitionPrivate as UniffiCredentialDefinitionPrivate,
  CredentialKeyCorrectnessProof as UniffiCredentialKeyCorrectnessProof,
  CredentialOffer as UniffiCredentialOffer,
  CredentialRequest as UniffiCredentialRequest,
  CredentialRequestMetadata as UniffiCredentialRequestMetadata,
  CredentialRequests as UniffiCredentialRequests,
  LinkSecret as UniffiLinkSecret,
  Nonce,
  Presentation as UniffiPresentation,
  PresentationRequest as UniffiPresentationRequest,
  Schema as UniffiSchema
}

import scala.jdk.CollectionConverters.*

// Extension methods for Uniffi-dependent operations on case classes defined in credentialsCore
extension (cd: AnoncredCredentialDefinition) {
  def schemaId: String = AnoncredConversions
    .credDefToUniffi(cd)
    .getSchemaId()
}

extension (offer: AnoncredCredentialOffer) {
  def schemaId: String = AnoncredConversions
    .credOfferToUniffi(offer)
    .getSchemaId()
  def credDefId: String = AnoncredConversions
    .credOfferToUniffi(offer)
    .getCredDefId()
}

extension (cred: AnoncredCredential) {
  def credDefId: String = AnoncredConversions
    .credToUniffi(cred)
    .getCredDefId
}

// Factory methods that depend on Uniffi
object AnoncredLinkSecretFactory {
  def create(): AnoncredLinkSecret =
    AnoncredConversions.uniffiLinkSecretToScala(UniffiLinkSecret())

  def createWithId(id: String): AnoncredLinkSecretWithId = AnoncredLinkSecretWithId(id, create())
}

// All Uniffi conversions in a single object to avoid shadowing case classes from credentialsCore
object AnoncredConversions {

  // LinkSecret
  given Conversion[AnoncredLinkSecret, UniffiLinkSecret] with {
    def apply(linkSecret: AnoncredLinkSecret): UniffiLinkSecret =
      UniffiLinkSecret.Companion.newFromValue(linkSecret.data)
  }

  given Conversion[UniffiLinkSecret, AnoncredLinkSecret] with {
    def apply(uniffiLinkSecret: UniffiLinkSecret): AnoncredLinkSecret =
      AnoncredLinkSecret(uniffiLinkSecret.getValue())
  }

  def uniffiLinkSecretToScala(ls: UniffiLinkSecret): AnoncredLinkSecret = AnoncredLinkSecret(ls.getValue())

  // SchemaDef
  given Conversion[AnoncredSchemaDef, UniffiSchema] with {
    def apply(schemaDef: AnoncredSchemaDef): UniffiSchema =
      UniffiSchema.apply(
        schemaDef.name,
        schemaDef.version,
        schemaDef.attributes.toSeq.asJava,
        schemaDef.issuer_id
      )
  }

  given Conversion[UniffiSchema, AnoncredSchemaDef] with {
    def apply(schema: UniffiSchema): AnoncredSchemaDef =
      AnoncredSchemaDef(
        name = schema.getName(),
        version = schema.getVersion(),
        attributes = schema.getAttrNames().asScala.toSet,
        issuer_id = schema.getIssuerId(),
      )
  }

  // CredentialDefinition
  def credDefToUniffi(cd: AnoncredCredentialDefinition): UniffiCredentialDefinition =
    UniffiCredentialDefinition(cd.data)

  given Conversion[AnoncredCredentialDefinition, UniffiCredentialDefinition] with {
    def apply(credentialDefinition: AnoncredCredentialDefinition): UniffiCredentialDefinition =
      credDefToUniffi(credentialDefinition)
  }

  given Conversion[UniffiCredentialDefinition, AnoncredCredentialDefinition] with {
    def apply(credentialDefinition: UniffiCredentialDefinition): AnoncredCredentialDefinition =
      AnoncredCredentialDefinition(credentialDefinition.getJson())
  }

  // CredentialDefinitionPrivate
  given Conversion[AnoncredCredentialDefinitionPrivate, UniffiCredentialDefinitionPrivate] with {
    def apply(credentialDefinitionPrivate: AnoncredCredentialDefinitionPrivate): UniffiCredentialDefinitionPrivate =
      UniffiCredentialDefinitionPrivate(credentialDefinitionPrivate.data)
  }

  given Conversion[UniffiCredentialDefinitionPrivate, AnoncredCredentialDefinitionPrivate] with {
    def apply(credentialDefinitionPrivate: UniffiCredentialDefinitionPrivate): AnoncredCredentialDefinitionPrivate =
      AnoncredCredentialDefinitionPrivate(credentialDefinitionPrivate.getJson())
  }

  // CredentialKeyCorrectnessProof
  given Conversion[AnoncredCredentialKeyCorrectnessProof, UniffiCredentialKeyCorrectnessProof] with {
    def apply(
        credentialKeyCorrectnessProof: AnoncredCredentialKeyCorrectnessProof
    ): UniffiCredentialKeyCorrectnessProof =
      UniffiCredentialKeyCorrectnessProof(credentialKeyCorrectnessProof.data)
  }

  given Conversion[UniffiCredentialKeyCorrectnessProof, AnoncredCredentialKeyCorrectnessProof] with {
    def apply(
        credentialKeyCorrectnessProof: UniffiCredentialKeyCorrectnessProof
    ): AnoncredCredentialKeyCorrectnessProof =
      AnoncredCredentialKeyCorrectnessProof(credentialKeyCorrectnessProof.getJson())
  }

  // CredentialOffer
  def credOfferToUniffi(offer: AnoncredCredentialOffer): UniffiCredentialOffer =
    UniffiCredentialOffer(offer.data)

  given Conversion[AnoncredCredentialOffer, UniffiCredentialOffer] with {
    def apply(credentialOffer: AnoncredCredentialOffer): UniffiCredentialOffer =
      credOfferToUniffi(credentialOffer)
  }

  given Conversion[UniffiCredentialOffer, AnoncredCredentialOffer] with {
    def apply(credentialOffer: UniffiCredentialOffer): AnoncredCredentialOffer =
      AnoncredCredentialOffer(credentialOffer.getJson())
  }

  // CredentialRequest
  given Conversion[AnoncredCredentialRequest, UniffiCredentialRequest] with {
    def apply(credentialRequest: AnoncredCredentialRequest): UniffiCredentialRequest =
      UniffiCredentialRequest(credentialRequest.data)
  }

  given Conversion[UniffiCredentialRequest, AnoncredCredentialRequest] with {
    def apply(credentialRequest: UniffiCredentialRequest): AnoncredCredentialRequest =
      AnoncredCredentialRequest(credentialRequest.getJson())
  }

  // CredentialRequestMetadata
  given Conversion[AnoncredCredentialRequestMetadata, UniffiCredentialRequestMetadata] with {
    def apply(credentialRequestMetadata: AnoncredCredentialRequestMetadata): UniffiCredentialRequestMetadata =
      UniffiCredentialRequestMetadata(
        /*link_secret_blinding_data*/ credentialRequestMetadata.linkSecretBlinding,
        /*nonce*/ Nonce.Companion.newFromValue(credentialRequestMetadata.nonce),
        /*link_secret_name*/ credentialRequestMetadata.linkSecretName,
      )
  }

  given Conversion[UniffiCredentialRequestMetadata, AnoncredCredentialRequestMetadata] with {
    def apply(credentialRequestMetadata: UniffiCredentialRequestMetadata): AnoncredCredentialRequestMetadata =
      AnoncredCredentialRequestMetadata(
        linkSecretBlinding = credentialRequestMetadata.getLinkSecretBlindingData(),
        nonce = credentialRequestMetadata.getNonce().getValue(),
        linkSecretName = credentialRequestMetadata.getLinkSecretName(),
      )
  }

  // Credential
  def credToUniffi(cred: AnoncredCredential): UniffiCredential = UniffiCredential(cred.data)

  given Conversion[AnoncredCredential, UniffiCredential] with {
    def apply(credential: AnoncredCredential): UniffiCredential =
      credToUniffi(credential)
  }

  given Conversion[UniffiCredential, AnoncredCredential] with {
    def apply(credential: UniffiCredential): AnoncredCredential =
      AnoncredCredential(credential.getJson())
  }

  // CredentialRequests
  given Conversion[AnoncredCredentialRequests, UniffiCredentialRequests] with {
    import uniffi.anoncreds_wrapper.RequestedAttribute
    import uniffi.anoncreds_wrapper.RequestedPredicate
    def apply(credentialRequests: AnoncredCredentialRequests): UniffiCredentialRequests = {
      val credential = credToUniffi(credentialRequests.credential)
      val requestedAttributes = credentialRequests.requestedAttribute.map(a => RequestedAttribute(a, true))
      val requestedPredicates = credentialRequests.requestedPredicate.map(p => RequestedPredicate(p))
      UniffiCredentialRequests(credential, requestedAttributes.asJava, requestedPredicates.asJava)
    }
  }

  given Conversion[UniffiCredentialRequests, AnoncredCredentialRequests] with {
    def apply(credentialRequests: UniffiCredentialRequests): AnoncredCredentialRequests = {
      AnoncredCredentialRequests(
        AnoncredCredential(credentialRequests.getCredential().getJson()),
        credentialRequests
          .getRequestedAttribute()
          .asScala
          .toSeq
          .filter(e => e.getRevealed())
          .map(e => e.getReferent()),
        credentialRequests
          .getRequestedPredicate()
          .asScala
          .toSeq
          .map(e => e.getReferent())
      )
    }
  }

  // PresentationRequest
  given Conversion[AnoncredPresentationRequest, UniffiPresentationRequest] with {
    def apply(presentationRequest: AnoncredPresentationRequest): UniffiPresentationRequest =
      UniffiPresentationRequest(presentationRequest.data)
  }

  given Conversion[UniffiPresentationRequest, AnoncredPresentationRequest] with {
    def apply(presentationRequest: UniffiPresentationRequest): AnoncredPresentationRequest =
      AnoncredPresentationRequest(presentationRequest.getJson())
  }

  // Presentation
  given Conversion[AnoncredPresentation, UniffiPresentation] with {
    def apply(presentation: AnoncredPresentation): UniffiPresentation = {
      UniffiPresentation(presentation.data)
    }
  }

  given Conversion[UniffiPresentation, AnoncredPresentation] with {
    def apply(presentation: UniffiPresentation): AnoncredPresentation = {
      AnoncredPresentation(presentation.getJson())
    }
  }
}
