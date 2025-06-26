package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.LogUtils.logTrace
import sttp.tapir.ztapir.*
import zio.*

class VdrServerEndpoints(
    vdrController: VdrController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  private val readEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.readEntry.zServerLogic { case (rc, url) =>
      vdrController
        .getVdrEntry(url)
        .logTrace(rc)
    }

  private val createEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.createEntry
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, data, params) =>
          vdrController
            .createVdrEntry(data, params.toMap)
            .logTrace(rc)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    readEntryServerEndpoint,
    createEntryServerEndpoint
  )
}

object VdrServerEndpoints {
  def all: URIO[VdrController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      vdrController <- ZIO.service[VdrController]
      vdrEndpoints = VdrServerEndpoints(vdrController, authenticator, authenticator)
    } yield vdrEndpoints.all
  }
}
