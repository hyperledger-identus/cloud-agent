package org.hyperledger.identus.server.config

import zio.ZIO

final case class FeatureFlagConfig(
    enableAnoncred: Boolean
) {
  def enableJWT: Boolean = true // Hardcoded for now // TODO FeatureNotImplemented
  def enableSDJWT: Boolean = true // Hardcoded for now // TODO FeatureNotImplemented

  def ifJWTIsEnabled[R, E, A](program: ZIO[R, E, A]) =
    if (enableJWT) program else ZIO.logWarning(FeatureFlagConfig.messageIfDisableForJWT)
  def ifSDJWTIsEnabled[R, E, A](program: ZIO[R, E, A]) =
    if (enableSDJWT) program else ZIO.logWarning(FeatureFlagConfig.messageIfDisableForSDJWT)
  def ifAnoncredIsEnabled[R, E, A](program: ZIO[R, E, A]) =
    if (enableAnoncred) program else ZIO.logWarning(FeatureFlagConfig.messageIfDisableForAnoncred)

  def ifJWTIsDisable[R, E, A](program: ZIO[R, E, A]) =
    if (!enableJWT) ZIO.logWarning(FeatureFlagConfig.messageIfDisableForJWT) *> program else ZIO.unit
  def ifSDJWTIsDisable[R, E, A](program: ZIO[R, E, A]) =
    if (!enableSDJWT) ZIO.logWarning(FeatureFlagConfig.messageIfDisableForSDJWT) *> program else ZIO.unit
  def ifAnoncredIsDisable[R, E, A](program: ZIO[R, E, A]) =
    if (!enableAnoncred) ZIO.logWarning(FeatureFlagConfig.messageIfDisableForAnoncred) *> program else ZIO.unit
}

object FeatureFlagConfig {
  def messageIfDisableForJWT = "Feature Disabled: Credential format JWT VC"
  def messageIfDisableForSDJWT = "Feature Disabled: Credential format SD JWT VC"
  def messageIfDisableForAnoncred = "Feature Disabled: Credential format Anoncred"
}
