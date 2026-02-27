package org.hyperledger.identus.shared.messaging

import org.hyperledger.identus.shared.messaging.kafka.{InMemoryMessagingService, ZKafkaMessagingServiceImpl}
import zio.{durationInt, Cause, Duration, EnvironmentTag, RIO, RLayer, Scope, Task, URIO, URLayer, ZIO, ZLayer}

import java.time.Instant
trait MessagingService {
  def makeConsumer[K, V](groupId: String)(implicit kSerde: Serde[K], vSerde: Serde[V]): RIO[Scope, Consumer[K, V]]
  def makeProducer[K, V]()(implicit kSerde: Serde[K], vSerde: Serde[V]): RIO[Scope, Producer[K, V]]
}

object MessagingService {

  case class RetryStep(topicName: String, consumerCount: Int, consumerBackoff: Duration, nextTopicName: Option[String])

  object RetryStep {
    def apply(topicName: String, consumerCount: Int, consumerBackoff: Duration, nextTopicName: String): RetryStep =
      RetryStep(topicName, consumerCount, consumerBackoff, Some(nextTopicName))
  }

  def consumeWithRetryStrategy[K: EnvironmentTag, V: EnvironmentTag, HR](
      groupId: String,
      handler: Message[K, V] => RIO[HR, Unit],
      steps: Seq[RetryStep]
  )(implicit kSerde: Serde[K], vSerde: Serde[V]): RIO[HR & Producer[K, V] & Scope & MessagingService, Unit] = {
    for {
      messagingService <- ZIO.service[MessagingService]
      messageProducer <- ZIO.service[Producer[K, V]]
      _ <- ZIO.foreachPar(steps) { step =>
        ZIO.foreachPar(1 to step.consumerCount)(_ =>
          for {
            consumer <- messagingService.makeConsumer[K, V](groupId)
            _ <- consumer
              .consume[HR](step.topicName) { m =>
                for {
                  // Wait configured backoff before processing message
                  millisSpentInQueue <- ZIO.succeed(Instant.now().toEpochMilli - m.timestamp)
                  sleepDelay = step.consumerBackoff.toMillis - millisSpentInQueue
                  _ <- ZIO.when(sleepDelay > 0)(ZIO.sleep(Duration.fromMillis(sleepDelay)))
                  _ <- handler(m)
                    .catchAll { t =>
                      for {
                        _ <- ZIO.logErrorCause(s"Error processing message: ${m.key} ", Cause.fail(t))
                        _ <- step.nextTopicName match
                          case Some(name) =>
                            messageProducer
                              .produce(name, m.key, m.value)
                              .catchAll(t =>
                                ZIO.logErrorCause("Unable to send message to the next topic", Cause.fail(t))
                              )
                          case None => ZIO.unit
                      } yield ()
                    }
                    .catchAllDefect(t => ZIO.logErrorCause(s"Defect processing message: ${m.key} ", Cause.fail(t)))
                } yield ()
              }
              .debug
              .fork
          } yield ()
        )
      }
    } yield ()
  }

  def consume[K: EnvironmentTag, V: EnvironmentTag, HR](
      groupId: String,
      topicName: String,
      consumerCount: Int,
      handler: Message[K, V] => RIO[HR, Unit]
  )(implicit kSerde: Serde[K], vSerde: Serde[V]): RIO[HR & Producer[K, V] & Scope & MessagingService, Unit] =
    consumeWithRetryStrategy(groupId, handler, Seq(RetryStep(topicName, consumerCount, 0.seconds, None)))

  val serviceLayer: URLayer[MessagingServiceConfig, MessagingService] =
    ZLayer
      .service[MessagingServiceConfig]
      .flatMap(config =>
        if (config.get.kafkaEnabled) ZKafkaMessagingServiceImpl.layer
        else InMemoryMessagingService.layer
      )

  def producerLayer[K: EnvironmentTag, V: EnvironmentTag](implicit
      kSerde: Serde[K],
      vSerde: Serde[V]
  ): RLayer[Scope & MessagingService, Producer[K, V]] = ZLayer.fromZIO(for {
    messagingService <- ZIO.service[MessagingService]
    producer <- messagingService.makeProducer[K, V]()
  } yield producer)

  def consumerLayer[K: EnvironmentTag, V: EnvironmentTag](groupId: String)(implicit
      kSerde: Serde[K],
      vSerde: Serde[V]
  ): RLayer[Scope & MessagingService, Consumer[K, V]] = ZLayer.fromZIO(for {
    messagingService <- ZIO.service[MessagingService]
    consumer <- messagingService.makeConsumer[K, V](groupId)
  } yield consumer)

}

case class Message[K, V](key: K, value: V, offset: Long, timestamp: Long)

trait Consumer[K, V] {
  def consume[HR](topic: String, topics: String*)(handler: Message[K, V] => URIO[HR, Unit]): RIO[HR, Unit]
}
trait Producer[K, V] {
  def produce(topic: String, key: K, value: V): Task[Unit]
}
