package org.hyperledger.identus.issue.controller

import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.issue.controller.http.CredentialSchemaRef as HTTPCredentialSchemaRef
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.schema.{
  CredentialSchemaRef as DomainCredentialSchemaRef,
  CredentialSchemaRefType
}
import zio.{IO, ZIO}

trait CredentialSchemaReferenceParsingLogic {

  // According to VCDM 1.1, the property "credentialSchema" is required to issue JWT, JSON, and JSON-LD credentials.
  // The "id" property in the "credentialSchema" object is a URI that points to the schema of the credential.
  // The "type" property in the "credentialSchema" object must be "JsonSchemaValidator2018".
  // Multiple schemas are not allowed in VCDM 1.1.
  def parseCredentialSchemaRef_VCDM1_1(
      deprecatedSchemaIdProperty: Option[String],
      credentialSchemaRefOption: Option[HTTPCredentialSchemaRef]
  ): IO[ErrorResponse, DomainCredentialSchemaRef] = {
    credentialSchemaRefOption match {
      case Some(csr) if csr.`type` == "JsonSchemaValidator2018" =>
        makeDomainCredentialSchemaRef(csr.id)
      case Some(csr) =>
        ZIO.fail(ErrorResponse.badRequest(detail = Some(s"Invalid credentialSchema type: ${csr.`type`}.")))
      case None =>
        handleDeprecatedSchemaId(deprecatedSchemaIdProperty)
          .flatMap(makeDomainCredentialSchemaRef)
    }
  }

  def parseSchemaIdForAnonCredsModelV1(
      deprecatedSchemaIdProperty: Option[String],
      schemaIdProperty: Option[String]
  ): IO[ErrorResponse, UriString] = {
    schemaIdProperty
      .map(makeUriStringOrErrorResponse)
      .getOrElse(handleDeprecatedSchemaId(deprecatedSchemaIdProperty).flatMap(makeUriStringOrErrorResponse))
  }

  private def handleDeprecatedSchemaId(
      deprecatedSchemaIdProperty: Option[String]
  ): IO[ErrorResponse, String] = {
    deprecatedSchemaIdProperty match {
      case Some(schemaId: String) =>
        ZIO.succeed(schemaId)
      case None =>
        ZIO.fail(ErrorResponse.badRequest(detail = Some("Credential schema property missed.")))
    }
  }

  private def makeDomainCredentialSchemaRef(input: String): IO[ErrorResponse, DomainCredentialSchemaRef] =
    makeUriStringOrErrorResponse(input).map(
      DomainCredentialSchemaRef(CredentialSchemaRefType.JsonSchemaValidator2018, _)
    )

  private def makeUriStringOrErrorResponse(input: String): IO[ErrorResponse, UriString] =
    UriString.make(input).toZIO.mapError(uriParseError => ErrorResponse.badRequest(detail = Some(uriParseError)))
}
