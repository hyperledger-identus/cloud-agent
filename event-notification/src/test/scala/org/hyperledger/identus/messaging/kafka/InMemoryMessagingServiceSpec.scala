package org.hyperledger.identus.messaging.kafka

import org.hyperledger.identus.shared.messaging.*
import zio.*
import zio.test.*
import zio.test.Assertion.*

object InMemoryMessagingServiceSpec extends ZIOSpecDefault {
  val testLayer = MessagingServiceConfig.inMemoryLayer >+> MessagingService.serviceLayer >+>
    MessagingService.producerLayer[String, String] >+>
    MessagingService.consumerLayer[String, String]("test-group")

  def spec = suite("InMemoryMessagingServiceSpec")(
    test("should produce and consume messages") {

      val key = "key"
      val value = "value"
      val topic = "test-topic"
      for {
        producer <- ZIO.service[Producer[String, String]]
        consumer <- ZIO.service[Consumer[String, String]]
        promise <- Promise.make[Nothing, Message[String, String]]
        _ <- producer.produce(topic, key, value)
        _ <- consumer
          .consume(topic) { msg =>
            promise.succeed(msg).unit
          }
          .fork
        receivedMessage <- promise.await
      } yield assert(receivedMessage)(equalTo(Message(key, value, 1L, 0)))
    }.provideLayer(testLayer),
    test("should produce and consume 5 messages") {
      val topic = "test-topic"
      val messages = List(
        ("key1", "value1"),
        ("key2", "value2"),
        ("key3", "value3"),
        ("key4", "value4"),
        ("key5", "value5")
      )

      for {
        producer <- ZIO.service[Producer[String, String]]
        consumer <- ZIO.service[Consumer[String, String]]
        promise <- Promise.make[Nothing, List[Message[String, String]]]
        ref <- Ref.make(List.empty[Message[String, String]])

        _ <- ZIO.foreach(messages) { case (key, value) =>
          producer.produce(topic, key, value) *> ZIO.logDebug(s"Produced message: $key -> $value")
        }
        _ <- consumer
          .consume(topic) { msg =>
            ZIO.logDebug(s"Consumed message: ${msg.key} -> ${msg.value}") *>
              ref.update(_ :+ msg) *> ref.get.flatMap { msgs =>
                if (msgs.size == messages.size) promise.succeed(msgs).unit else ZIO.unit
              }
          }
          .fork
        receivedMessages <- promise.await
        _ <- ZIO.logDebug(s"Received messages: ${receivedMessages.map(m => (m.key, m.value))}")
      } yield assert(receivedMessages.map(m => (m.key, m.value)).sorted)(
        equalTo(messages.sorted)
      )
    }.provideLayer(testLayer),
  )
}
