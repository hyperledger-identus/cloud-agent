package org.hyperledger.identus.credentialstatus.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.credentialstatus.controller.http.StatusListCredential.annotations
import org.hyperledger.identus.pollux.core.model.CredentialStatusList
import org.hyperledger.identus.pollux.vc.jwt.{CredentialIssuer, StatusPurpose}
import sttp.tapir.json.zio.schemaForZioJsonValue
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.time.Instant

case class StatusListCredential(
    @description(annotations.`@context`.description)
    @encodedExample(annotations.`@context`.example)
    `@context`: Set[String],
    @description(annotations.`type`.description)
    @encodedExample(annotations.`type`.example)
    `type`: Set[String],
    @description(annotations.issuer.description)
    @encodedExample(annotations.issuer.example)
    issuer: String | CredentialIssuer,
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: String,
    @description(annotations.issuanceDate.description)
    @encodedExample(annotations.issuanceDate.example)
    issuanceDate: Instant,
    @description("Object containing claims specific to status list credential")
    credentialSubject: CredentialSubject,
    @description(annotations.proof.description)
    @encodedExample(annotations.proof.example)
    proof: Json
)

case class CredentialSubject(
    @description(annotations.credentialSubject.`type`.description)
    @encodedExample(annotations.credentialSubject.`type`.example)
    `type`: String,
    @description(annotations.credentialSubject.statusPurpose.description)
    @encodedExample(annotations.credentialSubject.statusPurpose.example)
    statusPurpose: StatusPurpose,
    @description(annotations.credentialSubject.encodedList.description)
    @encodedExample(annotations.credentialSubject.encodedList.example)
    encodedList: String
)

object StatusListCredential {

  def fromCredentialStatusListEntry(
      domain: CredentialStatusList
  ): UIO[StatusListCredential] = ZIO
    .fromEither(domain.statusListCredential.fromJson[StatusListCredential])
    .orDieWith(err => RuntimeException(s"An error occurred when parsing the status list credential: $err"))

  object annotations {
    object `@context`
        extends Annotation[Set[String]](
          description = "List of JSON-LD contexts",
          example = Set("https://www.w3.org/2018/credentials/v1", "https://w3id.org/vc/status-list/2021/v1")
        )

    object `type`
        extends Annotation[Set[String]](
          description = "List of credential types",
          example = Set("VerifiableCredential", "StatusList2021Credential")
        )

    object issuer
        extends Annotation[String](
          description = "DID of the issuer of status list credential",
          example = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a"
        )

    object id
        extends Annotation[String](
          description = "Unique identifier of status list credential",
          example = "http://issuer-agent.com/credential-status/060a2bec-6d6f-4c1f-9414-d3c9dbd3ccc9"
        )

    object issuanceDate
        extends Annotation[Instant](
          description = "Issuance timestamp of status list credential",
          example = Instant.parse("2025-01-01T22:40:34.560891Z")
        )

    object credentialSubject {
      object id
          extends Annotation[String](
            description = "Url to resolve this particular status list credential",
            example = "http://issuer-agent.com/credential-status/060a2bec-6d6f-4c1f-9414-d3c9dbd3ccc9"
          )

      object `type`
          extends Annotation[String](
            description = "Always equals to constant value - StatusList2021",
            example = "StatusList2021"
          )

      object statusPurpose
          extends Annotation[StatusPurpose](
            description = "type of status list credential, either revocation or suspension",
            example = StatusPurpose.Revocation
          )

      object encodedList
          extends Annotation[String](
            description = "base64 url encoded bitstring of credential statuses",
            example = "H4sIAAAAAAAA_-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"
          )

    }

    object proof
        extends Annotation[Json](
          description =
            """Embedded proof to verify data integrity of status list credential, includes "type" property which defines an algorithm to be used for proof verification""",
          example = proofJsonExample.fromJson[Json].toOption.getOrElse(Json.Null)
        )

  }

