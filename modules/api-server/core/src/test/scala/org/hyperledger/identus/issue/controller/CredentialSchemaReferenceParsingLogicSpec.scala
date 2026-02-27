package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.issue.controller.http.CredentialSchemaRef as HTTPCredentialSchemaRef
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.{
  CredentialSchemaRef as DomainCredentialSchemaRef,
  CredentialSchemaRefType
}
import zio.test.*
import zio.test.Assertion.*

object CredentialSchemaReferenceParsingLogicSpec extends ZIOSpecDefault with CredentialSchemaReferenceParsingLogic {

  private val credentialSchemaExample = "http://example.com/schema"

  private def isErrorResponseWithDetailFieldEqualTo(detail: String) =
    isSubtype[ErrorResponse](hasField("detail", _.detail, isSome(equalTo(detail))))

  def spec = suite("CredentialSchemaReferenceParsingLogic")(
    suite("parseCredentialSchemaRef_VCDM1_1")(
      test("should parse valid schema ref with correct type") {
        val httpSchemaRef = HTTPCredentialSchemaRef(credentialSchemaExample, "JsonSchemaValidator2018")
        for {
          result <- parseCredentialSchemaRef_VCDM1_1(None, Some(httpSchemaRef)).either
          expectedUriString <- UriString.make(credentialSchemaExample).toZIO
        } yield assert(result)(
          isRight(
            equalTo(DomainCredentialSchemaRef(CredentialSchemaRefType.JsonSchemaValidator2018, expectedUriString))
          )
        )
      },
      test("should fail for schema ref with invalid type") {
        val httpSchemaRef = HTTPCredentialSchemaRef(credentialSchemaExample, "InvalidType")
        for {
          result <- parseCredentialSchemaRef_VCDM1_1(None, Some(httpSchemaRef)).either
        } yield assert(result)(
          isLeft(isErrorResponseWithDetailFieldEqualTo("Invalid credentialSchema type: InvalidType."))
        )
      },
      test("should parse deprecated schema ID property") {
        for {
          result <- parseCredentialSchemaRef_VCDM1_1(Some(credentialSchemaExample), None).either
          expectedUriString <- UriString.make(credentialSchemaExample).toZIO
        } yield assert(result)(
          isRight(
            equalTo(DomainCredentialSchemaRef(CredentialSchemaRefType.JsonSchemaValidator2018, expectedUriString))
          )
        )
      },
      test("should fail if no schema is provided") {
        for {
          result <- parseCredentialSchemaRef_VCDM1_1(None, None).either
        } yield assert(result)(
          isLeft(isErrorResponseWithDetailFieldEqualTo("Credential schema property missed."))
        )
      }
    ),
    suite("parseSchemaIdForAnonCredsModelV1")(
      test("should parse schema ID property") {
        for {
          result <- parseSchemaIdForAnonCredsModelV1(None, Some(credentialSchemaExample)).either
          expectedUriString <- UriString.make(credentialSchemaExample).toZIO
        } yield assert(result)(isRight(equalTo(expectedUriString)))
      },
      test("should parse deprecated schema ID property") {
        for {
          result <- parseSchemaIdForAnonCredsModelV1(Some(credentialSchemaExample), None).either
          expectedUriString <- UriString.make(credentialSchemaExample).toZIO
        } yield assert(result)(isRight(equalTo(expectedUriString)))
      },
      test("should fail if no schema ID is provided") {
        for {
          result <- parseSchemaIdForAnonCredsModelV1(None, None).either
        } yield assert(result)(
          isLeft(isErrorResponseWithDetailFieldEqualTo("Credential schema property missed."))
        )
      }
    )
  )
}
