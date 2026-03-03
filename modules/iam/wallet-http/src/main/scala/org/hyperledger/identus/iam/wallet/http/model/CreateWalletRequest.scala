package org.hyperledger.identus.iam.wallet.http.model

import org.hyperledger.identus.api.http.Annotation
import sttp.tapir.*
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.util.UUID

final case class CreateWalletRequest(
    @description(CreateWalletRequest.annotations.seed.description)
    @encodedExample(CreateWalletRequest.annotations.seed.example)
    seed: Option[String],
    @description(CreateWalletRequest.annotations.name.description)
    @encodedExample(CreateWalletRequest.annotations.name.example)
    @validate(CreateWalletRequest.annotations.name.validator)
    name: String,
    @description(CreateWalletRequest.annotations.id.description)
    @encodedExample(CreateWalletRequest.annotations.id.example)
    id: Option[UUID]
)

object CreateWalletRequest {
  given encoder: JsonEncoder[CreateWalletRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CreateWalletRequest] = DeriveJsonDecoder.gen
  given schema: Schema[CreateWalletRequest] = Schema.derived

  object annotations {
    object seed
        extends Annotation[String](
          description =
            "A BIP32 seed encoded in hexadecimal string. It is expected to represent 64-bytes binary seed (128 hex characters).",
          example =
            "c9994785ce6d548134020f610b76102ca1075d3bb672a75ec8c9a27a7b8607e3b9b384e43b77bb08f8d5159651ae38b98573f7ecc79f2d7e1f1cc371ce60cf8a"
        )

    object name
        extends Annotation[String](
          description = "A name of the wallet",
          example = "my-wallet-1",
          validator = Validator.all(Validator.nonEmptyString, Validator.maxLength(128))
        )

    object id
        extends Annotation[UUID](
          description = "The unique `id` of the wallet. Randomly generated if not specified.",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )
  }
}
