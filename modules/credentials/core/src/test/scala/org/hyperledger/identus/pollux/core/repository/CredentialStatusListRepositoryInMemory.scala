package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.vc.jwt.{Issuer, StatusPurpose}
import org.hyperledger.identus.pollux.vc.jwt.revocation.BitString
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.time.Instant
import java.util.UUID

class CredentialStatusListRepositoryInMemory(
    walletToStatusListRefs: Ref[Map[WalletId, Ref[Map[UUID, CredentialStatusList]]]],
    statusListToCredInStatusListRefs: Ref[Map[UUID, Ref[Map[UUID, CredentialInStatusList]]]]
) extends CredentialStatusListRepository {

  private def walletToStatusListStorageRefs: URIO[WalletAccessContext, Ref[Map[UUID, CredentialStatusList]]] =
    for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      refs <- walletToStatusListRefs.get
      maybeWalletRef = refs.get(walletId)
      walletRef <- maybeWalletRef
        .fold {
          for {
            ref <- Ref.make(Map.empty[UUID, CredentialStatusList])
            _ <- walletToStatusListRefs.set(refs.updated(walletId, ref))
          } yield ref
        }(ZIO.succeed)
    } yield walletRef

  private def allStatusListsStorageRefs: UIO[Ref[Map[UUID, CredentialStatusList]]] =
    for {
      refs <- walletToStatusListRefs.get
      allRefs = refs.values.toList
      allRefsMap <- ZIO
        .collectAll(allRefs.map(_.get))
        .map(_.foldLeft(Map.empty[UUID, CredentialStatusList]) { (acc, value) =>
          acc ++ value
        })
      ref <- Ref.make(allRefsMap)
    } yield ref

  private def statusListToCredInStatusListStorageRefs(
      statusListId: UUID
  ): UIO[Ref[Map[UUID, CredentialInStatusList]]] =
    for {
      refs <- statusListToCredInStatusListRefs.get
      maybeStatusListIdRef = refs.get(statusListId)
      statusListIdRef <- maybeStatusListIdRef.fold {
        for {
          ref <- Ref.make(Map.empty[UUID, CredentialInStatusList])
          _ <- statusListToCredInStatusListRefs.set(refs.updated(statusListId, ref))
        } yield ref
      }(ZIO.succeed)
    } yield statusListIdRef

  def findById(id: UUID): UIO[Option[CredentialStatusList]] = for {
    refs <- walletToStatusListRefs.get
    stores <- ZIO.foreach(refs.values.toList)(_.get)
    found = stores.flatMap(_.values).find(_.id == id)
  } yield found

  override def existsForIssueCredentialRecordId(id: DidCommID): UIO[Boolean] = for {
    refs <- statusListToCredInStatusListRefs.get
    stores <- ZIO.foreach(refs.values)(_.get)
    exists = stores.flatMap(_.values).exists(_.issueCredentialRecordId == id)
  } yield exists

  override def incrementAndGetStatusListIndex(
      jwtIssuer: Issuer,
      statusListRegistryUrl: String
  ): URIO[WalletAccessContext, (UUID, Int)] =
    def getLatestOfTheWallet: URIO[WalletAccessContext, Option[CredentialStatusList]] = for {
      storageRef <- walletToStatusListStorageRefs
      storage <- storageRef.get
      latest = storage.toSeq
        .sortBy(_._2.createdAt) { (x, y) => if x.isAfter(y) then -1 else 1 /* DESC */ }
        .headOption
        .map(_._2)
    } yield latest

    def createNewForTheWallet(
        id: UUID,
        jwtIssuer: Issuer,
        issued: Instant,
        credentialStr: String
    ): URIO[WalletAccessContext, CredentialStatusList] = {
      val issuerDid = jwtIssuer.did
      val canonical = PrismDID.fromString(issuerDid.toString).fold(e => throw RuntimeException(e), _.asCanonical)

      for {
        storageRef <- walletToStatusListStorageRefs
        walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
        newCredentialStatusList = CredentialStatusList(
          id = id,
          walletId = walletId,
          issuer = canonical,
          issued = issued,
          purpose = StatusPurpose.Revocation,
          statusListCredential = credentialStr,
          size = BitString.MIN_SL2021_SIZE,
          lastUsedIndex = 0,
          createdAt = Instant.now(),
          updatedAt = None
        )
        _ <- storageRef.update(r => r + (newCredentialStatusList.id -> newCredentialStatusList))
      } yield newCredentialStatusList
    }

    def updateLastUsedIndex(statusListId: UUID, lastUsedIndex: Int) =
      for {
        walletToStatusListStorageRef <- walletToStatusListStorageRefs
        _ <- walletToStatusListStorageRef.update(r => {
          val value = r.get(statusListId)
          value.fold(r) { v =>
            val updated = v.copy(lastUsedIndex = lastUsedIndex, updatedAt = Some(Instant.now))
            r.updated(statusListId, updated)
          }
        })
      } yield ()

    for {
      id <- ZIO.succeed(UUID.randomUUID())
      newStatusListVC <- createStatusListVC(jwtIssuer, statusListRegistryUrl, id).orDie
      maybeStatusList <- getLatestOfTheWallet
      statusList <- maybeStatusList match
        case Some(csl) if csl.lastUsedIndex < csl.size => ZIO.succeed(csl)
        case _ => createNewForTheWallet(id, jwtIssuer, Instant.now(), newStatusListVC)
      newIndex = statusList.lastUsedIndex + 1
      _ <- updateLastUsedIndex(statusList.id, newIndex)
    } yield (statusList.id, newIndex)

  def allocateSpaceForCredential(
      issueCredentialRecordId: DidCommID,
      credentialStatusListId: UUID,
      statusListIndex: Int
  ): URIO[WalletAccessContext, Unit] = {
    val newCredentialInStatusList = CredentialInStatusList(
      id = UUID.randomUUID(),
      issueCredentialRecordId = issueCredentialRecordId,
      credentialStatusListId = credentialStatusListId,
      statusListIndex = statusListIndex,
      isCanceled = false,
      isProcessed = false,
      createdAt = Instant.now(),
      updatedAt = None
    )

    for {
      credentialInStatusListStorageRef <- statusListToCredInStatusListStorageRefs(credentialStatusListId)
      _ <- credentialInStatusListStorageRef.update(r => r + (newCredentialInStatusList.id -> newCredentialInStatusList))
    } yield ()

  }

  def revokeByIssueCredentialRecordId(
      issueCredentialRecordId: DidCommID
  ): URIO[WalletAccessContext, Unit] = {
    for {
      statusListsRefs <- walletToStatusListStorageRefs
      statusLists <- statusListsRefs.get
      credInStatusListsRefs <- ZIO
        .collectAll(statusLists.keys.map(k => statusListToCredInStatusListStorageRefs(k)))
        .map(_.toVector)
      _ = credInStatusListsRefs.foreach(ref =>
        ref.update { credInStatusListsMap =>
          val maybeFound = credInStatusListsMap.find(_._2.issueCredentialRecordId == issueCredentialRecordId)
          maybeFound.fold(credInStatusListsMap) { case (id, value) =>
            if (!value.isCanceled) {
              credInStatusListsMap.updated(id, value.copy(isCanceled = true, updatedAt = Some(Instant.now())))
              credInStatusListsMap
            } else credInStatusListsMap
          }

        }
      )
    } yield ()
  }

  override def getCredentialStatusListIds: UIO[Seq[(WalletId, UUID)]] =
    for {
      statusListsRefs <- allStatusListsStorageRefs
      statusLists <- statusListsRefs.get
    } yield statusLists.values.toList.map(csl => (csl.walletId, csl.id))

  def getCredentialStatusListsWithCreds(
      statusListId: UUID
  ): URIO[WalletAccessContext, CredentialStatusListWithCreds] = {
    for {
      statusListsRefs <- allStatusListsStorageRefs
      statusLists <- statusListsRefs.get
      statusList = statusLists(statusListId)
      credsInStatusList <- statusListToCredInStatusListStorageRefs(statusList.id).flatMap(_.get.map(_.values.toList))
    } yield CredentialStatusListWithCreds(
      id = statusList.id,
      walletId = statusList.walletId,
      issuer = statusList.issuer,
      issued = statusList.issued,
      purpose = statusList.purpose,
      statusListCredential = statusList.statusListCredential,
      size = statusList.size,
      lastUsedIndex = statusList.lastUsedIndex,
      credentials = credsInStatusList.map { cred =>
        CredInStatusList(
          id = cred.id,
          issueCredentialRecordId = cred.issueCredentialRecordId,
          statusListIndex = cred.statusListIndex,
          isCanceled = cred.isCanceled,
          isProcessed = cred.isProcessed,
        )
      }
    )
  }

  def updateStatusListCredential(
      credentialStatusListId: UUID,
      statusListCredential: String
  ): URIO[WalletAccessContext, Unit] = {
    for {
      statusListsRefs <- walletToStatusListStorageRefs
      _ <- statusListsRefs.update { statusLists =>
        statusLists.updatedWith(credentialStatusListId) { maybeCredentialStatusList =>
          maybeCredentialStatusList.map { credentialStatusList =>
            credentialStatusList.copy(statusListCredential = statusListCredential, updatedAt = Some(Instant.now()))
          }
        }
      }
    } yield ()

  }

  def markAsProcessedMany(
      credsInStatusListIds: Seq[UUID]
  ): URIO[WalletAccessContext, Unit] = for {
    statusListsRefs <- walletToStatusListStorageRefs
    statusLists <- statusListsRefs.get
    credInStatusListsRefs <- ZIO
      .collectAll(statusLists.keys.map(k => statusListToCredInStatusListStorageRefs(k)))
      .map(_.toVector)
    _ = credInStatusListsRefs.foreach(ref =>
      ref.update { credInStatusListsMap =>
        credInStatusListsMap.transform { (id, credInStatusList) =>
          if (credsInStatusListIds.contains(id))
            credInStatusList.copy(isProcessed = true, updatedAt = Some(Instant.now()))
          else credInStatusList
        }
      }
    )
  } yield ()

}

object CredentialStatusListRepositoryInMemory {
  val layer: ULayer[CredentialStatusListRepositoryInMemory] = ZLayer.fromZIO(
    for {
      walletToStatusList <- Ref
        .make(Map.empty[WalletId, Ref[Map[UUID, CredentialStatusList]]])
      statusListIdToCredInStatusList <- Ref.make(Map.empty[UUID, Ref[Map[UUID, CredentialInStatusList]]])
    } yield CredentialStatusListRepositoryInMemory(walletToStatusList, statusListIdToCredInStatusList)
  )
}

private case class CredentialInStatusList(
    id: UUID,
    issueCredentialRecordId: DidCommID,
    credentialStatusListId: UUID,
    statusListIndex: Int,
    isCanceled: Boolean,
    isProcessed: Boolean,
    createdAt: Instant = Instant.now(),
    updatedAt: Option[Instant] = None
)
