package org.hyperledger.identus.agent.walletapi

import com.nimbusds.jose.jwk.OctetKeyPair
import doobie.*
import doobie.postgres.implicits.*
import doobie.util.invariant.InvalidEnum
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.agent.walletapi.model.{KeyManagementMode, ManagedDIDState, PublicationState}
import org.hyperledger.identus.castor.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  PrismDID,
  PrismDIDOperation,
  ScheduledDIDOperationStatus,
  VerificationRelationship
}
import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
import org.hyperledger.identus.shared.crypto.jwk.JWK
import org.hyperledger.identus.shared.models.WalletId
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*

import java.net.{URI, URL}
import java.time.Instant
import scala.collection.immutable.ArraySeq
import scala.util.Try

package object sql {

  sealed trait PublicationStatusType
  object PublicationStatusType {
    case object CREATED extends PublicationStatusType
    case object PUBLICATION_PENDING extends PublicationStatusType
    case object PUBLISHED extends PublicationStatusType

    def from(status: PublicationState): PublicationStatusType = status match {
      case PublicationState.Created()             => CREATED
      case PublicationState.PublicationPending(_) => PUBLICATION_PENDING
      case PublicationState.Published(_)          => PUBLISHED
    }
  }

  given Meta[VerificationRelationship | InternalKeyPurpose] = pgEnumString(
    "PRISM_DID_KEY_USAGE",
    {
      case "MASTER"                => InternalKeyPurpose.Master
      case "ISSUING"               => VerificationRelationship.AssertionMethod
      case "KEY_AGREEMENT"         => VerificationRelationship.KeyAgreement
      case "AUTHENTICATION"        => VerificationRelationship.Authentication
      case "REVOCATION"            => InternalKeyPurpose.Revocation
      case "CAPABILITY_INVOCATION" => VerificationRelationship.CapabilityInvocation
      case "CAPABILITY_DELEGATION" => VerificationRelationship.CapabilityDelegation
      case "VDR_SIGNING"           => InternalKeyPurpose.VDRSigning
      case s                       => throw InvalidEnum[VerificationRelationship | InternalKeyPurpose](s)
    },
    {
      case InternalKeyPurpose.Master                     => "MASTER"
      case VerificationRelationship.AssertionMethod      => "ISSUING"
      case VerificationRelationship.KeyAgreement         => "KEY_AGREEMENT"
      case VerificationRelationship.Authentication       => "AUTHENTICATION"
      case InternalKeyPurpose.Revocation                 => "REVOCATION"
      case VerificationRelationship.CapabilityInvocation => "CAPABILITY_INVOCATION"
      case VerificationRelationship.CapabilityDelegation => "CAPABILITY_DELEGATION"
      case InternalKeyPurpose.VDRSigning                 => "VDR_SIGNING"
    }
  )

  given Meta[KeyManagementMode] = pgEnumString(
    "PRISM_DID_KEY_MODE",
    {
      case "HD"     => KeyManagementMode.HD
      case "RANDOM" => KeyManagementMode.RANDOM
      case s        => throw InvalidEnum[KeyManagementMode](s)
    },
    {
      case KeyManagementMode.HD     => "HD"
      case KeyManagementMode.RANDOM => "RANDOM"
    }
  )

  given Meta[EllipticCurve] = pgEnumString(
    "CURVE_NAME",
    s => EllipticCurve.parseString(s).get,
    _.name
  )

  given Meta[PublicationStatusType] = pgEnumString(
    "PRISM_DID_WALLET_STATUS",
    {
      case "CREATED"             => PublicationStatusType.CREATED
      case "PUBLICATION_PENDING" => PublicationStatusType.PUBLICATION_PENDING
      case "PUBLISHED"           => PublicationStatusType.PUBLISHED
      case s                     => throw InvalidEnum[PublicationStatusType](s)
    },
    {
      case PublicationStatusType.CREATED             => "CREATED"
      case PublicationStatusType.PUBLICATION_PENDING => "PUBLICATION_PENDING"
      case PublicationStatusType.PUBLISHED           => "PUBLISHED"
    }
  )

  given Meta[ScheduledDIDOperationStatus] = pgEnumString(
    "PRISM_DID_OPERATION_STATUS",
    {
      case "PENDING_SUBMISSION"     => ScheduledDIDOperationStatus.Pending
      case "AWAIT_CONFIRMATION"     => ScheduledDIDOperationStatus.AwaitingConfirmation
      case "CONFIRMED_AND_APPLIED"  => ScheduledDIDOperationStatus.Confirmed
      case "CONFIRMED_AND_REJECTED" => ScheduledDIDOperationStatus.Rejected
      case s                        => throw InvalidEnum[ScheduledDIDOperationStatus](s)
    },
    {
      case ScheduledDIDOperationStatus.Pending              => "PENDING_SUBMISSION"
      case ScheduledDIDOperationStatus.AwaitingConfirmation => "AWAIT_CONFIRMATION"
      case ScheduledDIDOperationStatus.Confirmed            => "CONFIRMED_AND_APPLIED"
      case ScheduledDIDOperationStatus.Rejected             => "CONFIRMED_AND_REJECTED"
    }
  )

  given prismDIDGet: Get[PrismDID] = Get[String].map(PrismDID.fromString(_).left.map(Exception(_)).toTry.get)
  given prismDIDPut: Put[PrismDID] = Put[String].contramap(_.asCanonical.toString)

  given arraySeqByteGet: Get[ArraySeq[Byte]] = Get[Array[Byte]].map(ArraySeq.from)
  given arraySeqBytePut: Put[ArraySeq[Byte]] = Put[Array[Byte]].contramap(_.toArray)

  given urlGet: Get[URL] = Get[String].map(URI(_).toURL())
  given urlPut: Put[URL] = Put[String].contramap(_.toString())

  given octetKeyPairGet: Get[OctetKeyPair] = Get[String].map(OctetKeyPair.parse)
  given octetKeyPairPut: Put[OctetKeyPair] = Put[String].contramap(_.toJSONString)

  given jwkGet: Get[JWK] = Get[String].map(s => JWK.fromString(s).left.map(Exception(_)).toTry.get)
  given jwkPut: Put[JWK] = Put[String].contramap(_.toJsonString)

  given jsonGet: Get[Json] = Get[String].map(_.fromJson[Json] match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error)
  })
  given jsonPut: Put[Json] = Put[String].contramap(_.toString())

  final case class DIDStateRow(
      did: PrismDID,
      publicationStatus: PublicationStatusType,
      atalaOperationContent: Array[Byte],
      publishOperationId: Option[Array[Byte]],
      createdAt: Instant,
      updatedAt: Instant,
      didIndex: Int,
      walletId: WalletId
  ) {
    def toDomain: Try[ManagedDIDState] = {
      publicationStatus match {
        case PublicationStatusType.CREATED =>
          createDIDOperation.map(op => ManagedDIDState(op, didIndex, PublicationState.Created()))
        case PublicationStatusType.PUBLICATION_PENDING =>
          for {
            createDIDOperation <- createDIDOperation
            operationId <- publishOperationId
              .toRight(RuntimeException(s"DID publication operation id does not exists for PUBLICATION_PENDING status"))
              .toTry
          } yield ManagedDIDState(
            createDIDOperation,
            didIndex,
            PublicationState.PublicationPending(ArraySeq.from(operationId))
          )
        case PublicationStatusType.PUBLISHED =>
          for {
            createDIDOperation <- createDIDOperation
            operationId <- publishOperationId
              .toRight(RuntimeException(s"DID publication operation id does not exists for PUBLISHED status"))
              .toTry
          } yield ManagedDIDState(createDIDOperation, didIndex, PublicationState.Published(ArraySeq.from(operationId)))
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

  object DIDStateRow {
    def from(did: PrismDID, state: ManagedDIDState, now: Instant, walletId: WalletId): DIDStateRow = {
      val createOperation = state.createOperation
      val status = PublicationStatusType.from(state.publicationState)
      val publishedOperationId = state.publicationState match {
        case PublicationState.Created()                       => None
        case PublicationState.PublicationPending(operationId) => Some(operationId.toArray)
        case PublicationState.Published(operationId)          => Some(operationId.toArray)
      }
      DIDStateRow(
        did = did,
        publicationStatus = status,
        atalaOperationContent = createOperation.toAtalaOperation.toByteArray,
        publishOperationId = publishedOperationId.map(_.toArray),
        createdAt = now,
        updatedAt = now,
        didIndex = state.didIndex,
        walletId = walletId
      )
    }
  }

}
