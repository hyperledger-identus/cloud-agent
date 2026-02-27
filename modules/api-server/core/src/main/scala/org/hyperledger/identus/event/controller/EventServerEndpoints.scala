package org.hyperledger.identus.event.controller

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.iam.authentication.{Authenticator, Authorizer, DefaultAuthenticator, SecurityLogic}
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

class EventServerEndpoints(
    eventController: EventController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  val createWebhookNotificationServerEndpoint: ZServerEndpoint[Any, Any] =
    EventEndpoints.createWebhookNotification
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, createWebhook) =>
          eventController
            .createWebhookNotification(createWebhook)(rc)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val listWebhookNotificationServerEndpoint: ZServerEndpoint[Any, Any] =
    EventEndpoints.listWebhookNotification
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac => rc =>
        eventController
          .listWebhookNotifications(rc)
          .provideSomeLayer(ZLayer.succeed(wac))
          .logTrace(rc)
      }

  val deleteWebhookNotificationServerEndpoint: ZServerEndpoint[Any, Any] =
    EventEndpoints.deleteWebhookNotification
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, id) =>
          eventController
            .deleteWebhookNotification(id)(rc)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    createWebhookNotificationServerEndpoint,
    listWebhookNotificationServerEndpoint,
    deleteWebhookNotificationServerEndpoint
  )

}

object EventServerEndpoints {
  def all: URIO[EventController & DefaultAuthenticator, List[ZServerEndpoint[Any, Any]]] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      eventController <- ZIO.service[EventController]
      eventEndpoints = new EventServerEndpoints(eventController, authenticator, authenticator)
    } yield eventEndpoints.all
  }
}
