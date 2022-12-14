package io.iohk.atala.agent.server

import zio.*
import io.iohk.atala.mercury._
import org.didcommx.didcomm.DIDComm
import io.iohk.atala.resolvers.UniversalDidResolver
import io.iohk.atala.castor.sql.repository.{Migrations => CastorMigrations}
import io.iohk.atala.pollux.sql.repository.{Migrations => PolluxMigrations}
import io.iohk.atala.connect.sql.repository.{Migrations => ConnectMigrations}
import io.iohk.atala.agent.server.sql.{Migrations => AgentMigrations}
import io.iohk.atala.agent.walletapi.service.ManagedDIDService
import io.iohk.atala.resolvers.DIDResolver
import io.iohk.atala.agent.server.http.ZioHttpClient

object Main extends ZIOAppDefault {
  def agentLayer(peer: PeerDID): ZLayer[Any, Nothing, AgentServiceAny] =
    ZLayer.succeed(
      io.iohk.atala.mercury.AgentServiceAny(
        new DIDComm(UniversalDidResolver, peer.getSecretResolverInMemory),
        peer.did
      )
    )

  private def createAndStorePeerDID(serviceEndpoint: String): RIO[ManagedDIDService, PeerDID] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      peerDID <- managedDIDService.createAndStorePeerDID(serviceEndpoint)
      _ <- ZIO.logInfo(s"New DID: ${peerDID.did}")
    } yield peerDID
  }

  override def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console
        .printLine("""
      |██████╗ ██████╗ ██╗███████╗███╗   ███╗
      |██╔══██╗██╔══██╗██║██╔════╝████╗ ████║
      |██████╔╝██████╔╝██║███████╗██╔████╔██║
      |██╔═══╝ ██╔══██╗██║╚════██║██║╚██╔╝██║
      |██║     ██║  ██║██║███████║██║ ╚═╝ ██║
      |╚═╝     ╚═╝  ╚═╝╚═╝╚══════╝╚═╝     ╚═╝
      |
      | █████╗  ██████╗ ███████╗███╗   ██╗████████╗
      |██╔══██╗██╔════╝ ██╔════╝████╗  ██║╚══██╔══╝
      |███████║██║  ███╗█████╗  ██╔██╗ ██║   ██║
      |██╔══██║██║   ██║██╔══╝  ██║╚██╗██║   ██║
      |██║  ██║╚██████╔╝███████╗██║ ╚████║   ██║
      |╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚═╝  ╚═══╝   ╚═╝
      |""".stripMargin)
        .ignore

      restServicePort <- System.env("REST_SERVICE_PORT").map {
        case Some(s) if s.toIntOption.isDefined => s.toInt
        case _                                  => 8080
      }
      _ <- ZIO.logInfo(s"REST Service port => $restServicePort")

      didCommServiceUrl <- System.env("DIDCOMM_SERVICE_URL").map {
        case Some(s) => s
        case _       => "http://localhost"
      }
      _ <- ZIO.logInfo(s"DIDComm Service URL => $didCommServiceUrl")

      didCommServicePort <- System.env("DIDCOMM_SERVICE_PORT").map {
        case Some(s) if s.toIntOption.isDefined => s.toInt
        case _                                  => 8090
      }
      _ <- ZIO.logInfo(s"DIDComm Service port => $didCommServicePort")

      // Execute migrations from Castor and Pollux libraries using Flyway
      _ <- ZIO
        .serviceWithZIO[PolluxMigrations](_.migrate)
        .provide(RepoModule.polluxDbConfigLayer >>> PolluxMigrations.layer)
      _ <- ZIO
        .serviceWithZIO[ConnectMigrations](_.migrate)
        .provide(RepoModule.connectDbConfigLayer >>> ConnectMigrations.layer)
      _ <- ZIO
        .serviceWithZIO[AgentMigrations](_.migrate)
        .provide(RepoModule.agentDbConfigLayer >>> AgentMigrations.layer)

      peerDID <- createAndStorePeerDID(didCommServiceUrl)
        .provide(AppModule.manageDIDServiceLayer)

      didCommLayer = agentLayer(peerDID)

      didCommExchangesFiber <- Modules.didCommExchangesJob
        .provide(didCommLayer, DIDResolver.layer, ZioHttpClient.layer)
        .debug
        .fork

      presentProofDidCommExchangesFiber <- Modules.presentProofExchangeJob
        .provide(didCommLayer, DIDResolver.layer, ZioHttpClient.layer)
        .debug
        .fork

      connectDidCommExchangesFiber <- Modules.connectDidCommExchangesJob
        .provide(didCommLayer, DIDResolver.layer, ZioHttpClient.layer)
        .debug
        .fork

      didCommServiceFiber <- Modules
        .didCommServiceEndpoint(didCommServicePort)
        .provide(
          didCommLayer,
          AppModule.credentialServiceLayer,
          AppModule.presentationServiceLayer,
          AppModule.connectionServiceLayer,
          AppModule.manageDIDServiceLayer
        )
        .debug
        .fork

      _ <- Modules
        .app(restServicePort)
        .provide(didCommLayer, AppModule.manageDIDServiceLayer, SystemModule.configLayer)
        .fork

      _ <- Modules.zioApp.fork
      _ <- ZIO.never
    } yield ()

}
