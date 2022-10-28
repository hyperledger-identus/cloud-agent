package io.iohk.atala.agent.server

import akka.actor.BootstrapSetup
import akka.actor.setup.ActorSystemSetup
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.server.Route
import doobie.util.transactor.Transactor
import io.iohk.atala.agent.server.http.{HttpRoutes, HttpServer}
import io.iohk.atala.castor.core.service.{DIDService, DIDServiceImpl}
import io.iohk.atala.agent.server.http.marshaller.{
  DIDApiMarshallerImpl,
  DIDAuthenticationApiMarshallerImpl,
  DIDOperationsApiMarshallerImpl,
  DIDRegistrarApiMarshallerImpl,
  IssueCredentialsApiMarshallerImpl
}
import io.iohk.atala.agent.server.http.service.{
  DIDApiServiceImpl,
  DIDAuthenticationApiServiceImpl,
  DIDOperationsApiServiceImpl,
  DIDRegistrarApiServiceImpl,
  IssueCredentialsApiServiceImpl
}
import io.iohk.atala.castor.core.repository.DIDOperationRepository
import io.iohk.atala.agent.openapi.api.{
  DIDApi,
  DIDAuthenticationApi,
  DIDOperationsApi,
  DIDRegistrarApi,
  IssueCredentialsApi
}
import io.iohk.atala.castor.sql.repository.{JdbcDIDOperationRepository, TransactorLayer}
import zio.*
import zio.interop.catz.*
import cats.effect.std.Dispatcher
import com.typesafe.config.ConfigFactory
import doobie.util.transactor.Transactor
import io.grpc.ManagedChannelBuilder
import io.iohk.atala.agent.openapi.api.*
import io.iohk.atala.agent.server.config.AppConfig
import io.iohk.atala.agent.walletapi.service.ManagedDIDService
import io.iohk.atala.agent.server.http.marshaller.*
import io.iohk.atala.agent.server.http.service.*
import io.iohk.atala.agent.server.http.{HttpRoutes, HttpServer}
import io.iohk.atala.castor.core.repository.DIDOperationRepository
import io.iohk.atala.castor.core.service.{DIDService, DIDServiceImpl}
import io.iohk.atala.pollux.core.service.CredentialServiceImpl
import io.iohk.atala.castor.core.util.DIDOperationValidator
import io.iohk.atala.castor.sql.repository.{JdbcDIDOperationRepository, TransactorLayer}
import io.iohk.atala.iris.proto.service.IrisServiceGrpc
import io.iohk.atala.iris.proto.service.IrisServiceGrpc.IrisServiceStub
import io.iohk.atala.pollux.core.repository.CredentialRepository
import io.iohk.atala.pollux.core.service.CredentialService
import io.iohk.atala.pollux.sql.repository.JdbcCredentialRepository
import io.iohk.atala.agent.server.jobs.*
import zio.*
import zio.config.typesafe.TypesafeConfigSource
import zio.config.{ReadError, read}
import zio.interop.catz.*
import zio.stream.ZStream
import zhttp.http._
import zhttp.service.Server

import java.util.concurrent.Executors

object Modules {

  val app: Task[Unit] = {
    val httpServerApp = HttpRoutes.routes.flatMap(HttpServer.start(8080, _))

    httpServerApp
      .provideLayer(SystemModule.actorSystemLayer ++ HttpModule.layers)
      .unit
  }

  val didCommServiceEndpoint: Task[Nothing] = {
    val app: HttpApp[Any, Nothing] =
      Http.collect[Request] { case Method.POST -> !! / "did-comm-v2" =>
        // TODO add DIDComm messages parsing logic here! 
        Response.text("Hello World!").setStatus(Status.Accepted)
      }
    Server.start(8090, app)
  }

  val didCommExchangesJob: Task[Unit] = {
    val effect = BackgroundJobs.didCommExchanges
      .provideLayer(AppModule.credentialServiceLayer)
    (effect repeat Schedule.spaced(10.seconds)).unit
  }

}

object SystemModule {
  val actorSystemLayer: TaskLayer[ActorSystem[Nothing]] = ZLayer.scoped(
    ZIO.acquireRelease(
      ZIO.executor
        .map(_.asExecutionContext)
        .flatMap(ec =>
          ZIO.attempt(ActorSystem(Behaviors.empty, "actor-system", BootstrapSetup().withDefaultExecutionContext(ec)))
        )
    )(system => ZIO.attempt(system.terminate()).orDie)
  )

  val configLayer: Layer[ReadError[String], AppConfig] = ZLayer.fromZIO {
    read(
      AppConfig.descriptor.from(
        TypesafeConfigSource.fromTypesafeConfig(
          ZIO.attempt(ConfigFactory.load())
        )
      )
    )
  }
}

object AppModule {
  val didOpValidatorLayer: ULayer[DIDOperationValidator] = DIDOperationValidator.layer(
    DIDOperationValidator.Config(
      publicKeyLimit = 50,
      serviceLimit = 50
    )
  )

  val didServiceLayer: TaskLayer[DIDService] =
    (GrpcModule.layers ++ RepoModule.layers ++ didOpValidatorLayer) >>> DIDServiceImpl.layer

