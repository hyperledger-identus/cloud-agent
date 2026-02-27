package org.hyperledger.identus.pollux.credentialdefinition.http

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition.Input
import org.hyperledger.identus.pollux.credentialdefinition.http.CredentialDefinitionResponse.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample, validate, validateEach}
import sttp.tapir.Validator.*
import zio.json.*

case class CredentialDefinitionInput(
    @description(annotations.name.description)
    @encodedExample(annotations.name.example)
    @validate(nonEmptyString)
    name: String,
    @description(annotations.description.description)
    @encodedExample(annotations.description.example)
    @validateEach(nonEmptyString)
    description: Option[String],
    @description(annotations.version.description)
    @encodedExample(annotations.version.example)
    @validate(pattern(SemVerRegex))
    version: String,
    @description(annotations.tag.description)
    @encodedExample(annotations.tag.example)
    tag: String,
    @description(annotations.author.description)
    @encodedExample(annotations.author.example)
    @validate(pattern(DIDRefRegex))
    author: String,
    @description(annotations.schemaId.description)
    @encodedExample(annotations.schemaId.example)
    schemaId: String,
    @description(annotations.signatureType.description)
    @encodedExample(annotations.signatureType.example)
    signatureType: String,
    @description(annotations.supportRevocation.description)
    @encodedExample(annotations.supportRevocation.example)
    supportRevocation: Boolean
)

object CredentialDefinitionInput {
  def toDomain(in: CredentialDefinitionInput): Input =
    Input(
      name = in.name,
      description = in.description.getOrElse(""),
      version = in.version,
      authored = None,
      tag = in.tag,
      author = in.author,
      schemaId = in.schemaId,
      signatureType = in.signatureType,
      supportRevocation = in.supportRevocation
    )

  given encoder: JsonEncoder[CredentialDefinitionInput] =
    DeriveJsonEncoder.gen[CredentialDefinitionInput]

  given decoder: JsonDecoder[CredentialDefinitionInput] =
    DeriveJsonDecoder.gen[CredentialDefinitionInput]

  given schema: Schema[CredentialDefinitionInput] = Schema.derived
}
