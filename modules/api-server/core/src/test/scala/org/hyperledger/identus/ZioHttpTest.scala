package org.hyperledger.identus

import zio.*
import zio.http.*
import zio.http.netty.NettyConfig
import zio.http.ZClient.Config

import java.net.URI
import java.time
import java.time.Instant

object ZioHttpTest extends ZIOAppDefault {

  def effect(url: URL): URIO[Client & Scope, Unit] = (for {
    start <- ZIO.succeed(Instant.now)
    client <- ZIO.service[Client]
    response <- client
      .request(
        Request(
          url = url, // TODO make ERROR type
          method = Method.GET,
          headers = Headers("content-type" -> "application/didcomm-encrypted+json")
        )
      )
      .flatMap { response =>
        response.body.asString
      }
    end <- ZIO.succeed(Instant.now)
    _ <- ZIO.logInfo(s"Query duration => ${time.Duration.between(start, end).toMillis}")
  } yield response).exit
    .flatMap {
      case Exit.Success(_) =>
        ZIO.unit
      case Exit.Failure(cause) =>
        ZIO.logError(s"Failure => ${cause.squash}") *> ZIO.succeed(cause.squash.printStackTrace())
    }

  val count = 10

  override def run = (for {
    url <- ZIO.fromOption(
      URL.fromURI(URI("http://127.0.0.1:8080/prism-agent/schema-registry/schemas/4e67f019-dceb-3c0f-ac8c-7bb90e2c4df6"))
    )
    _ <- ZIO.logInfo("Sending request...") *> effect(url) *> ZIO.logInfo("Request sent!")
    _ <- ZIO.logInfo("First request completed!! Other requests in 15 seconds") *> ZIO.sleep(15.seconds)
    _ <- ZIO.foreachPar(1 to count)(_ => effect(url)).withParallelism(1)
  } yield ())
    .provideSomeLayer {
      implicit val trace: Trace = Trace.empty
      (ZLayer.succeed(
        Config.default.copy(
          connectionPool = ConnectionPoolConfig.Disabled,
          idleTimeout = Some(2.seconds),
          connectionTimeout = Some(2.seconds),
        )
      ) ++
        ZLayer.succeed(NettyConfig.default) ++
        DnsResolver.default) >>> zio.http.Client.live
    }
}
