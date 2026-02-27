package org.hyperledger.identus.pollux.core.model.error

import org.hyperledger.identus.shared.http.GenericUriResolverError
import org.hyperledger.identus.shared.json.JsonSchemaError
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait CredentialSchemaError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "CredentialSchema"
}

object CredentialSchemaError {
  final case class InvalidURI(uri: String)
      extends CredentialSchemaError(
        StatusCode.BadRequest,
        s"The URI to dereference is invalid: uri=[$uri]"
      )

  final case class CredentialSchemaParsingError(cause: String)
      extends CredentialSchemaError(
        StatusCode.BadRequest,
        s"Failed to parse the schema content as Json: cause[$cause]"
      )

  final case class CredentialSchemaValidationError(schemaError: JsonSchemaError)
      extends CredentialSchemaError(
        StatusCode.UnprocessableContent,
        s"The credential schema validation failed: schemaError[${schemaError.error}]"
      )

  final case class VCClaimsParsingError(cause: String)
      extends CredentialSchemaError(
        StatusCode.BadRequest,
        s"Failed to parse the VC claims as Json: cause[$cause]"
      )

  final case class VCClaimValidationError(name: String, cause: String)
      extends CredentialSchemaError(
        StatusCode.UnprocessableContent,
        s"The VC claim validation failed: claim=$name, cause=[$cause]"
      )

  final case class UnsupportedCredentialSchemaType(`type`: String)
      extends CredentialSchemaError(
        StatusCode.BadRequest,
        s"Unsupported credential schema type: ${`type`}"
      )

  final case class SchemaDereferencingError(cause: GenericUriResolverError)
      extends CredentialSchemaError(
        StatusCode.InternalServerError,
        s"The schema was not successfully dereferenced: cause=[${cause.userFacingMessage}]"
      )
}