  val proofJsonExample: String =
    """
      |{
      |  "type": "DataIntegrityProof",
      |  "proofPurpose": "assertionMethod",
      |  "verificationMethod": "data:application/json;base64,eyJAY29udGV4dCI6WyJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L211bHRpa2V5L3YxIl0sInR5cGUiOiJNdWx0aWtleSIsInB1YmxpY0tleU11bHRpYmFzZSI6InVNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVRUENjM1M0X0xHVXRIM25DRjZ2dUw3ekdEMS13UmVrMHRHbnB0UnZUakhIMUdvTnk1UFBIZ0FmNTZlSzNOd3B0LWNGcmhrT2pRQk1rcFRKOHNaS1pCZz09In0=",
      |  "created": "2024-01-22T22:40:34.560891Z",
      |  "proofValue": "zAN1rKq8npnByRqPRxhjHEkivhN8AhA8V6MqDJga1zcCUEvPDUoqJB5Rj6ZJHTCnBZ98VXTEVd1rprX2wvP1MAaTEi7Pm241qm",
      |  "cryptoSuite": "eddsa-jcs-2022"
      |}
      |""".stripMargin

  given StatusPurposeCodec: JsonCodec[StatusPurpose] = JsonCodec[StatusPurpose](
    JsonEncoder[String].contramap[StatusPurpose](_.toString),
    JsonDecoder[String].mapOrFail { input =>
      StatusPurpose.values.find(_.toString.compareToIgnoreCase(input) == 0).toRight("Unknown StatusPurpose")
    },
  )

  given instantDecoder: JsonDecoder[Instant] =
    JsonDecoder[Long].map(Instant.ofEpochSecond)

  given instantEncoder: JsonEncoder[Instant] =
    JsonEncoder[Long].contramap(_.getEpochSecond)

  given credentialIssuerEncoder: JsonEncoder[CredentialIssuer] =
    DeriveJsonEncoder.gen[CredentialIssuer]

  given credentialIssuerDecoder: JsonDecoder[CredentialIssuer] =
    DeriveJsonDecoder.gen[CredentialIssuer]

  given stringOrCredentialIssuerEncoder: JsonEncoder[String | CredentialIssuer] =
    JsonEncoder[String]
      .orElseEither(JsonEncoder[CredentialIssuer])
      .contramap[String | CredentialIssuer] {
        case string: String                     => Left(string)
        case credentialIssuer: CredentialIssuer => Right(credentialIssuer)
      }

  given stringOrCredentialIssuerDecoder: JsonDecoder[String | CredentialIssuer] =
    JsonDecoder[CredentialIssuer]
      .map(issuer => issuer: String | CredentialIssuer)
      .orElse(JsonDecoder[String].map(issuerId => issuerId: String | CredentialIssuer))

  given statusListCredentialEncoder: JsonEncoder[StatusListCredential] =
    DeriveJsonEncoder.gen[StatusListCredential]

  given statusListCredentialDecoder: JsonDecoder[StatusListCredential] =
    DeriveJsonDecoder.gen[StatusListCredential]

  given credentialSubjectEncoder: JsonEncoder[CredentialSubject] =
    DeriveJsonEncoder.gen[CredentialSubject]

  given credentialSubjectDecoder: JsonDecoder[CredentialSubject] =
    DeriveJsonDecoder.gen[CredentialSubject]

  given credentialSubjectSchema: Schema[CredentialSubject] = Schema.derived

  given statusPurposeSchema: Schema[StatusPurpose] = Schema.derivedEnumeration.defaultStringBased

  given credentialIssuerSchema: Schema[CredentialIssuer] = Schema.derived

  given schemaIssuer: Schema[String | CredentialIssuer] = Schema
    .schemaForEither(Schema.schemaForString, Schema.derived[CredentialIssuer])
    .map[String | CredentialIssuer] {
      case Left(string)            => Some(string)
      case Right(credentialIssuer) => Some(credentialIssuer)
    } {
      case string: String                     => Left(string)
      case credentialIssuer: CredentialIssuer => Right(credentialIssuer)
    }

  given statusListCredentialSchema: Schema[StatusListCredential] = Schema.derived

}
