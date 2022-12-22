package io.iohk.atala.agent.walletapi

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.invariant.InvalidEnum
import io.iohk.atala.agent.walletapi.model.ManagedDIDState
import io.iohk.atala.castor.core.model.did.{PrismDID, PrismDIDOperation}
import io.iohk.atala.castor.core.model.ProtoModelHelper.*
import io.iohk.atala.prism.protos.node_models

import scala.util.Try
import scala.collection.immutable.ArraySeq

package object sql {

  sealed trait DIDPublicationStatusType
  object DIDPublicationStatusType {
    case object CREATED extends DIDPublicationStatusType
    case object PUBLICATION_PENDING extends DIDPublicationStatusType
    case object PUBLISHED extends DIDPublicationStatusType
  }

  given didPublicationStatusMeta: Meta[DIDPublicationStatusType] = pgEnumString(
    "DID_PUBLICATION_STATUS",
    {
      case "CREATED"             => DIDPublicationStatusType.CREATED
      case "PUBLICATION_PENDING" => DIDPublicationStatusType.PUBLICATION_PENDING
      case "PUBLISHED"           => DIDPublicationStatusType.PUBLISHED
      case s                     => throw InvalidEnum[DIDPublicationStatusType](s)
    },
    {
      case DIDPublicationStatusType.CREATED             => "CREATED"
      case DIDPublicationStatusType.PUBLICATION_PENDING => "PUBLICATION_PENDING"
      case DIDPublicationStatusType.PUBLISHED           => "PUBLISHED"
    }
  )

  given prismDIDGet: Get[PrismDID] = Get[String].map(PrismDID.fromString(_).left.map(Exception(_)).toTry.get)
  given prismDIDPut: Put[PrismDID] = Put[String].contramap(_.asCanonical.toString)

  final case class DIDPublicationStateRow(
      did: PrismDID,
      publicationStatus: DIDPublicationStatusType,
      atalaOperationContent: Array[Byte],
      publishOperationId: Option[Array[Byte]]
  ) {
    def toDomain: Try[ManagedDIDState] = {
      publicationStatus match {
        case DIDPublicationStatusType.CREATED => createDIDOperation.map(ManagedDIDState.Created.apply)
        case DIDPublicationStatusType.PUBLICATION_PENDING =>
          for {
            createDIDOperation <- createDIDOperation
            operationId <- publishOperationId
              .toRight(RuntimeException(s"DID publication operation id does not exists for PUBLICATION_PENDING status"))
              .toTry
          } yield ManagedDIDState.PublicationPending(createDIDOperation, ArraySeq.from(operationId))
        case DIDPublicationStatusType.PUBLISHED =>
          for {
            createDIDOperation <- createDIDOperation
            operationId <- publishOperationId
              .toRight(RuntimeException(s"DID publication operation id does not exists for PUBLISHED status"))
              .toTry
          } yield ManagedDIDState.Published(createDIDOperation, ArraySeq.from(operationId))
      }
    }

    private def createDIDOperation: Try[PrismDIDOperation.Create] = {
      Try(node_models.AtalaOperation.parseFrom(atalaOperationContent))
        .flatMap { atalaOperation =>
          atalaOperation.operation.createDid
            .toRight(
              s"cannot extract CreateDIDOperation from AtalaOperation (${atalaOperation.operation.getClass.getSimpleName} found)"
            )
            .flatMap(_.toDomain)
            .left
            .map(RuntimeException(_))
            .toTry
        }
    }
  }

  object DIDPublicationStateRow {
    def from(did: PrismDID, state: ManagedDIDState): DIDPublicationStateRow = {
      import DIDPublicationStatusType.*
      val (status, createOperation, publishedOperationId) = state match {
        case ManagedDIDState.Created(operation) => (CREATED, operation, None)
        case ManagedDIDState.PublicationPending(operation, operationId) =>
          (PUBLICATION_PENDING, operation, Some(operationId))
        case ManagedDIDState.Published(operation, operationId) => (PUBLISHED, operation, Some(operationId))
      }
      DIDPublicationStateRow(
        did = did,
        publicationStatus = status,
        atalaOperationContent = createOperation.toAtalaOperation.toByteArray,
        publishOperationId = publishedOperationId.map(_.toArray)
      )
    }
  }

}
