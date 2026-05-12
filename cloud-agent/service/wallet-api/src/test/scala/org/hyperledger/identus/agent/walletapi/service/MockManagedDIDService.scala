package org.hyperledger.identus.agent.walletapi.service

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.agent.walletapi.model.error.*
import org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
import org.hyperledger.identus.castor.core.model.did.{
  CanonicalPrismDID,
  LongFormPrismDID,
  PrismDIDOperation,
  ScheduleDIDOperationOutcome
}
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.PeerDID
import org.hyperledger.identus.shared.crypto.{Ed25519KeyPair, Secp256k1KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.mock.*
import zio.test.Assertion

object MockManagedDIDService extends Mock[ManagedDIDService] {

  object GetManagedDIDState extends Effect[CanonicalPrismDID, GetManagedDIDError, Option[ManagedDIDState]]
  object FindDIDKeyPair
      extends Effect[(CanonicalPrismDID, KeyId), Nothing, Option[Secp256k1KeyPair | Ed25519KeyPair | X25519KeyPair]]
  object IsDidDeactivated extends Effect[CanonicalPrismDID, GetManagedDIDError, Boolean]

  override val compose: URLayer[mock.Proxy, ManagedDIDService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ManagedDIDService {
        override def nonSecretStorage: DIDNonSecretStorage = ???

        override def syncManagedDIDState: IO[GetManagedDIDError, Unit] = ???

        override def syncUnconfirmedUpdateOperations: IO[GetManagedDIDError, Unit] = ???

        override def findDIDKeyPair(
            did: CanonicalPrismDID,
            keyId: KeyId
        ): UIO[Option[Secp256k1KeyPair | Ed25519KeyPair | X25519KeyPair]] =
          proxy(FindDIDKeyPair, (did, keyId))

        override def getManagedDIDState(
            did: CanonicalPrismDID
        ): IO[GetManagedDIDError, Option[ManagedDIDState]] =
          proxy(GetManagedDIDState, did)

        override def isDidDeactivated(
            did: CanonicalPrismDID
        ): IO[GetManagedDIDError, Boolean] =
          proxy(IsDidDeactivated, did)

        override def listManagedDIDPage(
            offset: Int,
            limit: Int
        ): IO[GetManagedDIDError, (Seq[ManagedDIDDetail], Int)] = ???

        override def publishStoredDID(
            did: CanonicalPrismDID
        ): IO[PublishManagedDIDError, ScheduleDIDOperationOutcome] = ???

        override def createAndStoreDID(
            didTemplate: ManagedDIDTemplate
        ): IO[CreateManagedDIDError, LongFormPrismDID] = ???

        override def updateManagedDID(
            did: CanonicalPrismDID,
            actions: Seq[UpdateManagedDIDAction]
        ): IO[UpdateManagedDIDError, ScheduleDIDOperationOutcome] = ???

        override def deactivateManagedDID(
            did: CanonicalPrismDID
        ): IO[UpdateManagedDIDError, ScheduleDIDOperationOutcome] = ???

        override def createAndStorePeerDID(
            serviceEndpoint: java.net.URL
        ): UIO[PeerDID] = ???

        override def getPeerDID(
            didId: DidId
        ): IO[DIDSecretStorageError.KeyNotFoundError, PeerDID] = ???
      }
    }

  def getManagedDIDStateExpectation(createOperation: PrismDIDOperation.Create): Expectation[ManagedDIDService] =
    MockManagedDIDService
      .GetManagedDIDState(
        assertion = Assertion.anything,
        result = Expectation.value(
          Some(
            ManagedDIDState(
              createOperation,
              0,
              PublicationState.Published(scala.collection.immutable.ArraySeq.empty)
            )
          )
        )
      )

  def findDIDKeyPairExpectation(keyPair: Secp256k1KeyPair): Expectation[ManagedDIDService] =
    MockManagedDIDService.FindDIDKeyPair(
      assertion = Assertion.anything,
      result = Expectation.value(Some(keyPair))
    )
}
