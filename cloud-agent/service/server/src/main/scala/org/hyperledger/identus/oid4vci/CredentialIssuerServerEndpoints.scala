package org.hyperledger.identus.oid4vci

import org.hyperledger.identus.agent.walletapi.model.BaseEntity
import org.hyperledger.identus.api.http.ErrorResponse
import org.hyperledger.identus.iam.authentication.*
import org.hyperledger.identus.oid4vci.controller.CredentialIssuerController
import org.hyperledger.identus.oid4vci.http.{CredentialErrorResponse, ExtendedErrorResponse, NonceResponse}
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import scala.language.implicitConversions

case class CredentialIssuerServerEndpoints(
    credentialIssuerController: CredentialIssuerController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity],
    oid4vciAuthenticatorFactory: Oid4vciAuthenticatorFactory
) {
  val credentialServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.credentialEndpoint
      .zServerSecurityLogic(ZIO.succeed)
      .serverLogic { jwt =>
        { case (rc, issuerId, request) =>
          oid4vciAuthenticatorFactory
            .make(issuerId)
            .flatMap(_.authenticate(jwt))
            .mapError[CredentialErrorResponse](identity)
            .flatMap { entity => // FIXME: this entity should be checked
              credentialIssuerController
                .issueCredential(rc, issuerId, request)
                .logTrace(rc)
            }
        }
      }

  val createCredentialOfferServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.createCredentialOfferEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId, request) =>
          credentialIssuerController
            .createCredentialOffer(rc, issuerId, request)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val nonceServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.nonceEndpoint
      .zServerSecurityLogic(ZIO.succeed)
      .serverLogic { jwt =>
        { case (rc, request) =>
          oid4vciAuthenticatorFactory
            .make(request.issuerState)
            .flatMap(_.authenticate(jwt))
            .flatMap { entity =>
              credentialIssuerController
                .getNonce(rc, request)
                .logTrace(rc)
            }
        }
      }

  val createCredentialIssuerServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.createCredentialIssuerEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, request) =>
          credentialIssuerController
            .createCredentialIssuer(rc, request)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val getCredentialIssuersServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.getCredentialIssuersEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case rc =>
          credentialIssuerController
            .getCredentialIssuers(rc)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val updateCredentialIssuerServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.updateCredentialIssuerEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId, request) =>
          credentialIssuerController
            .updateCredentialIssuer(rc, issuerId, request)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val deleteCredentialIssuerServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.deleteCredentialIssuerEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId) =>
          credentialIssuerController
            .deleteCredentialIssuer(rc, issuerId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val createCredentialConfigurationServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.createCredentialConfigurationEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId, request) =>
          credentialIssuerController
            .createCredentialConfiguration(rc, issuerId, request)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val getCredentialConfigurationServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.getCredentialConfigurationEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId, configurationId) =>
          credentialIssuerController
            .getCredentialConfiguration(rc, issuerId, configurationId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val deleteCredentialConfigurationServerEndpoint: ZServerEndpoint[Any, Any] =
    CredentialIssuerEndpoints.deleteCredentialConfigurationEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (rc, issuerId, configurationId) =>
          credentialIssuerController
            .deleteCredentialConfiguration(rc, issuerId, configurationId)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(rc)
        }
      }

  val issuerMetadataServerEndpoint: ZServerEndpoint[Any, Any] = CredentialIssuerEndpoints.issuerMetadataEndpoint
    .zServerLogic { { case (rc, didRef) =>
      credentialIssuerController.getIssuerMetadata(rc, didRef).logTrace(rc)
    }
    }

  val all: List[ZServerEndpoint[Any, Any]] = List(
    credentialServerEndpoint,
    createCredentialOfferServerEndpoint,
    nonceServerEndpoint,
    createCredentialIssuerServerEndpoint,
    getCredentialIssuersServerEndpoint,
    updateCredentialIssuerServerEndpoint,
    deleteCredentialIssuerServerEndpoint,
    createCredentialConfigurationServerEndpoint,
    getCredentialConfigurationServerEndpoint,
    deleteCredentialConfigurationServerEndpoint,
    issuerMetadataServerEndpoint
  )
}

object CredentialIssuerServerEndpoints {
  def all: URIO[
    DefaultAuthenticator & Oid4vciAuthenticatorFactory & CredentialIssuerController,
    List[ZServerEndpoint[Any, Any]]
  ] = {
    for {
      authenticator <- ZIO.service[DefaultAuthenticator]
      credentialIssuerController <- ZIO.service[CredentialIssuerController]
      oid4vciAuthenticatorFactory <- ZIO.service[Oid4vciAuthenticatorFactory]
      oidcEndpoints = CredentialIssuerServerEndpoints(
        credentialIssuerController,
        authenticator,
        authenticator,
        oid4vciAuthenticatorFactory
      )
    } yield oidcEndpoints.all
  }
}
