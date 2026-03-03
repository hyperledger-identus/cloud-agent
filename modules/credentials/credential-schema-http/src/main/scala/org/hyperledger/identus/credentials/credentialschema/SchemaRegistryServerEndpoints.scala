package org.hyperledger.identus.credentials.credentialschema

import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.api.http.RequestContext
import org.hyperledger.identus.credentials.credentialschema.controller.CredentialSchemaController
import org.hyperledger.identus.credentials.credentialschema.http.{CredentialSchemaInput, FilterInput}
import org.hyperledger.identus.credentials.credentialschema.SchemaRegistryEndpoints.*
import org.hyperledger.identus.iam.authentication.{Authenticator, AuthenticatorWithAuthZ, Authorizer, SecurityLogic}
import org.hyperledger.identus.shared.models.WalletAccessContext
import org.hyperledger.identus.wallet.model.BaseEntity
import org.hyperledger.identus.LogUtils.*
import sttp.tapir.ztapir.*
import zio.*

import java.util.UUID

class SchemaRegistryServerEndpoints(
    serviceName: String,
    credentialSchemaController: CredentialSchemaController,
    authenticator: Authenticator[BaseEntity],
    authorizer: Authorizer[BaseEntity]
) {

  object create {
    val http: ZServerEndpoint[Any, Any] = createSchemaHttpUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, schemaInput: CredentialSchemaInput) =>
          credentialSchemaController
            .createSchema(schemaInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }
    val did: ZServerEndpoint[Any, Any] = createSchemaDidUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, schemaInput: CredentialSchemaInput) =>
          credentialSchemaController
            .createSchemaDidUrl(serviceName, schemaInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }

    val all = List(http, did)

  }

  object update {
    val http: ZServerEndpoint[Any, Any] = updateSchemaHttpUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, id: UUID, schemaInput: CredentialSchemaInput) =>
          credentialSchemaController
            .updateSchema(id, schemaInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }
    val did: ZServerEndpoint[Any, Any] = updateSchemaDidUrlEndpoint
      .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
      .serverLogic { wac =>
        { case (ctx: RequestContext, id: UUID, schemaInput: CredentialSchemaInput) =>
          credentialSchemaController
            .updateSchemaDidUrl(serviceName, id, schemaInput)(ctx)
            .provideSomeLayer(ZLayer.succeed(wac))
            .logTrace(ctx)
        }
      }
    val all = List(http, did)

  }

  object get {
    val http: ZServerEndpoint[Any, Any] = getSchemaByIdHttpUrlEndpoint
      .zServerLogic { case (ctx: RequestContext, guid: UUID) =>
        credentialSchemaController
          .getSchemaByGuid(guid)(ctx)
          .logTrace(ctx)
      }
    val did: ZServerEndpoint[Any, Any] = getSchemaByIdDidUrlEndpoint
      .zServerLogic { case (ctx: RequestContext, guid: UUID) =>
        credentialSchemaController
          .getSchemaByGuidDidUrl(serviceName, guid)(ctx)
          .logTrace(ctx)
      }
    val all = List(http, did)

  }

  object getRaw {
    val http: ZServerEndpoint[Any, Any] = getRawSchemaByIdHttpUrlEndpoint
      .zServerLogic { case (ctx: RequestContext, guid: UUID) =>
        credentialSchemaController.getSchemaJsonByGuid(guid)(ctx)
      }
    val did: ZServerEndpoint[Any, Any] = getRawSchemaByIdDidUrlEndpoint
      .zServerLogic { case (ctx: RequestContext, guid: UUID) =>
        credentialSchemaController.getSchemaJsonByGuidDidUrl(serviceName, guid)(ctx)
      }
    val all = List(http, did)

  }

  object getMany {
    val http: ZServerEndpoint[Any, Any] =
      lookupSchemasByQueryHttpUrlEndpoint
        .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
        .serverLogic { wac =>
          { case (ctx: RequestContext, filter: FilterInput, paginationInput: PaginationInput, order: Option[Order]) =>
            credentialSchemaController
              .lookupSchemas(
                filter,
                paginationInput.toPagination,
                order,
              )(ctx)
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
          }
        }

    val did: ZServerEndpoint[Any, Any] =
      lookupSchemasByQueryDidUrlEndpoint
        .zServerSecurityLogic(SecurityLogic.authorizeWalletAccessWith(_)(authenticator, authorizer))
        .serverLogic { wac =>
          { case (ctx: RequestContext, filter: FilterInput, paginationInput: PaginationInput, order: Option[Order]) =>
            credentialSchemaController
              .lookupSchemasDidUrl(
                serviceName,
                filter,
                paginationInput.toPagination,
                order,
              )(ctx)
              .provideSomeLayer(ZLayer.succeed(wac))
              .logTrace(ctx)
          }
        }

    val all = List(http, did)
  }

  val all: List[ZServerEndpoint[Any, Any]] =
    create.all ++ update.all ++ getMany.all ++ getRaw.all ++ get.all
}

object SchemaRegistryServerEndpoints {
  def all(
      serviceName: String
  ): URIO[CredentialSchemaController & AuthenticatorWithAuthZ[BaseEntity], List[
    ZServerEndpoint[Any, Any]
  ]] = {
    for {
      authenticator <- ZIO.service[AuthenticatorWithAuthZ[BaseEntity]]
      schemaRegistryService <- ZIO.service[CredentialSchemaController]
      schemaRegistryEndpoints = new SchemaRegistryServerEndpoints(
        serviceName,
        schemaRegistryService,
        authenticator,
        authenticator
      )
    } yield schemaRegistryEndpoints.all
  }
}
