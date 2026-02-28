package org.hyperledger.identus.notifications

import zio.IO

trait EventProducer[A]:
  def send(event: Event[A]): IO[EventNotificationServiceError, Unit]
