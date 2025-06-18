package org.hyperledger.identus.oid4vci.http

import org.hyperledger.identus.pollux.core.model.oid4vci as pollux
import sttp.tapir.Schema
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.net.URL
import scala.language.implicitConversions

case class IssuerMetadata(
    credential_issuer: String,
    authorization_servers: Option[Seq[String]],
    credential_endpoint: String,
    credential_configurations_supported: Map[String, SupportedCredentialConfiguration]
)

object IssuerMetadata {
  given schema: Schema[IssuerMetadata] = Schema.derived
  given encoder: JsonEncoder[IssuerMetadata] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[IssuerMetadata] = DeriveJsonDecoder.gen

  def fromIssuer(
      credentialIssuerBaseUrl: URL,
      issuer: pollux.CredentialIssuer,
      credentialConfigurations: Seq[pollux.CredentialConfiguration]
  ): IssuerMetadata = {
    IssuerMetadata(
      credential_issuer = credentialIssuerBaseUrl.toString(),
      authorization_servers = Some(Seq(issuer.authorizationServer.toString())),
      credential_endpoint = s"$credentialIssuerBaseUrl/credentials",
      credential_configurations_supported =
        credentialConfigurations.map(cc => (cc.configurationId, cc: SupportedCredentialConfiguration)).toMap
    )
  }
}

final case class SupportedCredentialConfiguration(
    format: CredentialFormat,
    scope: String,
    credential_definition: CredentialDefinition,
    cryptographic_binding_methods_supported: Seq[String] = Seq("did:prism"),
    credential_signing_alg_values_supported: Seq[String] = Seq("ES256K"),
    proof_types_supported: SupportProofType =
      SupportProofType(jwt = ProofTypeConfiguration(proof_signing_alg_values_supported = Seq("ES256K")))
)

object SupportedCredentialConfiguration {
  given schema: Schema[SupportedCredentialConfiguration] = Schema.derived
  given encoder: JsonEncoder[SupportedCredentialConfiguration] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[SupportedCredentialConfiguration] = DeriveJsonDecoder.gen

  given Conversion[pollux.CredentialConfiguration, SupportedCredentialConfiguration] = cc =>
    SupportedCredentialConfiguration(
      format = cc.format,
      scope = cc.scope,
      credential_definition = CredentialDefinition(
        `@context` = Some(Seq("https://www.w3.org/2018/credentials/v1")),
        `type` = Seq("VerifiableCredential"),
        credentialSubject = None
      )
    )
}

final case class SupportProofType(jwt: ProofTypeConfiguration)

object SupportProofType {
  given schema: Schema[SupportProofType] = Schema.derived
  given encoder: JsonEncoder[SupportProofType] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[SupportProofType] = DeriveJsonDecoder.gen
}

final case class ProofTypeConfiguration(proof_signing_alg_values_supported: Seq[String])

object ProofTypeConfiguration {
  given schema: Schema[ProofTypeConfiguration] = Schema.derived
  given encoder: JsonEncoder[ProofTypeConfiguration] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[ProofTypeConfiguration] = DeriveJsonDecoder.gen
}
