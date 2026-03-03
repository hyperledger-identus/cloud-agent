package org.hyperledger.identus.server.jobs

import org.hyperledger.identus.credentials.core.model.error.CredentialServiceError
import org.hyperledger.identus.credentials.core.model.DidCommID
import org.hyperledger.identus.credentials.core.service.CredentialService
import org.hyperledger.identus.didcomm.protocol.invitation.v2.Invitation
import org.hyperledger.identus.shared.messaging.ConsumerJobConfig
import org.hyperledger.identus.shared.messaging.MessagingService.RetryStep
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.{durationInt, Duration, ZIO}
import zio.prelude.OrdOps

import java.time.Instant

trait BackgroundJobsHelper extends DIDResolutionHelper with JwtIssuerHelper with DIDCommHelper {

  def checkInvitationExpiry(
      id: DidCommID,
      invitation: Option[Invitation]
  ): ZIO[CredentialService & WalletAccessContext, CredentialServiceError, Unit] = {
    invitation.flatMap(_.expires_time) match {
      case Some(expiryTime) if Instant.now().getEpochSecond > expiryTime =>
        for {
          service <- ZIO.service[CredentialService]
          _ <- service.markCredentialOfferInvitationExpired(id)
          _ <- ZIO.fail(CredentialServiceError.InvitationExpired(expiryTime))
        } yield ()
      case _ => ZIO.unit
    }
  }

  def retryStepsFromConfig(topicName: String, jobConfig: ConsumerJobConfig): Seq[RetryStep] = {
    val retryTopics = jobConfig.retryStrategy match
      case None     => Seq.empty
      case Some(rs) =>
        (1 to rs.maxRetries).map(i =>
          (
            s"$topicName-retry-$i",
            rs.initialDelay.multipliedBy(Math.pow(2, i - 1).toLong).min(rs.maxDelay)
          )
        )
    val topics = retryTopics prepended (topicName, 0.seconds) appended (s"$topicName-DLQ", Duration.Infinity)
    (0 until topics.size - 1).map { i =>
      RetryStep(topics(i)._1, jobConfig.consumerCount, topics(i)._2, topics(i + 1)._1)
    }
  }
}
