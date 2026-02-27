package org.hyperledger.identus.credentialstatus.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.credentialstatus.controller.http.StatusListCredential
import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.pollux.core.service.CredentialStatusListService
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID
import scala.language.implicitConversions

class CredentialStatusControllerImpl(
    credentialStatusListService: CredentialStatusListService
) extends CredentialStatusController {

  def getStatusListCredentialById(id: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, StatusListCredential] =
    credentialStatusListService
      .getById(id)
      .flatMap(StatusListCredential.fromCredentialStatusListEntry)

  def revokeCredentialById(
      id: DidCommID
  )(implicit rc: RequestContext): ZIO[WalletAccessContext, ErrorResponse, Unit] =
    credentialStatusListService.revokeByIssueCredentialRecordId(id)

}

object CredentialStatusControllerImpl {
  val layer: URLayer[CredentialStatusListService, CredentialStatusControllerImpl] =
    ZLayer.fromFunction(CredentialStatusControllerImpl(_))
}
