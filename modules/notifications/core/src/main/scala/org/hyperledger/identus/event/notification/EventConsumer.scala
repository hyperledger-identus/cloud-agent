package org.hyperledger.identus.event.notification

import zio.IO

trait EventConsumer[A]:
  def poll(count: Int): IO[EventNotificationServiceError, Seq[Event[A]]]
