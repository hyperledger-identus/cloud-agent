package org.hyperledger.identus.pollux.credentialschema.http

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.pollux.credentialschema.http.Proof.annotations
import sttp.tapir.generic.auto.*
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.OffsetDateTime

case class Proof(
    @description(annotations.`type`.description)
    @encodedExample(annotations.`type`.example)
    `type`: String,
    @description(annotations.created.description)
    @encodedExample(annotations.created.example)
    created: OffsetDateTime,
    @description(annotations.verificationMethod.description)
    @encodedExample(annotations.verificationMethod.example)
    verificationMethod: String,
    @description(annotations.proofPurpose.description)
    @encodedExample(annotations.proofPurpose.example)
    proofPurpose: String,
    @description(annotations.proofValue.description)
    @encodedExample(annotations.proofValue.example)
    proofValue: String,
    @description(annotations.jws.description)
    @encodedExample(annotations.jws.example)
    jws: String,
    @description(annotations.domain.description)
    @encodedExample(annotations.domain.example)
    domain: Option[String]
)

object Proof {
  given encoder: JsonEncoder[Proof] = DeriveJsonEncoder.gen[Proof]
  given decoder: JsonDecoder[Proof] = DeriveJsonDecoder.gen[Proof]
  given schema: Schema[Proof] = Schema.derived

  object annotations {
    object `type`
        extends Annotation[String](
          description = "The type of cryptographic signature algorithm used to generate the proof.",
          example = "Ed25519Signature2018"
        )

    object created
        extends Annotation[OffsetDateTime](
          description = "The date and time at which the proof was created, in UTC format. " +
            "This field is used to ensure that the proof was generated before or at the same time as the credential schema itself.",
          example = OffsetDateTime.parse("2022-03-10T12:00:00Z")
        )

    object proofPurpose
        extends Annotation[String](
          description = "The purpose of the proof (for example: `assertionMethod`). " +
            "This indicates that the proof is being used to assert that the issuer really issued this credential schema instance.",
          example = "assertionMethod"
        )

    object verificationMethod
        extends Annotation[String](
          description = "The verification method used to generate the proof. " +
            "This is usually a DID and key ID combination that can be used to look up the public key needed to verify the proof.",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff#key-1"
        )

    object jws
        extends Annotation[String](
          description = "The JSON Web Signature (JWS) that contains the proof information.",
          example = "eyJhbGciOiJFZERTQSIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il0sImt0eSI6Ik..."
        )

    object proofValue
        extends Annotation[String](
          description =
            "The cryptographic signature value that was generated using the private key associated with the verification method, " +
              "and which can be used to verify the proof.",
          example = "FiPfjknHikKmZ..."
        )

    object domain
        extends Annotation[String](
          description = "It specifies the domain context within which the credential schema and proof are being used",
          example = "prims.atala.com"
        )
  }

  val Example = Proof(
    `type` = annotations.`type`.example,
    created = annotations.created.example,
    verificationMethod = annotations.verificationMethod.example,
    proofPurpose = annotations.proofPurpose.example,
    proofValue = annotations.proofValue.example,
    jws = annotations.jws.example,
    domain = Some(annotations.domain.example)
  )
}
