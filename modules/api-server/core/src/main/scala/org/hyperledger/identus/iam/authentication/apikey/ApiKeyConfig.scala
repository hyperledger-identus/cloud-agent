package org.hyperledger.identus.iam.authentication.apikey

import org.hyperledger.identus.agent.server.config.AppConfig
import zio.*

case class ApiKeyConfig(salt: String, enabled: Boolean, authenticateAsDefaultUser: Boolean, autoProvisioning: Boolean)

object ApiKeyConfig {
  val layer: URLayer[AppConfig, ApiKeyConfig] =
    ZLayer.fromFunction((conf: AppConfig) => conf.agent.authentication.apiKey)
}
