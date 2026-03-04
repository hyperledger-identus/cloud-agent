package org.hyperledger.identus.credentials.vc.jwt

import org.hyperledger.identus.did.core.model.did.w3c.*
import org.hyperledger.identus.did.core.service.DIDService
import zio.*

/** An adapter for translating Castor resolver to resolver defined in JWT library */
class PrismDidResolver(didService: DIDService) extends DidResolver {

  private val w3cResolver = makeW3CResolver(didService)

  override def resolve(didUrl: String): UIO[DIDResolutionResult] = {
    w3cResolver(didUrl)
      .fold(
        toPolluxResolutionErrorModel,
        { case (didDocumentMetadata, didDocument) =>
          DIDResolutionSucceeded(
            didDocument = toPolluxDIDDocumentModel(didDocument),
            didDocumentMetadata = DIDDocumentMetadata(
              deactivated = Some(didDocumentMetadata.deactivated)
            )
          )
        }
      )
  }

  private def toPolluxDIDDocumentModel(didDocument: DIDDocumentRepr): DIDDocument = {
    DIDDocument(
      id = didDocument.id,
      alsoKnowAs = Vector.empty,
      controller = Vector(didDocument.controller),
      verificationMethod = didDocument.verificationMethod.map(toPolluxVerificationMethodModel).toVector,
      authentication = didDocument.authentication.map(toPolluxVerificationMethodOrRefModel).toVector,
      assertionMethod = didDocument.assertionMethod.map(toPolluxVerificationMethodOrRefModel).toVector,
      keyAgreement = didDocument.keyAgreement.map(toPolluxVerificationMethodOrRefModel).toVector,
      capabilityInvocation = didDocument.capabilityInvocation.map(toPolluxVerificationMethodOrRefModel).toVector,
      capabilityDelegation = didDocument.capabilityDelegation.map(toPolluxVerificationMethodOrRefModel).toVector,
      service = didDocument.service.map(toPolluxServiceModel).toVector
    )
  }

  private def toPolluxResolutionErrorModel(error: DIDResolutionErrorRepr): DIDResolutionFailed = {
    val e = error match {
      case DIDResolutionErrorRepr.InvalidDID(_)              => InvalidDid(error.value)
      case DIDResolutionErrorRepr.InvalidDIDUrl(_)           => InvalidDid(error.value)
      case DIDResolutionErrorRepr.NotFound                   => NotFound(error.value)
      case DIDResolutionErrorRepr.RepresentationNotSupported => RepresentationNotSupported(error.value)
      case DIDResolutionErrorRepr.InternalError(_)           => Error(error.value, error.value)
      case DIDResolutionErrorRepr.InvalidPublicKeyLength     => InvalidPublicKeyLength(error.value)
      case DIDResolutionErrorRepr.InvalidPublicKeyType       => InvalidPublicKeyType(error.value)
      case DIDResolutionErrorRepr.UnsupportedPublicKeyType   => UnsupportedPublicKeyType(error.value)
    }
    DIDResolutionFailed(e)
  }

  private def toPolluxServiceModel(service: ServiceRepr): Service = {
    Service(
      id = service.id,
      `type` = service.`type`,
      serviceEndpoint = service.serviceEndpoint
    )
  }

  private def toPolluxVerificationMethodModel(verificationMethod: PublicKeyRepr): VerificationMethod = {
    VerificationMethod(
      id = verificationMethod.id,
      `type` = verificationMethod.`type`,
      controller = verificationMethod.controller,
      publicKeyJwk = Some(toPolluxJwtModel(verificationMethod.publicKeyJwk))
    )
  }

  private def toPolluxVerificationMethodOrRefModel(verificationMethod: PublicKeyReprOrRef): VerificationMethodOrRef = {
    verificationMethod match {
      case uri: String       => uri
      case pk: PublicKeyRepr => toPolluxVerificationMethodModel(pk)
    }
  }

  private def toPolluxJwtModel(jwk: PublicKeyJwk): JsonWebKey = {
    JsonWebKey(
      crv = Some(jwk.crv),
      kty = jwk.kty,
      x = jwk.x,
      y = jwk.y
    )
  }

}

object PrismDidResolver {
  val layer: URLayer[DIDService, DidResolver] = ZLayer.fromFunction(PrismDidResolver(_))
}
