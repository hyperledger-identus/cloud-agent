package org.hyperledger.identus.agent.walletapi.util

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.agent.walletapi.model.error.{CreateManagedDIDError, UpdateManagedDIDError}
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.shared.crypto.{
  Apollo,
  Ed25519PublicKey,
  Secp256k1KeyPair,
  Secp256k1PublicKey,
  X25519PublicKey
}
import org.hyperledger.identus.shared.models.{Base64UrlString, KeyId}
import zio.*

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

private[util] final case class KeyDerivationOutcome[PK](
    publicKey: PK,
    path: ManagedDIDHdKeyPath,
    nextCounter: HdKeyIndexCounter
)

private[util] final case class KeyGenerationOutcome(
    publicKey: PublicKey,
    key: ManagedDIDRandKeyPair
)

class OperationFactory(apollo: Apollo) {

  /** Generates a key pair and a public key from a DID template
    *
    * @param masterKeyId
    *   The key id of the master key
    * @param seed
    *   The seed to use for the key generation
    * @param didTemplate
    *   The DID template
    * @param didIndex
    *   The index of the DID to be used for the key derivation
    */
  def makeCreateOperation(
      masterKeyId: KeyId,
      seed: Array[Byte]
  )(
      didIndex: Int,
      didTemplate: ManagedDIDTemplate
  ): IO[CreateManagedDIDError, (PrismDIDOperation.Create, CreateDIDKey)] = {
    val initKeysWithCounter = (Vector.empty[KeyDerivationOutcome[PublicKey]], HdKeyIndexCounter.zero(didIndex))
    val (hdKeysTemplate, randKeysTemplate) = didTemplate.publicKeys.partition(_.curve == EllipticCurve.SECP256K1)
    for {
      randKeys <- ZIO.foreach(randKeysTemplate)(generatePublicKey)
      hdKeysWithCounter <- ZIO.foldLeft(hdKeysTemplate)(initKeysWithCounter) { case ((keys, keyCounter), template) =>
        derivePublicKey(seed)(template, keyCounter)
          .map(outcome => (keys :+ outcome, outcome.nextCounter))
      }
      masterKeyOutcome <- deriveInternalPublicKey(seed)(
        masterKeyId,
        InternalKeyPurpose.Master,
        hdKeysWithCounter._2
      )
      derivedInternalKeysWithCounter <- ZIO.foldLeft(didTemplate.internalKeys)(
        (Vector.empty[KeyDerivationOutcome[InternalPublicKey]], masterKeyOutcome.nextCounter)
      ) { case ((keys, keyCounter), template) =>
        deriveInternalPublicKey(seed)(KeyId(template.id), template.purpose, keyCounter)
          .map(outcome => (keys :+ outcome, outcome.nextCounter))
      }
      (derivedInternalKeys, _) = derivedInternalKeysWithCounter
      operation = PrismDIDOperation.Create(
        publicKeys = hdKeysWithCounter._1.map(_._1) ++
          randKeys.map(_.publicKey) ++
          Seq(masterKeyOutcome.publicKey) ++
          derivedInternalKeys.map(_.publicKey),
        services = didTemplate.services,
        context = didTemplate.contexts
      )
      keys = CreateDIDKey(
        hdKeys = hdKeysWithCounter._1.map(i => i.publicKey.id.value -> i.path).toMap ++
          Map(masterKeyOutcome.publicKey.id.value -> masterKeyOutcome.path) ++
          derivedInternalKeys.map(i => i.publicKey.id.value -> i.path).toMap,
        randKeys = randKeys.map(i => i.publicKey.id.value -> i.key).toMap,
      )
    } yield operation -> keys
  }

  def makeUpdateOperation(seed: Array[Byte])(
      did: CanonicalPrismDID,
      previousOperationHash: Array[Byte],
      actions: Seq[UpdateManagedDIDAction],
      fromKeyCounter: HdKeyIndexCounter
  ): IO[UpdateManagedDIDError, (PrismDIDOperation.Update, UpdateDIDKey)] = {
    val initKeysWithCounter =
      (
        Vector.empty[(UpdateManagedDIDAction, Option[(PublicKey, ManagedDIDHdKeyPath | ManagedDIDRandKeyPair)])],
        fromKeyCounter
      )
    val actionsWithKeyMaterial = ZIO.foldLeft(actions)(initKeysWithCounter) { case ((acc, keyCounter), action) =>
      val outcome: UIO[Option[KeyGenerationOutcome | KeyDerivationOutcome[PublicKey]]] = action match {
        case UpdateManagedDIDAction.AddKey(template) =>
          if template.curve == EllipticCurve.SECP256K1
          then derivePublicKey(seed)(template, keyCounter).asSome
          else generatePublicKey(template).asSome
        case _ => ZIO.none
      }
      outcome.map {
        case Some(outcome: KeyDerivationOutcome[PublicKey]) =>
          (acc :+ (action -> Some((outcome.publicKey, outcome.path))), outcome.nextCounter)
        case Some(outcome: KeyGenerationOutcome) =>
          (acc :+ (action -> Some((outcome.publicKey, outcome.key))), keyCounter)
        case None =>
          (acc :+ (action -> None), keyCounter)
      }
    }

    for {
      actionsWithKeyMaterial <- actionsWithKeyMaterial
      (actionWithKey, keyCounter) = actionsWithKeyMaterial
      transformedActions <- ZIO.foreach(actionWithKey) { case (action, keyMaterial) =>
        transformUpdateAction(action, keyMaterial.map(_._1))
      }
      keys = actionWithKey.collect { case (UpdateManagedDIDAction.AddKey(_), Some(secret)) => secret }
      (randKeys, hdKeys) = keys.partitionMap {
        case (pk, hdPath: ManagedDIDHdKeyPath)    => Right(pk.id.value -> hdPath)
        case (pk, keyPair: ManagedDIDRandKeyPair) => Left(pk.id.value -> keyPair)
      }
      operation = PrismDIDOperation.Update(
        did = did,
        previousOperationHash = ArraySeq.from(previousOperationHash),
        actions = transformedActions
      )
      updateKey = UpdateDIDKey(
        // NOTE: Prism DID specification currently doesn't allow updating existing key with the same key-id.
        // Duplicated key-id in AddKey action can be ignored as the specification will reject the whole update operation.
        // If the specification supports updating existing key, the key that will be stored in the wallet
        // MUST be aligned with the spec (e.g. keep first / keep last in the action list)
        hdKeys = hdKeys.toMap,
        randKeys = randKeys.toMap,
        counter = keyCounter
      )
    } yield operation -> updateKey
  }

