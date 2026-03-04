package org.hyperledger.identus.notifications

import zio.IO
import zio.json.ast.Json

trait JsonEventConsumer:
  def poll(count: Int): IO[EventNotificationServiceError, Seq[Event[Json]]]

object JsonEventConsumer:
  def fromTyped[A](consumer: EventConsumer[A], encode: A => Json): JsonEventConsumer =
    new JsonEventConsumer:
      def poll(count: Int) =
        consumer.poll(count).map(_.map(e => Event(e.`type`, e.id, e.ts, encode(e.data), e.walletId)))
