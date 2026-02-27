package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.pollux.core.model.oid4vci.CredentialConfiguration as PolluxCredentialConfiguration
import sttp.tapir.Schema
import zio.json.*

import java.time.{OffsetDateTime, ZoneOffset}
import scala.language.implicitConversions

final case class CreateCredentialConfigurationRequest(
    configurationId: String,
    format: CredentialFormat,
    schemaId: String,
)

object CreateCredentialConfigurationRequest {
  given schema: Schema[CreateCredentialConfigurationRequest] = Schema.derived
  given encoder: JsonEncoder[CreateCredentialConfigurationRequest] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CreateCredentialConfigurationRequest] = DeriveJsonDecoder.gen
}

final case class CredentialConfiguration(
    configurationId: String,
    format: CredentialFormat,
    scope: String,
    schemaId: String,
    createdAt: OffsetDateTime
)

object CredentialConfiguration {
  given schema: Schema[CredentialConfiguration] = Schema.derived
  given encoder: JsonEncoder[CredentialConfiguration] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[CredentialConfiguration] = DeriveJsonDecoder.gen

  given Conversion[PolluxCredentialConfiguration, CredentialConfiguration] = cc =>
    CredentialConfiguration(
      configurationId = cc.configurationId,
      format = cc.format,
      scope = cc.scope,
      schemaId = cc.schemaId.toString(),
      createdAt = cc.createdAt.atOffset(ZoneOffset.UTC),
    )
}
