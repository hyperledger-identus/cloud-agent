package org.hyperledger.identus.vdr.controller

import org.hyperledger.identus.LogUtils.logTrace
import sttp.tapir.ztapir.*
import zio.*

class VdrServerEndpoints(
    vdrController: VdrController,
) {

  private val readEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.readEntry.zServerLogic { case (rc, url) =>
      vdrController
        .getVdrEntry(url)
        .logTrace(rc)
    }

  private val createEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.createEntry
      .zServerLogic { case (rc, data, params, _, _, _) =>
        vdrController
          .createVdrEntry(data, params.toMap)
          .logTrace(rc)
      }

  private val updateEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.updateEntry
      .zServerLogic { case (rc, url, data, params) =>
        vdrController
          .updateVdrEntry(url, data, params.toMap)
          .logTrace(rc)
      }

  private val deleteEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.deleteEntry
      .zServerLogic { case (rc, url, params) =>
        vdrController
          .deleteVdrEntry(url, params.toMap)
          .logTrace(rc)
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
  def all: URIO[VdrController, List[ZServerEndpoint[Any, Any]]] = {
    for {
      vdrController <- ZIO.service[VdrController]
      vdrEndpoints = VdrServerEndpoints(vdrController)
    } yield vdrEndpoints.all
  }
}
