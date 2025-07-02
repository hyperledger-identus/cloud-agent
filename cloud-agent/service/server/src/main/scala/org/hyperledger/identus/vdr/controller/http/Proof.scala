package org.hyperledger.identus.vdr.controller.http

import interfaces.Proof as VdrProof
import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.shared.models.HexString
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class Proof(
    @description(Proof.annotations.`type`.description)
    @encodedExample(Proof.annotations.`type`.example)
    `type`: String,
    @description(Proof.annotations.proof.description)
    @encodedExample(Proof.annotations.proof.example)
    proof: String
)

object Proof {
  given Conversion[VdrProof, Proof] = (vdrProof: VdrProof) =>
    Proof(
      `type` = vdrProof.getType(),
      proof = HexString.fromByteArray(vdrProof.getProof()).toString()
    )

  given encoder: JsonEncoder[Proof] = DeriveJsonEncoder.gen[Proof]
  given decoder: JsonDecoder[Proof] = DeriveJsonDecoder.gen[Proof]
  given schema: Schema[Proof] = Schema.derived

  object annotations {
    object `type`
        extends Annotation[String](
          description = "A type of proof",
          example = "SHA256"
        )

    object proof
        extends Annotation[String](
          description = "A proof in hexadecimal string",
          example = "98e6a4db10e58fcc011dd8def5ce99fd8b52af39e61e5fb436dc28259139818b"
        )
  }
}
