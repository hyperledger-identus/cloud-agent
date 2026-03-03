package org.hyperledger.identus.credentials.anoncreds

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

type AttributeNames = Set[String]
type IssuerId = String

case class AnoncredLinkSecretWithId(id: String, secret: AnoncredLinkSecret) { def data = secret.data }

case class AnoncredLinkSecret(data: String)

case class AnoncredSchemaDef(
    name: String,
    version: String,
    attributes: AttributeNames,
    issuer_id: IssuerId,
)

case class AnoncredCredentialDefinition(data: String)

case class AnoncredCredentialDefinitionPrivate(data: String)

case class AnoncredCredentialKeyCorrectnessProof(data: String)

case class AnoncredCreateCredentialDefinition(
    cd: AnoncredCredentialDefinition,
    cdPrivate: AnoncredCredentialDefinitionPrivate,
    proofKey: AnoncredCredentialKeyCorrectnessProof,
)

case class AnoncredCredentialOffer(data: String)

case class AnoncredCreateCrendentialRequest(
    request: AnoncredCredentialRequest,
    metadata: AnoncredCredentialRequestMetadata,
)

case class AnoncredCredentialRequest(data: String)

case class AnoncredCredentialRequestMetadata(
    linkSecretBlinding: String,
    nonce: String,
    linkSecretName: String,
)
object AnoncredCredentialRequestMetadata {
  given JsonDecoder[AnoncredCredentialRequestMetadata] = DeriveJsonDecoder.gen[AnoncredCredentialRequestMetadata]
  given JsonEncoder[AnoncredCredentialRequestMetadata] = DeriveJsonEncoder.gen[AnoncredCredentialRequestMetadata]
}

case class AnoncredCredential(data: String)

case class AnoncredCredentialRequests(
    credential: AnoncredCredential,
    requestedAttribute: Seq[String],
    requestedPredicate: Seq[String],
)

case class AnoncredPresentationRequest(data: String)

case class AnoncredPresentation(data: String)
