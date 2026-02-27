package org.hyperledger.identus.agent.walletapi.model.error

import org.hyperledger.identus.shared.models.{Failure, StatusCode}

import java.util.UUID

sealed trait EntityServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "EntityServiceError"
}

object EntityServiceError {
  final case class EntityNotFound(id: UUID)
      extends EntityServiceError(
        StatusCode.NotFound,
        s"There is no entity matching the given identifier: id=$id"
      )

  final case class WalletNotFound(walletId: UUID)
      extends EntityServiceError(
        StatusCode.NotFound,
        s"There is no wallet matching the given identifier: walletId:$walletId"
      )
}
