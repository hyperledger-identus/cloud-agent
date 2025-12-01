package org.hyperledger.identus.agent.walletapi.model

import org.hyperledger.identus.castor.core.model.did.{EllipticCurve, InternalKeyPurpose, VerificationRelationship}
import org.hyperledger.identus.shared.crypto.{DerivationPath, Ed25519KeyPair, Sha256Hash, X25519KeyPair}

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

opaque type WalletSeed = ArraySeq[Byte]

object WalletSeed {
  extension (s: WalletSeed) {
    final def toString(): String = "<REDACTED>"
    def toByteArray: Array[Byte] = s.toArray
    def sha256Digest: Array[Byte] = Sha256Hash.compute(toByteArray).bytes.toArray
  }

  def fromByteArray(bytes: Array[Byte]): Either[String, WalletSeed] = {
    if (bytes.length != 64) Left(s"The bytes must be 64-bytes (got ${bytes.length} bytes)")
    else Right(ArraySeq.from(bytes))
  }
}

enum KeyManagementMode {
  case HD extends KeyManagementMode
  case RANDOM extends KeyManagementMode
}

final case class VerificationRelationshipCounter(
    authentication: Int,
    assertionMethod: Int,
    keyAgreement: Int,
    capabilityInvocation: Int,
    capabilityDelegation: Int,
) {
  def next(keyUsage: VerificationRelationship): VerificationRelationshipCounter = {
    keyUsage match {
      case VerificationRelationship.Authentication       => copy(authentication = authentication + 1)
      case VerificationRelationship.AssertionMethod      => copy(assertionMethod = assertionMethod + 1)
      case VerificationRelationship.KeyAgreement         => copy(keyAgreement = keyAgreement + 1)
      case VerificationRelationship.CapabilityInvocation => copy(capabilityInvocation = capabilityInvocation + 1)
      case VerificationRelationship.CapabilityDelegation => copy(capabilityDelegation = capabilityDelegation + 1)
    }
  }
}

object VerificationRelationshipCounter {
  def zero: VerificationRelationshipCounter = VerificationRelationshipCounter(0, 0, 0, 0, 0)
}

final case class InternalKeyCounter(
    master: Int,
    revocation: Int,
    vdrSigning: Int
) {
  def next(keyUsage: InternalKeyPurpose): InternalKeyCounter = {
    keyUsage match {
      case InternalKeyPurpose.Master     => copy(master = master + 1)
      case InternalKeyPurpose.Revocation => copy(revocation = revocation + 1)
      case InternalKeyPurpose.VDRSigning => copy(vdrSigning = vdrSigning + 1)
    }
  }
}

object InternalKeyCounter {
  def zero: InternalKeyCounter = InternalKeyCounter(0, 0, 0)
}

/** Key counter of a single DID */
final case class HdKeyIndexCounter(
    didIndex: Int,
    verificationRelationship: VerificationRelationshipCounter,
    internalKey: InternalKeyCounter
) {
  def next(keyUsage: VerificationRelationship | InternalKeyPurpose): HdKeyIndexCounter = {
    keyUsage match {
      case i: VerificationRelationship => copy(verificationRelationship = verificationRelationship.next(i))
      case i: InternalKeyPurpose       => copy(internalKey = internalKey.next(i))
    }
  }

  def path(keyUsage: VerificationRelationship | InternalKeyPurpose): ManagedDIDHdKeyPath = {
    val keyIndex = keyUsage match {
      case VerificationRelationship.AssertionMethod      => verificationRelationship.assertionMethod
      case VerificationRelationship.KeyAgreement         => verificationRelationship.keyAgreement
      case VerificationRelationship.CapabilityInvocation => verificationRelationship.capabilityInvocation
      case VerificationRelationship.CapabilityDelegation => verificationRelationship.capabilityDelegation
      case VerificationRelationship.Authentication       => verificationRelationship.authentication
      case InternalKeyPurpose.Master                     => internalKey.master
      case InternalKeyPurpose.Revocation                 => internalKey.revocation
      case InternalKeyPurpose.VDRSigning                 => internalKey.vdrSigning
    }
    ManagedDIDHdKeyPath(didIndex, keyUsage, keyIndex)
  }
}

