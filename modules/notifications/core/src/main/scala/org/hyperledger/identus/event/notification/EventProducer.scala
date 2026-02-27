package org.hyperledger.identus.event.notification

import zio.IO

trait EventProducer[A]:
  def send(event: Event[A]): IO[EventNotificationServiceError, Unit]
