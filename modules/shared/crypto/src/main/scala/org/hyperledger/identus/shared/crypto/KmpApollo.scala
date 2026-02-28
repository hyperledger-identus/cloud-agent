package org.hyperledger.identus.shared.crypto

import org.hyperledger.identus.apollo.derivation
import org.hyperledger.identus.apollo.derivation.MnemonicHelper
import org.hyperledger.identus.apollo.secp256k1.Secp256k1Lib
import org.hyperledger.identus.apollo.securerandom.SecureRandom
import org.hyperledger.identus.apollo.utils.{
  KMMECSecp256k1PrivateKey,
  KMMECSecp256k1PublicKey,
  KMMEdKeyPair,
  KMMEdPrivateKey,
  KMMEdPublicKey,
  KMMX25519KeyPair,
  KMMX25519PrivateKey,
  KMMX25519PublicKey
}
import zio.*

import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

final case class KmpSecp256k1PublicKey(publicKey: KMMECSecp256k1PublicKey) extends Secp256k1PublicKey {

  override def getECPoint: ECPoint = {
    val point = publicKey.getCurvePoint()
    ECPoint(point.getX(), point.getY())
  }

  override def getEncoded: Array[Byte] = publicKey.getCompressed()

  override def getEncodedCompressed: Array[Byte] = getEncoded

  override def getEncodedUncompressed: Array[Byte] =
    KmpSecp256k1KeyOps.secpLib.uncompressPublicKey(getEncodedCompressed)

  override def verify(data: Array[Byte], signature: Array[Byte]): Try[Unit] =
    Try(publicKey.verify(signature, data))
      .flatMap(isValid => if (isValid) Success(()) else Failure(Exception("The signature verification does not match")))
}

final case class KmpSecp256k1PrivateKey(privateKey: KMMECSecp256k1PrivateKey) extends Secp256k1PrivateKey {
  override def sign(data: Array[Byte]): Array[Byte] = privateKey.sign(data)

  override def toPublicKey: Secp256k1PublicKey = KmpSecp256k1PublicKey(privateKey.getPublicKey())

  override def getEncoded: Array[Byte] = privateKey.getEncoded()
}

object KmpSecp256k1KeyOps extends Secp256k1KeyOps {
  private[crypto] val secpLib: Secp256k1Lib = Secp256k1Lib()
  private[crypto] val secureRandom: SecureRandom = SecureRandom()

  override def publicKeyFromEncoded(bytes: Array[Byte]): Try[Secp256k1PublicKey] =
    Try {
      val publicKey = KMMECSecp256k1PublicKey.Companion.secp256k1FromBytes(bytes)
      val point = publicKey.getCurvePoint()
      val isOnCurve = KMMECSecp256k1PublicKey.Companion.isPointOnSecp256k1Curve(point)
      if (isOnCurve) KmpSecp256k1PublicKey(publicKey)
      else throw new Exception("The public key is not on the secp256k1 curve")
    }

  override def publicKeyFromCoordinate(x: Array[Byte], y: Array[Byte]): Try[Secp256k1PublicKey] =
    Try {
      val pk = KMMECSecp256k1PublicKey.Companion.secp256k1FromByteCoordinates(x, y)
      val point = pk.getCurvePoint()
      val isOnCurve = KMMECSecp256k1PublicKey.Companion.isPointOnSecp256k1Curve(point)
      if (isOnCurve) KmpSecp256k1PublicKey(pk)
      else throw Exception("The point is not on the secp256k1 curve")
    }

  override def privateKeyFromEncoded(bytes: Array[Byte]): Try[Secp256k1PrivateKey] =
    Try(KMMECSecp256k1PrivateKey.Companion.secp256k1FromByteArray(bytes)).map(KmpSecp256k1PrivateKey(_))

  override def generateKeyPair: Secp256k1KeyPair = {
    val randBytes = secureRandom.nextBytes(32)
    val privateKey = KMMECSecp256k1PrivateKey(randBytes)
    val publicKey = privateKey.getPublicKey
    Secp256k1KeyPair(
      KmpSecp256k1PublicKey(publicKey),
      KmpSecp256k1PrivateKey(privateKey)
    )
  }