object HdKeyIndexCounter {
  def zero(didIndex: Int): HdKeyIndexCounter =
    HdKeyIndexCounter(didIndex, VerificationRelationshipCounter.zero, InternalKeyCounter.zero)
}

sealed trait ManagedDIDKeyMeta

object ManagedDIDKeyMeta {
  final case class HD(path: ManagedDIDHdKeyPath) extends ManagedDIDKeyMeta
  final case class Rand(meta: ManagedDIDRandKeyMeta) extends ManagedDIDKeyMeta
}

final case class ManagedDIDHdKeyPath(
    didIndex: Int,
    keyUsage: VerificationRelationship | InternalKeyPurpose,
    keyIndex: Int
) {

  private val WALLET_PURPOSE: Int = 0x1d
  private val PRISM_DID_METHOD_PATH: Int = 0x1d

  def curve: EllipticCurve = EllipticCurve.SECP256K1

  def keyMode: KeyManagementMode = KeyManagementMode.HD

  def derivationPath: Seq[DerivationPath] =
    Seq(
      DerivationPath.Hardened(WALLET_PURPOSE),
      DerivationPath.Hardened(PRISM_DID_METHOD_PATH),
      DerivationPath.Hardened(didIndex),
      DerivationPath.Hardened(keyUsageIndex),
      DerivationPath.Hardened(keyIndex)
    )

  def keyUsageIndex: Int = mapKeyUsageIndex(keyUsage)

  private def mapKeyUsageIndex(keyUsage: VerificationRelationship | InternalKeyPurpose): Int = {
    keyUsage match {
      case InternalKeyPurpose.Master                     => 1
      case VerificationRelationship.AssertionMethod      => 2
      case VerificationRelationship.KeyAgreement         => 3
      case VerificationRelationship.Authentication       => 4
      case InternalKeyPurpose.Revocation                 => 5
      case VerificationRelationship.CapabilityInvocation => 6
      case VerificationRelationship.CapabilityDelegation => 7
      case InternalKeyPurpose.VDRSigning                 => 8
    }
  }
}

final case class ManagedDIDRandKeyPair(
    keyUsage: VerificationRelationship | InternalKeyPurpose,
    keyPair: Ed25519KeyPair | X25519KeyPair
) {
  def meta: ManagedDIDRandKeyMeta = {
    val curve = keyPair match {
      case _: Ed25519KeyPair => EllipticCurve.ED25519
      case _: X25519KeyPair  => EllipticCurve.X25519
    }
    ManagedDIDRandKeyMeta(keyUsage, curve)
  }
}

final case class ManagedDIDRandKeyMeta(
    keyUsage: VerificationRelationship | InternalKeyPurpose,
    curve: EllipticCurve
) {
  def keyMode: KeyManagementMode = KeyManagementMode.RANDOM
}

private[walletapi] final case class CreateDIDKey(
    hdKeys: Map[String, ManagedDIDHdKeyPath], // TODO use type KeyId
    randKeys: Map[String, ManagedDIDRandKeyPair] // TODO use type KeyId
) {
  def randKeyMeta: Map[String, ManagedDIDRandKeyMeta] = randKeys.map { case (k, v) => k -> v.meta }
}

private[walletapi] final case class UpdateDIDKey(
    hdKeys: Map[String, ManagedDIDHdKeyPath], // TODO use type KeyId
    randKeys: Map[String, ManagedDIDRandKeyPair], // TODO use type KeyId
    counter: HdKeyIndexCounter
) {
  def randKeyMeta: Map[String, ManagedDIDRandKeyMeta] = randKeys.map { case (k, v) => k -> v.meta }
}
