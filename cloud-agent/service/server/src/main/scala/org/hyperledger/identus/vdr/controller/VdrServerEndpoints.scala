package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.shared.models.WalletAccessContext
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
        { case (rc, data, params, _, _, _, didKeyId) =>
          vdrController
            .createVdrEntry(data, params.toMap, didKeyId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  private val updateEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.updateEntry
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, url, data, params, didKeyId) =>
          vdrController
            .updateVdrEntry(url, data, params.toMap, didKeyId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  private val deleteEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.deleteEntry
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, url, params, didKeyId) =>
          vdrController
            .deleteVdrEntry(url, params.toMap, didKeyId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  private val entryProofServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.entryProof
      .zServerLogic { case (rc, url) =>
        vdrController
          .entryProof(url)
          .logTrace(rc)
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    readEntryServerEndpoint,
    createEntryServerEndpoint,
    updateEntryServerEndpoint,
    deleteEntryServerEndpoint,
    entryProofServerEndpoint
  )
}

object VdrServerEndpoints {
  def all: URIO[VdrController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      vdrController <- ZIO.service[VdrController]
      authenticator <- ZIO.service[DefaultAuthenticator]
      vdrEndpoints = VdrServerEndpoints(vdrController, authenticator, authenticator)
    } yield vdrEndpoints.all
  }
}
