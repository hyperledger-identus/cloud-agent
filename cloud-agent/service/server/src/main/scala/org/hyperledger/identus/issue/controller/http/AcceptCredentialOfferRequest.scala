package org.hyperledger.identus.issue.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.issue.controller.http.AcceptCredentialOfferRequest.annotations
import sttp.tapir.{Schema, Validator}
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/** A request to accept a credential offer received from an issuer.
  *
  * @param subjectId
  *   The short-form subject Prism DID to which the verifiable credential should be issued. for example:
  *   ''did:prism:3bb0505d13fcb04d28a48234edb27b0d4e6d7e18a81e2c1abab58f3bbc21ce6f''
  */
final case class AcceptCredentialOfferRequest(
    @description(annotations.subjectId.description)
    @encodedExample(annotations.subjectId.example)
    subjectId: Option[String],
    @description(annotations.subjectId.description)
    @encodedExample(annotations.subjectId.example)
    keyId: Option[String]
)

object AcceptCredentialOfferRequest {

  object annotations {
    object subjectId
        extends Annotation[Option[String]](
          description = """
          |The short-form subject Prism DID to which the JWT verifiable credential will be issued.
          |This parameter only applies if the offer is of type 'JWT'.
          |""".stripMargin,
          example = Some("did:prism:3bb0505d13fcb04d28a48234edb27b0d4e6d7e18a81e2c1abab58f3bbc21ce6f")
        )
    object keyId
        extends Annotation[Option[String]](
          description = """
                        |The KeyID (KID / key ID) parameter is used to identify a specific key within
                        |the subject Prism DID, allowing for the selection of a particular key when the
                        |DID contains multiple keys. This is relevant for issuing the SDJWT verifiable credential.
                        |This parameter is only applicable if the offer type is 'SDJWT'.
                        |SDJWT credential issued will be bound to this key.
                        |""".stripMargin,
          example = Some("my-auth-key")
        )
  }

  given encoder: JsonEncoder[AcceptCredentialOfferRequest] =
    DeriveJsonEncoder.gen[AcceptCredentialOfferRequest]

  given decoder: JsonDecoder[AcceptCredentialOfferRequest] =
    DeriveJsonDecoder.gen[AcceptCredentialOfferRequest]

  given schema: Schema[AcceptCredentialOfferRequest] = Schema.derived

}
