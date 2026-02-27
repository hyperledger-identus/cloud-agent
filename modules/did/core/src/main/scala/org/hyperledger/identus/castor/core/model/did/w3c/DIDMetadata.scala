package org.hyperledger.identus.castor.core.model.did.w3c

// errors are based on https://www.w3.org/TR/did-spec-registries/#error
enum DIDResolutionErrorRepr(val value: String, val errorMessage: Option[String]) {
  case InvalidDID(msg: String) extends DIDResolutionErrorRepr("invalidDid", Some(msg))
  case InvalidDIDUrl(msg: String) extends DIDResolutionErrorRepr("invalidDidUrl", Some(msg))
  case NotFound extends DIDResolutionErrorRepr("notFound", None)
  case RepresentationNotSupported extends DIDResolutionErrorRepr("representationNotSupported", None)
  case InternalError(msg: String) extends DIDResolutionErrorRepr("internalError", Some(msg))
  case InvalidPublicKeyLength extends DIDResolutionErrorRepr("invalidPublicKeyLength", None)
  case InvalidPublicKeyType extends DIDResolutionErrorRepr("invalidPublicKeyType", None)
  case UnsupportedPublicKeyType extends DIDResolutionErrorRepr("unsupportedPublicKeyType", None)
}

final case class DIDDocumentMetadataRepr(
    deactivated: Boolean,
    canonicalId: Option[String],
    versionId: String,
    created: Option[String],
    updated: Option[String]
)
