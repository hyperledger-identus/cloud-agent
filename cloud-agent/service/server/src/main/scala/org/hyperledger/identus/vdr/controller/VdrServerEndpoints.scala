package org.hyperledger.identus.vdr.controller

import sttp.tapir.ztapir.*
import zio.*

class VdrServerEndpoints(vdrController: VdrController) {

  private val readEntryServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.readEntry.zServerLogic { case i =>
      vdrController.getVdrEntry
    }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    readEntryServerEndpoint
  )
}

object VdrServerEndpoints {
  def all: URIO[VdrController, List[ZServerEndpoint[Any, Any]]] = ZIO.fromFunction(VdrServerEndpoints(_)).map(_.all)
}
