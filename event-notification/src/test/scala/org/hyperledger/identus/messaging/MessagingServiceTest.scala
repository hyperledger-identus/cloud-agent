package org.hyperledger.identus.messaging

import org.hyperledger.identus.shared.messaging
import org.hyperledger.identus.shared.messaging.{Message, MessagingService, Serde}
import zio.{durationInt, Random, Schedule, Scope, URIO, ZIO, ZIOAppArgs, ZIOAppDefault, ZLayer}
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

import java.nio.charset.StandardCharsets
import java.util.UUID

case class Customer(name: String)

object Customer {
  given encoder: JsonEncoder[Customer] = DeriveJsonEncoder.gen[Customer]
  given decoder: JsonDecoder[Customer] = DeriveJsonDecoder.gen[Customer]
  given serde: Serde[Customer] = new Serde[Customer]:
    override def serialize(t: Customer): Array[Byte] =
      t.toJson.getBytes(StandardCharsets.UTF_8)
    override def deserialize(ba: Array[Byte]): Customer =
      new String(ba, StandardCharsets.UTF_8).fromJson[Customer].getOrElse(Customer("Parsing Error"))
}

object MessagingServiceTest extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val effect = for {
      ms <- ZIO.service[MessagingService]
      consumer <- ms.makeConsumer[UUID, Customer]("identus-cloud-agent")
      producer <- ms.makeProducer[UUID, Customer]()
      f1 <- consumer
        .consume("Connect")(handle)
        .fork
      f2 <- Random.nextUUID
        .flatMap(uuid => producer.produce("Connect", uuid, Customer(s"Name $uuid")))
        .repeat(Schedule.spaced(500.millis))
        .fork
      _ <- ZIO.never
    } yield ()
    effect.provide(
      messaging.MessagingServiceConfig.inMemoryLayer,
      messaging.MessagingService.serviceLayer,
      ZLayer.succeed("Sample 'R' passed to handler"),
      zio.Scope.default
    )
  }

  def handle[K, V](msg: Message[K, V]): URIO[String, Unit] = for {
    tag <- ZIO.service[String]
    _ <- ZIO.logInfo(s"Handling new message [$tag]: ${msg.offset} - ${msg.key} - ${msg.value}")
  } yield ()
}
