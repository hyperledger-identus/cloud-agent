package org.hyperledger.identus.pollux.credentialdefinition.http

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition
import org.hyperledger.identus.pollux.core.model.ResourceResolutionMethod
import org.hyperledger.identus.pollux.credentialdefinition.http.FilterInput.annotations
import sttp.tapir.EndpointIO.annotations.{example, query}
import sttp.tapir.Validator.*

case class FilterInput(
    @query
    @example(Option(annotations.author.example))
    author: Option[String] = Option.empty[String],
    @query
    @example(Option(annotations.name.example))
    name: Option[String] = Option.empty[String],
    @query
    @example(Option(annotations.version.example))
    version: Option[String] = Option.empty[String],
    @query
    @example(Option(annotations.tag.example))
    tag: Option[String] = Option.empty[String]
) {
  def toDomain(resolutionMethod: ResourceResolutionMethod = ResourceResolutionMethod.http) =
    CredentialDefinition.Filter(author, name, version, tag, resolutionMethod)
}

object FilterInput {
  // TODO: for some reason @description attribute doesn't work together with @query in tapir
  // need to invest more time and probably report the issue
  object annotations {

    object author
        extends Annotation[String](
          description =
            "An optional field that can be used to filter the credential definition collection by `author`'s DID",
          example = CredentialDefinitionResponse.annotations.author.example
        )

    object name
        extends Annotation[String](
          description = "An optional field that can be used to filter the credential definition records by `name`",
          example = CredentialDefinitionResponse.annotations.name.example
        )

    object version
        extends Annotation[String](
          description = "An optional string field that can be used to filter resources by version",
          example = CredentialDefinitionResponse.annotations.version.example
        )

    object tag
        extends Annotation[String](
          description = "An optional string field that can be used to filter resources by tag",
          example = CredentialDefinitionResponse.annotations.tag.example
        )
  }
}
