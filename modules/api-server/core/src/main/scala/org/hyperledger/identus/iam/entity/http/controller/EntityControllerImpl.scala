package org.hyperledger.identus.iam.entity.http.controller

import org.hyperledger.identus.agent.walletapi.model.Entity
import org.hyperledger.identus.agent.walletapi.service.EntityService
import org.hyperledger.identus.api.http.{ErrorResponse, RequestContext}
import org.hyperledger.identus.api.http.model.PaginationInput
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyAuthenticator
import org.hyperledger.identus.iam.entity.http.model.{CreateEntityRequest, EntityResponse, EntityResponsePage}
import zio.{IO, URLayer, ZLayer}
import zio.ZIO.succeed

import java.util.UUID
import scala.language.implicitConversions

case class EntityControllerImpl(service: EntityService, apiKeyAuthenticator: ApiKeyAuthenticator)
    extends EntityController {
  override def createEntity(in: CreateEntityRequest)(implicit rc: RequestContext): IO[ErrorResponse, EntityResponse] = {
    val id = in.id.getOrElse(UUID.randomUUID())
    val walletId = in.walletId.getOrElse(Entity.ZeroWalletId)
    for {
      entityToCreate <- succeed(Entity(id, in.name, walletId))
      createdEntity <- service.create(entityToCreate)
      self = rc.request.uri.addPath(createdEntity.id.toString).toString
    } yield EntityResponse.fromDomain(createdEntity).withSelf(self)
  }

  override def getEntity(id: UUID)(implicit rc: RequestContext): IO[ErrorResponse, EntityResponse] = {
    for {
      entity <- service.getById(id)
      self = rc.request.uri.toString
    } yield EntityResponse.fromDomain(entity).withSelf(self)
  }

  // TODO: add the missing pagination fields to the response
  override def getEntities(paginationIn: PaginationInput)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, EntityResponsePage] = {
    for {
      entities <- service.getAll(paginationIn.offset, paginationIn.limit)
      self = rc.request.uri.toString
    } yield EntityResponsePage.fromDomain(entities).withSelf(self)
  }

  override def updateEntityName(id: UUID, name: String)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, EntityResponse] = {
    for {
      _ <- service.updateName(id, name)
      updatedEntity <- service.getById(id)
      self = rc.request.uri.toString
    } yield EntityResponse.fromDomain(updatedEntity).withSelf(self)
  }

  override def updateEntityWalletId(id: UUID, walletId: UUID)(implicit
      rc: RequestContext
  ): IO[ErrorResponse, EntityResponse] = {
    for {
      _ <- service.assignWallet(id, walletId)
      updatedEntity <- service.getById(id)
      self = rc.request.uri.toString
    } yield EntityResponse.fromDomain(updatedEntity).withSelf(self)
  }

  override def deleteEntity(id: UUID)(implicit rc: RequestContext): IO[ErrorResponse, Unit] = {
    service
      .deleteById(id)
  }

  override def addApiKeyAuth(id: UUID, apiKey: String)(implicit rc: RequestContext): IO[ErrorResponse, Unit] = {
    service
      .getById(id)
      .flatMap(entity => apiKeyAuthenticator.add(entity.id, apiKey))
  }

  override def deleteApiKeyAuth(id: UUID, apiKey: String)(implicit rc: RequestContext): IO[ErrorResponse, Unit] = {
    service
      .getById(id)
      .flatMap(entity => apiKeyAuthenticator.delete(entity.id, apiKey))
  }
}

object EntityControllerImpl {
  val layer: URLayer[EntityService & ApiKeyAuthenticator, EntityController] =
    ZLayer.fromFunction(EntityControllerImpl(_, _))
}
