package org.hyperledger.identus.iam.authorization.core

import org.hyperledger.identus.shared.models.{Failure, StatusCode, WalletId}

import java.util.UUID

sealed trait PermissionManagementServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "PermissionManagementServiceError"
}

object PermissionManagementServiceError {

  case class UserNotFoundById(userId: UUID, cause: Option[Throwable] = None)
      extends PermissionManagementServiceError(
        StatusCode.BadRequest,
        s"User $userId is not found" + cause.map(t => s" Cause: ${t.getMessage}")
      )

  case class WalletNotFoundByUserId(userId: UUID)
      extends PermissionManagementServiceError(
        StatusCode.BadRequest,
        s"Wallet for user $userId is not found"
      )

  case class WalletNotFoundById(walletId: WalletId)
      extends PermissionManagementServiceError(
        StatusCode.BadRequest,
        s"Wallet not found by ${walletId.toUUID}"
      )

  case class WalletResourceNotFoundById(walletId: WalletId)
      extends PermissionManagementServiceError(
        StatusCode.BadRequest,
        s"Wallet resource not found by ${walletId.toUUID}"
      )

  case class PermissionNotFoundById(userId: UUID, walletId: WalletId, walletResourceId: String)
      extends PermissionManagementServiceError(
        StatusCode.BadRequest,
        s"Permission not found by userId: $userId, walletId: ${walletId.toUUID}, walletResourceId: $walletResourceId"
      )

  case class PermissionNotAvailable(userId: UUID, cause: String)
      extends PermissionManagementServiceError(StatusCode.BadRequest, cause)

  case class ServiceError(cause: String) extends PermissionManagementServiceError(StatusCode.InternalServerError, cause)
}
