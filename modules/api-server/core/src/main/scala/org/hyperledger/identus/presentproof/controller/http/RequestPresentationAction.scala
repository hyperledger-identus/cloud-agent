package org.hyperledger.identus.presentproof.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.pollux.core.service.serdes.*
import org.hyperledger.identus.presentproof.controller.http.RequestPresentationAction.annotations
import sttp.tapir.{Schema, Validator}
import sttp.tapir.json.zio.*
import sttp.tapir.Schema.annotations.{description, encodedExample, validate}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class RequestPresentationAction(
    @description(annotations.action.description)
    @encodedExample(annotations.action.example)
    @validate(annotations.action.validator)
    action: String,
    @description(annotations.proofId.description)
    @encodedExample(annotations.proofId.example)
    proofId: Option[Seq[String]] = None,
    @description(annotations.anoncredProof.description)
    @encodedExample(annotations.anoncredProof.example)
    anoncredPresentationRequest: Option[AnoncredCredentialProofsV1],
    @description(annotations.claims.description)
    @encodedExample(annotations.claims.example)
    claims: Option[zio.json.ast.Json.Obj],
    @description(annotations.credentialFormat.description)
    @encodedExample(annotations.credentialFormat.example)
    credentialFormat: Option[String],
)

object RequestPresentationAction {
  object annotations {
    object action
        extends Annotation[String](
          description = "The action to perform on the proof presentation record.",
          example = "request-accept",
          validator = Validator.enumeration(
            List(
              "request-accept",
              "request-reject",
              "presentation-accept",
              "presentation-reject"
            )
          )
        )
    object proofId
        extends Annotation[Option[Seq[String]]](
          description =
            "The unique identifier of the issue credential record - and hence VC - to use as the prover accepts the presentation request. Only applicable on the prover side when the action is `request-accept`.",
          example = None
        )

    object anoncredProof
        extends Annotation[Option[AnoncredCredentialProofsV1]](
          description = "A list of proofs from the Anoncred library, each corresponding to a credential.",
          example = None
        )

    object credential
        extends Annotation[String](
          description =
            "The unique identifier of the issue credential record - and hence VC - to use as the prover accepts the presentation request. Only applicable on the prover side when the action is `request-accept`.",
          example = "id"
        )

    object claims
        extends Annotation[Option[zio.json.ast.Json]](
          description = """
            |The set of claims to be disclosed from the  issued credential.
            |The JSON object should comply with the schema applicable for this offer (i.e. 'schemaId' or 'credentialDefinitionId').
            |""".stripMargin,
          example = Some(
            zio.json.ast.Json.Obj(
              "firstname" -> zio.json.ast.Json.Str("Alice"),
              "lastname" -> zio.json.ast.Json.Str("Wonderland"),
            )
          )
        )

    object credentialFormat
        extends Annotation[Option[String]](
          description = "The credential format (default to 'JWT')",
          example = Some("JWT"),
          validator = Validator.enumeration(
            List(
              Some("JWT"),
              Some("SDJWT"),
              Some("AnonCreds")
            )
          )
        )
  }

  given RequestPresentationActionEncoder: JsonEncoder[RequestPresentationAction] =
    DeriveJsonEncoder.gen[RequestPresentationAction]

  given RequestPresentationActionDecoder: JsonDecoder[RequestPresentationAction] =
    DeriveJsonDecoder.gen[RequestPresentationAction]

  import AnoncredCredentialProofsV1.given

  given Schema[AnoncredCredentialProofsV1] = Schema.derived

  given Schema[AnoncredCredentialProofV1] = Schema.derived

  given RequestPresentationActionSchema: Schema[RequestPresentationAction] = Schema.derived

}
