package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.core.model.did.ScheduleDIDOperationOutcome
import org.hyperledger.identus.shared.models.HexString
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class DIDOperationResponse(
    scheduledOperation: DidOperationSubmission
)

object DIDOperationResponse {
  given encoder: JsonEncoder[DIDOperationResponse] = DeriveJsonEncoder.gen[DIDOperationResponse]
  given decoder: JsonDecoder[DIDOperationResponse] = DeriveJsonDecoder.gen[DIDOperationResponse]
  given schema: Schema[DIDOperationResponse] = Schema.derived

  given Conversion[ScheduleDIDOperationOutcome, DIDOperationResponse] = { outcome =>
    DIDOperationResponse(scheduledOperation =
      DidOperationSubmission(
        id = HexString.fromByteArray(outcome.operationId.toArray).toString,
        didRef = outcome.did.toString
      )
    )
  }
}

final case class DidOperationSubmission(
    @description(DidOperationSubmission.annotations.id.description)
    @encodedExample(DidOperationSubmission.annotations.id.example)
    id: String,
    @description(DidOperationSubmission.annotations.didRef.description)
    @encodedExample(DidOperationSubmission.annotations.didRef.example)
    didRef: String
)

object DidOperationSubmission {
  object annotations {
    object id
        extends Annotation[String](
          description = "A scheduled operation ID",
          example = "98e6a4db10e58fcc011dd8def5ce99fd8b52af39e61e5fb436dc28259139818b"
        )

    object didRef
        extends Annotation[String](
          description = "A DID affected by the scheduled operation",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )
  }

  given encoder: JsonEncoder[DidOperationSubmission] = DeriveJsonEncoder.gen[DidOperationSubmission]
  given decoder: JsonDecoder[DidOperationSubmission] = DeriveJsonDecoder.gen[DidOperationSubmission]
  given schema: Schema[DidOperationSubmission] = Schema.derived
}