  val manageDIDServiceLayer: TaskLayer[ManagedDIDService] =
    (didOpValidatorLayer ++ didServiceLayer) >>> ManagedDIDService.inMemoryStorage()

  val credentialServiceLayer: TaskLayer[CredentialService] =
    (GrpcModule.layers ++ RepoModule.layers) >>> CredentialServiceImpl.layer
}

object GrpcModule {
  val irisStubLayer: TaskLayer[IrisServiceStub] = {
    val stubLayer = ZLayer.fromZIO(
      ZIO
        .service[AppConfig]
        .map(_.iris.service)
        .flatMap(config =>
          ZIO.attempt(
            IrisServiceGrpc.stub(ManagedChannelBuilder.forAddress(config.host, config.port).usePlaintext.build)
          )
        )
    )
    SystemModule.configLayer >>> stubLayer
  }

  val layers = irisStubLayer
}

object HttpModule {
  val didApiLayer: TaskLayer[DIDApi] = {
    val serviceLayer = AppModule.didServiceLayer
    val apiServiceLayer = serviceLayer >>> DIDApiServiceImpl.layer
    val apiMarshallerLayer = DIDApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new DIDApi(_, _))
  }

  val didOperationsApiLayer: ULayer[DIDOperationsApi] = {
    val apiServiceLayer = DIDOperationsApiServiceImpl.layer
    val apiMarshallerLayer = DIDOperationsApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new DIDOperationsApi(_, _))
  }

  val didAuthenticationApiLayer: ULayer[DIDAuthenticationApi] = {
    val apiServiceLayer = DIDAuthenticationApiServiceImpl.layer
    val apiMarshallerLayer = DIDAuthenticationApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new DIDAuthenticationApi(_, _))
  }

  val didRegistrarApiLayer: TaskLayer[DIDRegistrarApi] = {
    val serviceLayer = AppModule.manageDIDServiceLayer
    val apiServiceLayer = serviceLayer >>> DIDRegistrarApiServiceImpl.layer
    val apiMarshallerLayer = DIDRegistrarApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new DIDRegistrarApi(_, _))
  }

  val issueCredentialsApiLayer: TaskLayer[IssueCredentialsApi] = {
    val serviceLayer = AppModule.credentialServiceLayer
    val apiServiceLayer = serviceLayer >>> IssueCredentialsApiServiceImpl.layer
    val apiMarshallerLayer = IssueCredentialsApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new IssueCredentialsApi(_, _))
  }

  val issueCredentialsProtocolApiLayer: TaskLayer[IssueCredentialsProtocolApi] = {
    val serviceLayer = AppModule.credentialServiceLayer
    val apiServiceLayer = serviceLayer >>> IssueCredentialsProtocolApiServiceImpl.layer
    val apiMarshallerLayer = IssueCredentialsProtocolApiMarshallerImpl.layer
    (apiServiceLayer ++ apiMarshallerLayer) >>> ZLayer.fromFunction(new IssueCredentialsProtocolApi(_, _))
  }

  val layers =
   
    didApiLayer ++ didOperationsApiLayer ++ didAuthenticationApiLayer ++ didRegistrarApiLayer ++ issueCredentialsApiLayer ++ issueCredentialsProtocolApiLayer
}

object RepoModule {
  val castorTransactorLayer: TaskLayer[Transactor[Task]] = {
    val transactorLayer = ZLayer.fromZIO {
      ZIO.service[AppConfig].map(_.castor.database).flatMap { config =>
        Dispatcher[Task].allocated.map { case (dispatcher, _) =>
          given Dispatcher[Task] = dispatcher
          TransactorLayer.hikari[Task](
            TransactorLayer.DbConfig(
              username = config.username,
              password = config.password,
              jdbcUrl = s"jdbc:postgresql://${config.host}:${config.port}/${config.databaseName}"
            )
          )
        }
      }
    }.flatten
    SystemModule.configLayer >>> transactorLayer
  }

  val polluxTransactorLayer: TaskLayer[Transactor[Task]] = {
    val transactorLayer = ZLayer.fromZIO {
      ZIO.service[AppConfig].map(_.pollux.database).flatMap { config =>
        Dispatcher[Task].allocated.map { case (dispatcher, _) =>
          given Dispatcher[Task] = dispatcher
          io.iohk.atala.pollux.sql.repository.TransactorLayer.hikari[Task](
            io.iohk.atala.pollux.sql.repository.TransactorLayer.DbConfig(
              username = config.username,
              password = config.password,
              jdbcUrl = s"jdbc:postgresql://${config.host}:${config.port}/${config.databaseName}"
            )
          )
        }
      }
    }.flatten

    SystemModule.configLayer >>> transactorLayer
  }

  val didOperationRepoLayer: TaskLayer[DIDOperationRepository[Task]] =
    castorTransactorLayer >>> JdbcDIDOperationRepository.layer

  val credentialRepoLayer: TaskLayer[CredentialRepository[Task]] =
    polluxTransactorLayer >>> JdbcCredentialRepository.layer

  val layers = didOperationRepoLayer ++ credentialRepoLayer
}
