package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.controller.http.VerificationMethod.annotations
import org.hyperledger.identus.castor.core.model.did.w3c
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import scala.language.implicitConversions

@description(
  "A cryptographic public key expressed in the DID document. https://www.w3.org/TR/did-core/#verification-methods"
)
final case class VerificationMethod(
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: String,
    @description(annotations.`type`.description)
    @encodedExample(annotations.`type`.example)
    `type`: String,
    @description(annotations.controller.description)
    @encodedExample(annotations.controller.example)
    controller: String,
    publicKeyJwk: PublicKeyJwk
)

object VerificationMethod {
  object annotations {
    object id
        extends Annotation[String](
          description = "The identifier for the verification method.",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff#key-1"
        )

    object `type`
        extends Annotation[String](
          description = "The type of the verification method.",
          example = "JsonWebKey2020"
        )

    object controller
        extends Annotation[String](
          description = "The DID that controls the verification method.",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )
  }

  given encoder: JsonEncoder[VerificationMethod] = DeriveJsonEncoder.gen[VerificationMethod]
  given decoder: JsonDecoder[VerificationMethod] = DeriveJsonDecoder.gen[VerificationMethod]
  given schema: Schema[VerificationMethod] = Schema.derived

  given Conversion[w3c.PublicKeyRepr, VerificationMethod] = (publicKey: w3c.PublicKeyRepr) =>
    VerificationMethod(
      id = publicKey.id,
      `type` = publicKey.`type`,
      controller = publicKey.controller,
      publicKeyJwk = publicKey.publicKeyJwk
    )
}
