package org.hyperledger.identus.verification.controller.http

import org.hyperledger.identus.api.http.{Annotation, ErrorResponse}
import org.hyperledger.identus.pollux.core.service.verification.VcVerificationRequest as ServiceVcVerificationRequest
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.*
import zio.json.*
import zio.json.ast.Json.*

import java.time.OffsetDateTime

final case class VcVerificationRequest(
    @description(VcVerificationRequest.annotations.credential.description)
    @encodedExample(VcVerificationRequest.annotations.credential.example)
    credential: String,
    @description(VcVerificationRequest.annotations.parameterizableVcVerifications.description)
    @encodedExample(VcVerificationRequest.annotations.parameterizableVcVerifications.example)
    verifications: List[ParameterizableVcVerification]
)

object VcVerificationRequest {
  object annotations {

    object credential
        extends Annotation[String](
          description = "Encoded Verifiable Credential to verify",
          example =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        )

    object parameterizableVcVerifications
        extends Annotation[String](
          description =
            "The list of verifications to perform on the credential. If the list is empty, all available verifications will be performed.",
          example = List(
            ParameterizableVcVerification(VcVerification.SignatureVerification, None),
            ParameterizableVcVerification(VcVerification.IssuerIdentification, Some(DidParameter("did:prism:issuer"))),
            ParameterizableVcVerification(
              VcVerification.ExpirationCheck,
              Some(DateTimeParameter(OffsetDateTime.parse("2022-03-10T12:00:00Z")))
            ),
            ParameterizableVcVerification(
              VcVerification.NotBeforeCheck,
              Some(DateTimeParameter(OffsetDateTime.parse("2022-03-10T12:00:00Z")))
            ),
            ParameterizableVcVerification(VcVerification.AudienceCheck, Some(DidParameter("did:prism:holder"))),
            ParameterizableVcVerification(VcVerification.SubjectVerification, None),
            ParameterizableVcVerification(VcVerification.IntegrityOfClaims, None),
            ParameterizableVcVerification(VcVerification.ComplianceWithStandards, None),
            ParameterizableVcVerification(VcVerification.RevocationCheck, None),
            ParameterizableVcVerification(VcVerification.AlgorithmVerification, None),
            ParameterizableVcVerification(VcVerification.SchemaCheck, None),
            ParameterizableVcVerification(VcVerification.SemanticCheckOfClaims, None)
          ).toJson
        )
  }

  given credentialVerificationRequestEncoder: JsonEncoder[VcVerificationRequest] =
    DeriveJsonEncoder.gen[VcVerificationRequest]

  given credentialVerificationRequestDecoder: JsonDecoder[VcVerificationRequest] =
    DeriveJsonDecoder.gen[VcVerificationRequest]

  given credentialVerificationRequestSchema: Schema[VcVerificationRequest] = Schema.derived

  def toService(request: VcVerificationRequest): IO[ErrorResponse, List[ServiceVcVerificationRequest]] = {
    ZIO.collectAll(
      request.verifications.map(verification =>
        for {
          serviceVerification <- VcVerification.convert(
            verification.verification,
            verification.parameter
          )

        } yield ServiceVcVerificationRequest(credential = request.credential, verification = serviceVerification)
      )
    )
  }
}
