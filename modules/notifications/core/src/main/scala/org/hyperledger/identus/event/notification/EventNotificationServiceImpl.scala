package org.hyperledger.identus.event.notification

import org.hyperledger.identus.event.notification.EventNotificationServiceError.EventSendingFailed
import zio.{IO, Queue, URLayer, ZIO, ZLayer}
import zio.concurrent.ConcurrentMap

class EventNotificationServiceImpl(queueMap: ConcurrentMap[String, Queue[Event[?]]], queueCapacity: Int)
    extends EventNotificationService:

  private def getOrCreateQueue(topic: String): IO[EventNotificationServiceError, Queue[Event[?]]] = {
    for {
      maybeQueue <- queueMap.get(topic)
      queue <- maybeQueue match
        case Some(value) => ZIO.succeed(value)
        case None        => Queue.sliding(queueCapacity)
      _ <- queueMap.put(topic, queue)
    } yield queue
  }

  override def consumer[A](
      topic: String
  ): IO[EventNotificationServiceError, EventConsumer[A]] =
    ZIO.succeed(new EventConsumer[A] {
      override def poll(count: Int): IO[EventNotificationServiceError, Seq[Event[A]]] = for {
        queue <- getOrCreateQueue(topic)
        events <- queue.takeBetween(1, count)
        decodedEvents <- ZIO.foreach(events)(e => ZIO.succeed(e.asInstanceOf[Event[A]]))
      } yield decodedEvents
    })

  override def producer[A](
      topic: String
  ): IO[EventNotificationServiceError, EventProducer[A]] =
    ZIO.succeed(new EventProducer[A] {
      override def send(event: Event[A]): IO[EventNotificationServiceError, Unit] = for {
        queue <- getOrCreateQueue(topic)
        succeeded <- queue.offer(event)
        _ <- if (succeeded) ZIO.unit else ZIO.fail(EventSendingFailed("Queue.offer returned 'false'"))
      } yield ()
    })

object EventNotificationServiceImpl {
  val layer: URLayer[Int, EventNotificationServiceImpl] =
    ZLayer.fromZIO(
      for {
        map <- ConcurrentMap.make[String, Queue[Event[?]]]()
        capacity <- ZIO.service[Int]
      } yield new EventNotificationServiceImpl(map, capacity)
    )
}
