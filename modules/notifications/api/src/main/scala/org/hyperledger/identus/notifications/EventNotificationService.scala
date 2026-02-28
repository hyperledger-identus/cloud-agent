package org.hyperledger.identus.notifications

import zio.IO

trait EventNotificationService:
  def consumer[A](topic: String): IO[EventNotificationServiceError, EventConsumer[A]]
  def producer[A](topic: String): IO[EventNotificationServiceError, EventProducer[A]]