  def randomBip32Seed: UIO[(Array[Byte], Seq[String])] =
    ZIO.attemptBlocking {
      val words = MnemonicHelper.Companion.createRandomMnemonics()
      val seed = MnemonicHelper.Companion.createSeed(words, "")
      seed -> words.asScala.toList
    }.orDie

  def deriveKeyPair(seed: Array[Byte])(path: DerivationPath*): UIO[Secp256k1KeyPair] =
    ZIO.attemptBlocking {
      val pathStr = path
        .foldLeft(derivation.DerivationPath.empty()) { case (path, p) =>
          p match {
            case DerivationPath.Hardened(i) => path.derive(derivation.DerivationAxis.hardened(i))
            case DerivationPath.Normal(i)   => path.derive(derivation.DerivationAxis.normal(i))
          }
        }
        .toString()
      val hdKey = derivation.HDKey(seed, 0, 0).derive(pathStr)
      val privateKey = hdKey.getKMMSecp256k1PrivateKey()
      val publicKey = privateKey.getPublicKey()

      Secp256k1KeyPair(
        KmpSecp256k1PublicKey(publicKey),
        KmpSecp256k1PrivateKey(privateKey)
      )
    }.orDie

}

final case class KmpEd25519PublicKey(publicKey: KMMEdPublicKey) extends Ed25519PublicKey {

  override def getEncoded: Array[Byte] = publicKey.getRaw()
  override def verify(data: Array[Byte], signature: Array[Byte]): Try[Unit] =
    Try(publicKey.verify(data, signature))
      .flatMap(isValid => if (isValid) Success(()) else Failure(Exception("The signature verification does not match")))
}

final case class KmpEd25519PrivateKey(privateKey: KMMEdPrivateKey) extends Ed25519PrivateKey {
  override def getEncoded: Array[Byte] = privateKey.getRaw()
  override def sign(data: Array[Byte]): Array[Byte] = privateKey.sign(data)
  override def toPublicKey: Ed25519PublicKey = KmpEd25519PublicKey(privateKey.publicKey())
}

object KmpEd25519KeyOps extends Ed25519KeyOps {

  override def publicKeyFromEncoded(bytes: Array[Byte]): Try[Ed25519PublicKey] =
    Try {
      if bytes.length != 32 then throw Exception("Invalid public key length")
      KmpEd25519PublicKey(KMMEdPublicKey(bytes))
    }

  override def privateKeyFromEncoded(bytes: Array[Byte]): Try[Ed25519PrivateKey] =
    Try {
      if bytes.length != 32 then throw Exception("Invalid private key length")
      KmpEd25519PrivateKey(KMMEdPrivateKey(bytes))
    }

  override def generateKeyPair: Ed25519KeyPair = {
    val keyPair = KMMEdKeyPair.Companion.generateKeyPair()
    Ed25519KeyPair(
      KmpEd25519PublicKey(keyPair.getPublicKey()),
      KmpEd25519PrivateKey(keyPair.getPrivateKey())
    )
  }

}

final case class KmpX25519PublicKey(publicKey: KMMX25519PublicKey) extends X25519PublicKey {
  override def getEncoded: Array[Byte] = publicKey.getRaw()
}

final case class KmpX25519PrivateKey(privateKey: KMMX25519PrivateKey) extends X25519PrivateKey {
  override def getEncoded: Array[Byte] = privateKey.getRaw()
  override def toPublicKey: X25519PublicKey = KmpX25519PublicKey(privateKey.publicKey())
}

object KmpX25519KeyOps extends X25519KeyOps {
  override def publicKeyFromEncoded(bytes: Array[Byte]): Try[X25519PublicKey] =
    Try {
      if bytes.length != 32 then throw Exception("Invalid public key length")
      KmpX25519PublicKey(KMMX25519PublicKey(bytes))
    }

  override def privateKeyFromEncoded(bytes: Array[Byte]): Try[X25519PrivateKey] =
    Try {
      if bytes.length != 32 then throw Exception("Invalid private key length")
      KmpX25519PrivateKey(KMMX25519PrivateKey(bytes))
    }

  override def generateKeyPair: X25519KeyPair = {
    val keyPair = KMMX25519KeyPair.Companion.generateKeyPair()
    X25519KeyPair(
      KmpX25519PublicKey(keyPair.getPublicKey()),
      KmpX25519PrivateKey(keyPair.getPrivateKey())
    )
  }
}
