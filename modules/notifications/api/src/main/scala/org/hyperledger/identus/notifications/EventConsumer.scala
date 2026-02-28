package org.hyperledger.identus.notifications

import zio.IO

trait EventConsumer[A]:
  def poll(count: Int): IO[EventNotificationServiceError, Seq[Event[A]]]
