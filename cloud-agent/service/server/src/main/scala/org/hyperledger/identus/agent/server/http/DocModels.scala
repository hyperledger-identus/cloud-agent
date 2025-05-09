package org.hyperledger.identus.agent.server.http

import org.hyperledger.identus.castor.controller.{DIDEndpoints, DIDRegistrarEndpoints}
import org.hyperledger.identus.connect.controller.ConnectionEndpoints
import org.hyperledger.identus.event.controller.EventEndpoints
import org.hyperledger.identus.iam.entity.http.EntityEndpoints
import org.hyperledger.identus.iam.wallet.http.WalletManagementEndpoints
import org.hyperledger.identus.issue.controller.IssueEndpoints
import org.hyperledger.identus.pollux.credentialdefinition.CredentialDefinitionRegistryEndpoints
import org.hyperledger.identus.pollux.credentialschema.{SchemaRegistryEndpoints, VerificationPolicyEndpoints}
import org.hyperledger.identus.pollux.prex.PresentationExchangeEndpoints
import org.hyperledger.identus.system.controller.SystemEndpoints
import sttp.apispec.{SecurityScheme, Tag}
import sttp.apispec.openapi.*
import sttp.model.headers.AuthenticationScheme

import scala.collection.immutable.ListMap

object DocModels {

  private val apiKeySecuritySchema = SecurityScheme(
    `type` = "apiKey",
    description = Some("API Key Authentication. The header `apikey` must be set with the API key."),
    name = Some("apikey"),
    in = Some("header"),
    scheme = None,
    bearerFormat = None,
    flows = None,
    openIdConnectUrl = None
  )

  private val adminApiKeySecuritySchema = SecurityScheme(
    `type` = "apiKey",
    description =
      Some("Admin API Key Authentication. The header `x-admin-api-key` must be set with the Admin API key."),
    name = Some("x-admin-api-key"),
    in = Some("header"),
    scheme = None,
    bearerFormat = None,
    flows = None,
    openIdConnectUrl = None
  )

  private val jwtSecurityScheme = SecurityScheme(
    `type` = "http",
    description =
      Some("JWT Authentication. The header `Authorization` must be set with the JWT token using `Bearer` scheme"),
    name = Some("Authorization"),
    in = Some("header"),
    scheme = Some(AuthenticationScheme.Bearer.name),
    bearerFormat = None,
    flows = None,
    openIdConnectUrl = None
  )

  val customiseDocsModel: OpenAPI => OpenAPI = { oapi =>
    oapi
      .info(
        Info(
          title = "Identus Cloud Agent API Reference",
          version = "1.0", // Will be replaced dynamically by 'Tapir2StaticOAS'
          summary = None,
          description = Some("""
              |The Identus Cloud Agent API facilitates the integration and management of self-sovereign identity capabilities within applications.
              |It supports DID (Decentralized Identifiers) management, verifiable credential exchange, and secure messaging based on DIDComm standards.
              |The API is designed to be interoperable with various blockchain and DLT (Distributed Ledger Technology) platforms, ensuring wide compatibility and flexibility.
              |Key features include connection management, credential issuance and verification, and secure, privacy-preserving communication between entities.
              |Additional information and the full list of capabilities can be found in the [Open Enterprise Agent documentation](https://github.com/hyperledger-identus/cloud-agent)
              |""".stripMargin),
          termsOfService = None,
          contact = None,
          license = Some(
            License(
              name = "Apache 2.0",
              url = Some("https://www.apache.org/licenses/LICENSE-2.0"),
              extensions = ListMap.empty
            )
          ),
          extensions = ListMap.empty
        )
      )
      .servers(
        List(
          Server(url = "http://localhost:8085", description = Some("The local instance of the Cloud Agent")),
          Server(
            url = "http://localhost/cloud-agent",
            description = Some("The local instance of the Cloud Agent behind the APISIX proxy")
          ),
          Server(
            url = "https://k8s-dev.atalaprism.io/cloud-agent",
            description = Some("The Cloud Agent in the Staging Environment")
          ),
        )
      )
      .components(
        oapi.components
          .getOrElse(sttp.apispec.openapi.Components.Empty)
          .copy(securitySchemes =
            ListMap(
              "apiKeyAuth" -> Right(apiKeySecuritySchema),
              "adminApiKeyAuth" -> Right(adminApiKeySecuritySchema),
              "jwtAuth" -> Right(jwtSecurityScheme)
            )
          )
      )
      .addSecurity(
        ListMap(
          "apiKeyAuth" -> Vector.empty[String],
          "adminApiKeyAuth" -> Vector.empty[String],
          "jwtAuth" -> Vector.empty[String]
        )
      )
      .tags(
        List(
          ConnectionEndpoints.tag,
          IssueEndpoints.tag,
          VerificationPolicyEndpoints.tag,
          SchemaRegistryEndpoints.tag,
          CredentialDefinitionRegistryEndpoints.tag,
          DIDEndpoints.tag,
          DIDRegistrarEndpoints.tag,
          WalletManagementEndpoints.tag,
          SystemEndpoints.tag,
          EventEndpoints.tag,
          EntityEndpoints.tag,
          PresentationExchangeEndpoints.tag
        )
      )

  }

}
