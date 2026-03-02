package org.hyperledger.identus.presentproof.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.presentproof.controller.http.ProofRequestAux.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class ProofRequestAux(
    @description(annotations.schemaId.description)
    @encodedExample(annotations.schemaId.example)
    schemaId: String,
    @description(annotations.trustIssuers.description)
    @encodedExample(annotations.trustIssuers.example)
    trustIssuers: Seq[String]
)

object ProofRequestAux {
  object annotations {
    object schemaId
        extends Annotation[String](
          description = "The unique identifier of a schema the VC should comply with.",
          example = "https://schema.org/Person"
        )
    object trustIssuers
        extends Annotation[Seq[String]](
          description = "One or more issuers that are trusted by the verifier emitting the proof presentation request.",
          example = Seq("did:web:atalaprism.io/users/testUser", "did.prism:123", "did:prism:...")
        )
  }

  given encoder: JsonEncoder[ProofRequestAux] =
    DeriveJsonEncoder.gen[ProofRequestAux]

  given decoder: JsonDecoder[ProofRequestAux] =
    DeriveJsonDecoder.gen[ProofRequestAux]

  given schema: Schema[ProofRequestAux] = Schema.derived
}