  private def transformUpdateAction(
      updateAction: UpdateManagedDIDAction,
      publicKey: Option[PublicKey]
  ): UIO[UpdateDIDAction] = {
    updateAction match {
      case UpdateManagedDIDAction.AddKey(_) =>
        publicKey match {
          case Some(publicKey) => ZIO.succeed(UpdateDIDAction.AddKey(publicKey))
          case None            =>
            // should be impossible otherwise it's a defect
            ZIO.dieMessage("addKey update DID action must have a generated a key-pair")
        }
      case UpdateManagedDIDAction.RemoveKey(id)        => ZIO.succeed(UpdateDIDAction.RemoveKey(id))
      case UpdateManagedDIDAction.AddService(service)  => ZIO.succeed(UpdateDIDAction.AddService(service))
      case UpdateManagedDIDAction.RemoveService(id)    => ZIO.succeed(UpdateDIDAction.RemoveService(id))
      case UpdateManagedDIDAction.UpdateService(patch) =>
        ZIO.succeed(UpdateDIDAction.UpdateService(patch.id, patch.serviceType, patch.serviceEndpoints))
      case UpdateManagedDIDAction.PatchContext(context) => ZIO.succeed(UpdateDIDAction.PatchContext(context))
    }
  }

  private def generatePublicKey(template: DIDPublicKeyTemplate): UIO[KeyGenerationOutcome] = {
    ZIO.attempt {
      val (publicKeyData, keyPair) = template.curve match {
        case EllipticCurve.SECP256K1 => throw Exception("secp256k1 key must be derived, not randomly generated")
        case EllipticCurve.ED25519   =>
          val kp = apollo.ed25519.generateKeyPair
          toPublicKeyData(kp.publicKey) -> kp
        case EllipticCurve.X25519 =>
          val kp = apollo.x25519.generateKeyPair
          toPublicKeyData(kp.publicKey) -> kp
      }
      KeyGenerationOutcome(
        publicKey = PublicKey(KeyId(template.id), template.purpose, publicKeyData),
        key = ManagedDIDRandKeyPair(template.purpose, keyPair)
      )
    }.orDie
  }

  private def derivePublicKey(seed: Array[Byte])(
      template: DIDPublicKeyTemplate,
      keyCounter: HdKeyIndexCounter
  ): UIO[KeyDerivationOutcome[PublicKey]] = {
    val purpose = template.purpose
    val keyPath = keyCounter.path(purpose)
    for {
      keyPair <- deriveSecp256k1KeyPair(seed, keyPath)
      publicKey = PublicKey(KeyId(template.id), purpose, toPublicKeyData(keyPair.publicKey))
    } yield KeyDerivationOutcome(publicKey, keyPath, keyCounter.next(purpose))
  }

  private def deriveInternalPublicKey(seed: Array[Byte])(
      id: KeyId,
      purpose: InternalKeyPurpose,
      keyCounter: HdKeyIndexCounter
  ): UIO[KeyDerivationOutcome[InternalPublicKey]] = {
    val keyPath = keyCounter.path(purpose)
    for {
      keyPair <- deriveSecp256k1KeyPair(seed, keyPath)
      internalPublicKey = InternalPublicKey(id, purpose, toPublicKeyData(keyPair.publicKey))
    } yield KeyDerivationOutcome(internalPublicKey, keyPath, keyCounter.next(purpose))
  }

  private def deriveSecp256k1KeyPair(seed: Array[Byte], path: ManagedDIDHdKeyPath): UIO[Secp256k1KeyPair] =
    apollo.secp256k1.deriveKeyPair(seed)(path.derivationPath*)

  private def toPublicKeyData(publicKey: Secp256k1PublicKey | Ed25519PublicKey | X25519PublicKey): PublicKeyData =
    publicKey match {
      case pk: Secp256k1PublicKey =>
        PublicKeyData.ECCompressedKeyData(
          crv = EllipticCurve.SECP256K1,
          data = Base64UrlString.fromByteArray(pk.getEncodedCompressed),
        )
      case pk: Ed25519PublicKey =>
        PublicKeyData.ECCompressedKeyData(
          crv = EllipticCurve.ED25519,
          data = Base64UrlString.fromByteArray(publicKey.getEncoded),
        )
      case pk: X25519PublicKey =>
        PublicKeyData.ECCompressedKeyData(
          crv = EllipticCurve.X25519,
          data = Base64UrlString.fromByteArray(publicKey.getEncoded),
        )
    }

}
