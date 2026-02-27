package org.hyperledger.identus.credentialstatus.controller

import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.credentialstatus.controller.http.StatusListCredential
import org.hyperledger.identus.pollux.core.model.DidCommID
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

trait CredentialStatusController {
  def getStatusListCredentialById(id: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, StatusListCredential]

  def revokeCredentialById(id: DidCommID)(implicit
      rc: RequestContext
  ): ZIO[WalletAccessContext, ErrorResponse, Unit]

}
