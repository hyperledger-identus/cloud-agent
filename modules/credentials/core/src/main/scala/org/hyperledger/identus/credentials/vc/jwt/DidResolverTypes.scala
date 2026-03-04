package org.hyperledger.identus.credentials.vc.jwt

import zio.*
import zio.json.ast.Json

import java.time.Instant
import scala.annotation.unused

trait DidResolver {
  def resolve(didUrl: String): UIO[DIDResolutionResult]
}

trait DIDResolutionResult

sealed case class DIDResolutionFailed(
    error: DIDResolutionError
) extends DIDResolutionResult

sealed case class DIDResolutionSucceeded(
    didDocument: DIDDocument,
    didDocumentMetadata: DIDDocumentMetadata
) extends DIDResolutionResult

sealed trait DIDResolutionError(@unused error: String, @unused message: String)
case class InvalidDid(message: String) extends DIDResolutionError("invalidDid", message)
case class NotFound(message: String) extends DIDResolutionError("notFound", message)
case class RepresentationNotSupported(message: String) extends DIDResolutionError("RepresentationNotSupported", message)
case class InvalidPublicKeyLength(message: String) extends DIDResolutionError("invalidPublicKeyLength", message)
case class InvalidPublicKeyType(message: String) extends DIDResolutionError("invalidPublicKeyType", message)
case class UnsupportedPublicKeyType(message: String) extends DIDResolutionError("unsupportedPublicKeyType", message)
case class Error(error: String, message: String) extends DIDResolutionError(error, message)

case class DIDDocumentMetadata(
    created: Option[Instant] = Option.empty,
    updated: Option[Instant] = Option.empty,
    deactivated: Option[Boolean] = Option.empty,
    versionId: Option[Instant] = Option.empty, // TODO: this probably should not be an instant, it should be a string
    nextUpdate: Option[Instant] = Option.empty,
    nextVersionId: Option[Instant] = Option.empty,
    equivalentId: Option[Instant] = Option.empty,
    canonicalId: Option[Instant] = Option.empty
)

case class DIDDocument(
    id: String,
    alsoKnowAs: Vector[String],
    controller: Vector[String],
    verificationMethod: Vector[VerificationMethod] = Vector.empty,
    authentication: Vector[VerificationMethodOrRef] = Vector.empty,
    assertionMethod: Vector[VerificationMethodOrRef] = Vector.empty,
    keyAgreement: Vector[VerificationMethodOrRef] = Vector.empty,
    capabilityInvocation: Vector[VerificationMethodOrRef] = Vector.empty,
    capabilityDelegation: Vector[VerificationMethodOrRef] = Vector.empty,
    service: Vector[Service] = Vector.empty
)

type VerificationMethodOrRef = VerificationMethod | String

case class VerificationMethod(
    id: String,
    `type`: String,
    controller: String,
    publicKeyBase58: Option[String] = Option.empty,
    publicKeyBase64: Option[String] = Option.empty,
    publicKeyJwk: Option[JsonWebKey] = Option.empty,
    publicKeyHex: Option[String] = Option.empty,
    publicKeyMultibase: Option[String] = Option.empty,
    blockchainAccountId: Option[String] = Option.empty,
    ethereumAddress: Option[String] = Option.empty
)

case class Service(id: String, `type`: String | Seq[String], serviceEndpoint: Json)
