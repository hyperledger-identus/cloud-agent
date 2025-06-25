package org.hyperledger.identus.vdr.controller

import sttp.tapir.ztapir.*
import zio.*

class VdrServerEndpoints() {

  private val readDataServerEndpoint: ZServerEndpoint[Any, Any] =
    VdrEndpoints.readData.zServerLogic { case i =>
      ZIO.dieMessage("TODO")
    }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    readDataServerEndpoint
  )
}

object VdrServerEndpoints {
  def all: UIO[List[ZServerEndpoint[Any, Any]]] = ZIO.succeed(VdrServerEndpoints().all)
}
