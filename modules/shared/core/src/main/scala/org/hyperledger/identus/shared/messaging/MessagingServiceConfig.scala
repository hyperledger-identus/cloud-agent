package org.hyperledger.identus.shared.messaging

import zio.{ULayer, ZLayer}

import java.time.Duration

case class MessagingServiceConfig(
    connectFlow: ConsumerJobConfig,
    issueFlow: ConsumerJobConfig,
    presentFlow: ConsumerJobConfig,
    didStateSync: ConsumerJobConfig,
    statusListSync: ConsumerJobConfig,
    inMemoryQueueCapacity: Int,
    kafkaEnabled: Boolean,
    kafka: Option[KafkaConfig]
)

final case class ConsumerJobConfig(
    consumerCount: Int,
    retryStrategy: Option[ConsumerRetryStrategy]
)

final case class ConsumerRetryStrategy(
    maxRetries: Int,
    initialDelay: Duration,
    maxDelay: Duration
)

final case class KafkaConfig(
    bootstrapServers: String,
    consumers: KafkaConsumersConfig
)

final case class KafkaConsumersConfig(
    autoCreateTopics: Boolean,
    maxPollRecords: Int,
    maxPollInterval: Duration,
    pollTimeout: Duration,
    rebalanceSafeCommits: Boolean
)

object MessagingServiceConfig {

  val inMemoryLayer: ULayer[MessagingServiceConfig] =
    ZLayer.succeed(
      MessagingServiceConfig(
        ConsumerJobConfig(1, None),
        ConsumerJobConfig(1, None),
        ConsumerJobConfig(1, None),
        ConsumerJobConfig(1, None),
        ConsumerJobConfig(1, None),
        100,
        false,
        None
      )
    )

}
