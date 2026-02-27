package org.hyperledger.identus.castor.core.service

import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.error
import org.hyperledger.identus.shared.crypto.{Apollo, Secp256k1KeyPair}
import org.hyperledger.identus.shared.models.{Base64UrlString, KeyId}
import zio.{mock, IO, URLayer, ZIO, ZLayer}
import zio.mock.{Expectation, Mock, Proxy}
import zio.test.Assertion

import scala.collection.immutable.ArraySeq

// FIXME: move this to test code
object MockDIDService extends Mock[DIDService] {

  object ScheduleOperation extends Effect[SignedPrismDIDOperation, error.DIDOperationError, ScheduleDIDOperationOutcome]
  // FIXME leaving this out for now as it gives a "java.lang.AssertionError: assertion failed: class Array" compilation error
  // object GetScheduledDIDOperationDetail extends Effect[Array[Byte], error.DIDOperationError, Option[ScheduledDIDOperationDetail]]
  object ResolveDID extends Effect[PrismDID, error.DIDResolutionError, Option[(DIDMetadata, DIDData)]]

  override val compose: URLayer[mock.Proxy, DIDService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new DIDService {
        override def scheduleOperation(
            operation: SignedPrismDIDOperation
        ): IO[error.DIDOperationError, ScheduleDIDOperationOutcome] =
          proxy(ScheduleOperation, operation)

        override def getScheduledDIDOperationDetail(
            operationId: Array[Byte]
        ): IO[error.DIDOperationError, Option[ScheduledDIDOperationDetail]] =
          ???

        override def resolveDID(did: PrismDID): IO[error.DIDResolutionError, Option[(DIDMetadata, DIDData)]] =
          proxy(ResolveDID, did)
      }
    }

  private def createDIDInternal(
      verificationRelationship: VerificationRelationship,
      addEd25519Key: Boolean = false
  ): (PrismDIDOperation.Create, Secp256k1KeyPair, DIDMetadata, DIDData) = {
    val masterKeyPair = Apollo.default.secp256k1.generateKeyPair
    val keyPair = Apollo.default.secp256k1.generateKeyPair

    val basePublicKeys = Seq(
      InternalPublicKey(
        id = KeyId("master-0"),
        purpose = InternalKeyPurpose.Master,
        publicKeyData = PublicKeyData.ECCompressedKeyData(
          crv = EllipticCurve.SECP256K1,
          data = Base64UrlString.fromByteArray(masterKeyPair.publicKey.getEncodedCompressed)
        )
      ),
      PublicKey(
        id = KeyId("key-0"),
        purpose = verificationRelationship,
        publicKeyData = PublicKeyData.ECCompressedKeyData(
          crv = EllipticCurve.SECP256K1,
          data = Base64UrlString.fromByteArray(keyPair.publicKey.getEncodedCompressed)
        )
      )
    )

    val publicKeys = if (addEd25519Key) {
      val keyPair2 = Apollo.default.ed25519.generateKeyPair
      basePublicKeys :+ PublicKey(
        id = KeyId("key-1"),
        purpose = verificationRelationship,
        publicKeyData = PublicKeyData.ECKeyData(
          crv = EllipticCurve.ED25519,
          x = Base64UrlString.fromByteArray(keyPair2.publicKey.getEncoded),
          y = Base64UrlString.fromByteArray(Array.emptyByteArray),
        )
      )
    } else basePublicKeys

    val createOperation = PrismDIDOperation.Create(
      publicKeys = publicKeys,
      services = Nil,
      context = Nil,
    )
    val longFormDid = PrismDID.buildLongFormFromOperation(createOperation)

    val didMetadata = DIDMetadata(
      lastOperationHash = ArraySeq.from(longFormDid.stateHash.toByteArray),
      canonicalId = None,
      deactivated = false,
      created = None,
      updated = None
    )
    val didData = DIDData(
      id = longFormDid.asCanonical,
      publicKeys = createOperation.publicKeys.collect { case pk: PublicKey => pk },
      services = createOperation.services,
      internalKeys = createOperation.publicKeys.collect { case pk: InternalPublicKey => pk },
      context = createOperation.context
    )
    (createOperation, keyPair, didMetadata, didData)
  }

  def createDIDOIDC(
      verificationRelationship: VerificationRelationship
  ): (PrismDIDOperation.Create, Secp256k1KeyPair, DIDMetadata, DIDData) = {
    createDIDInternal(verificationRelationship, addEd25519Key = false)
  }

  def createDID(
      verificationRelationship: VerificationRelationship
  ): (PrismDIDOperation.Create, Secp256k1KeyPair, DIDMetadata, DIDData) = {
    createDIDInternal(verificationRelationship, addEd25519Key = true)
  }

  def resolveDIDExpectation(didMetadata: DIDMetadata, didData: DIDData): Expectation[DIDService] =
    MockDIDService.ResolveDID(
      assertion = Assertion.anything,
      result = Expectation.value(Some(didMetadata, didData))
    )
}
