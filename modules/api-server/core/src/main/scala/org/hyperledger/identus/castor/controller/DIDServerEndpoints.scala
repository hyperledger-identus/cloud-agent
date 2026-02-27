package org.hyperledger.identus.castor.controller

import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class DIDServerEndpoints(didController: DIDController) {

  private val getDIDServerEndpoint: ZServerEndpoint[Any, Any] =
    DIDEndpoints.getDID.zServerLogic { case (ctx, didRef) =>
      didController
        .getDID(didRef)
        .logTrace(ctx)
    }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    getDIDServerEndpoint
  )

}

object DIDServerEndpoints {
  def all: URIO[DIDController, List[ZServerEndpoint[Any, Any]]] = {
    for {
      didController <- ZIO.service[DIDController]
      didEndpoints = new DIDServerEndpoints(didController)
    } yield didEndpoints.all
  }
}
