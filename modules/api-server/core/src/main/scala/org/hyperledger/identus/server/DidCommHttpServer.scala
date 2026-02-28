package org.hyperledger.identus.server

import org.hyperledger.identus.didcomm.controller.DIDCommServerEndpoints
import org.hyperledger.identus.server.config.AppConfig
import org.hyperledger.identus.server.http.ZHttp4sBlazeServer
import zio.*

object DidCommHttpServer {

  def run = for {
    allEndpoints <- DIDCommServerEndpoints.all
    server <- ZHttp4sBlazeServer.make("didcomm")
    appConfig <- ZIO.service[AppConfig]
    didCommServicePort = appConfig.agent.didCommEndpoint.http.port
    _ <- ZIO.logInfo(s"Running DIDComm Server on port '$didCommServicePort''")
    _ <- server.start(allEndpoints, didCommServicePort).debug
  } yield ()

}
