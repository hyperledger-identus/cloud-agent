package org.hyperledger.identus.vc.dif

type DID = String

case class CredentialManifest(
    issuer: DID,
    credential: NameAndSchema,
)

case class NameAndSchema(
    name: String,
    schema: String,
)

///////////////////////////////////////////////////////////

object ProposeCredentialFormat {
  def formatDIF = "dif/credential-manifest@v1.0"
}

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0511-dif-cred-manifest-attach/README.md#propose-credential-attachment-format
  */
final case class ProposeCredentialFormat(
    issuer: DID,
    credential: NameAndSchema,
)

///////////////////////////////////////////////////////////

object OfferCredentialFormat {
  def formatDIF = "dif/credential-manifest@v1.0"
}

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0511-dif-cred-manifest-attach/README.md#offer-credential-attachment-format
  */
final case class OfferCredentialFormat(
    challenge: String,
    domain: String,
    credential_manifest: CredentialManifestObject,
)

type CredentialManifestObject = Any

///////////////////////////////////////////////////////////

object RequestCredentialFormat {
  def formatDIF = "dif/credential-manifest@v1.0"
}

/** @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0511-dif-cred-manifest-attach/README.md#request-credential-attachment-format
  *
  * @param `credential-manifest`
  *   Is OPTIONAL. But required if the Holder starts the protocol with request-credential.
  * @param `presentation-submission`
  *   Is OPTIONAL. But required as a response to the presentation_definition attribute in the Issuer's credential
  *   manifest, if present.
  */
case class RequestCredentialFormat(
    `credential-manifest`: Option[CredentialManifest],
    `presentation-submission`: PresentationSubmissionObject,
)

type PresentationSubmissionObject = Any

///////////////////////////////////////////////////////////

//issue-credential
