package org.hyperledger.identus.agent.vdr

import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.ZIO

trait VdrOperationSigner {
  def signCreate(
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation]
  def signUpdate(
      previousEventHash: Array[Byte],
      data: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation]
  def signDeactivate(
      previousEventHash: Array[Byte],
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, VdrServiceError.MissingVdrKey, node_models.SignedAtalaOperation]
}
