package org.hyperledger.identus.iam.authentication.admin

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.iam.authentication.AuthenticationError
import zio.{IO, URLayer, ZIO, ZLayer}

case class AdminApiKeyAuthenticatorImpl(adminConfig: AdminConfig) extends AdminApiKeyAuthenticator {
  override def isEnabled: Boolean = true

  def authenticate(adminApiKey: String): IO[AuthenticationError, Entity] = {
    if (adminApiKey == adminConfig.token) {
      ZIO.logDebug(s"Admin API key authentication successful") *>
        ZIO.succeed(Entity.Admin)
    } else ZIO.fail(AdminApiKeyAuthenticationError.invalidAdminApiKey)
  }
}

object AdminApiKeyAuthenticatorImpl {
  val layer: URLayer[AdminConfig, AdminApiKeyAuthenticator] =
    ZLayer.fromZIO(ZIO.service[AdminConfig].map(AdminApiKeyAuthenticatorImpl(_)))
}
