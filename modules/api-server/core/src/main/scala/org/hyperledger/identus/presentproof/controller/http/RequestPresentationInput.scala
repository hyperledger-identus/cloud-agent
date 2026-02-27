package org.hyperledger.identus.presentproof.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.mercury.protocol.presentproof.PresentCredentialRequestFormat
import org.hyperledger.identus.pollux.core.service.serdes.*
import org.hyperledger.identus.presentproof.controller.http.RequestPresentationInput.annotations
import sttp.tapir.{Schema, Validator}
import sttp.tapir.json.zio.*
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.util.UUID

final case class RequestPresentationInput(
    @description(annotations.goalcode.description)
    @encodedExample(annotations.goalcode.example)
    goalCode: Option[String] = None,
    @description(annotations.goal.description)
    @encodedExample(annotations.goal.example)
    goal: Option[String] = None,
    @description(annotations.connectionId.description)
    @encodedExample(annotations.connectionId.example)
    connectionId: Option[UUID] = None,
    @description(annotations.options.description)
    @encodedExample(annotations.options.example)
    options: Option[Options] = None,
    @description(annotations.proofs.description)
    @encodedExample(annotations.proofs.example)
    proofs: Seq[ProofRequestAux],
    @description(annotations.anoncredPresentationRequest.description)
    @encodedExample(annotations.anoncredPresentationRequest.example)
    anoncredPresentationRequest: Option[AnoncredPresentationRequestV1],
    @description(annotations.presentationFormat.description)
    @encodedExample(annotations.presentationFormat.example)
    presentationFormat: Option[PresentCredentialRequestFormat],
    @description(annotations.claims.description)
    @encodedExample(annotations.claims.example)
    claims: Option[zio.json.ast.Json.Obj],
    @description(annotations.credentialFormat.description)
    @encodedExample(annotations.credentialFormat.example)
    credentialFormat: Option[String],
)

object RequestPresentationInput {
  object annotations {
    object connectionId
        extends Annotation[Option[UUID]](
          description = """
            |The unique identifier of a DIDComm connection that already exists between the this verifier agent and the prover cloud or edeg agent.
            |It should be the identifier of a connection that exists in the verifier agent's database.
            |This connection will be used to execute the present proof protocol.
            |Note: connectionId is only required when the presentation request is from existing connection.
            |connectionId is not required when the presentation request is from invitation for connectionless issuance.
            |""".stripMargin,
          example = Some(UUID.fromString("d9569cec-c81e-4779-aa86-0d5994d82676"))
        )

    object goalcode
        extends Annotation[Option[String]](
          description = """
            | A self-attested code the receiver may want to display to the user or use in automatically deciding what to do with the out-of-band message.
            | goalcode is optional and can be included when the presentation request is from invitation for connectionless verification.
            |""".stripMargin,
          example = Some("present-vp")
        )

    object goal
        extends Annotation[Option[String]](
          description = """
          | A self-attested string that the receiver may want to display to the user about the context-specific goal of the out-of-band message.
          | goal is optional and can be included when the presentation request is from invitation for connectionless verification.
          |""".stripMargin,
          example = Some("Request proof of vaccine")
        )

    object options
        extends Annotation[Option[Options]](
          description = "The options to use when creating the proof presentation request (e.g., domain, challenge).",
          example = None
        )
    object proofs
        extends Annotation[Seq[ProofRequestAux]](
          description =
            "The type of proofs requested in the context of this proof presentation request (e.g., VC schema, trusted issuers, etc.)",
          example = Seq.empty
        )

    object anoncredPresentationRequest
        extends Annotation[Option[AnoncredPresentationRequestV1]](
          description = "Anoncred Presentation Request",
          example = Some(
            AnoncredPresentationRequestV1(
              requested_attributes = Map(
                "attribute1" -> AnoncredRequestedAttributeV1(
                  "Attribute 1",
                  List(
                    Map(
                      "cred_def_id" -> "credential_definition_id_of_attribute1"
                    )
                  ),
                  Some(
                    AnoncredNonRevokedIntervalV1(
                      Some(1635734400),
                      Some(1735734400)
                    )
                  )
                )
              ),
              requested_predicates = Map(
                "predicate1" ->
                  AnoncredRequestedPredicateV1(
                    "Predicate 1",
                    ">=",
                    18,
                    List(
                      Map(
                        "schema_id" -> "schema_id_of_predicate1"
                      )
                    ),
                    Some(
                      AnoncredNonRevokedIntervalV1(
                        Some(1635734400),
                        None
                      )
                    )
                  )
              ),
              name = "Example Presentation Request",
              nonce = "1234567890",
              version = "1.0",
              non_revoked = None
            )
          )
        )

    object presentationFormat
        extends Annotation[Option[String]](
          description =
            "The presentation format to display in Didcomm messages (default to 'prism/jwt', vc+sd-jwt or anoncreds/proof-request@v1.0)",
          example = Some("prism/jwt"),
          validator = Validator.enumeration(
            List(
              Some("prism/jwt"),
              Some("vc+sd-jwt"),
              Some("anoncreds/proof-request@v1.0")
            )
          )
        )
    object claims
        extends Annotation[Option[zio.json.ast.Json.Obj]](
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

  given encoder: JsonEncoder[RequestPresentationInput] =
    DeriveJsonEncoder.gen[RequestPresentationInput]

  given decoder: JsonDecoder[RequestPresentationInput] =
    DeriveJsonDecoder.gen[RequestPresentationInput]

  import AnoncredPresentationRequestV1.given

  given Schema[PresentCredentialRequestFormat] = Schema.derivedEnumeration.defaultStringBased

  given Schema[AnoncredPresentationRequestV1] = Schema.derived

  given Schema[AnoncredRequestedAttributeV1] = Schema.derived

  given Schema[AnoncredRequestedPredicateV1] = Schema.derived

  given Schema[AnoncredNonRevokedIntervalV1] = Schema.derived

  given schema: Schema[RequestPresentationInput] = Schema.derived
}
