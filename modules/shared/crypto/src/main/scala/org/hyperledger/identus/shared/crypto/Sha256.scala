package org.hyperledger.identus.shared.crypto

import java.security.MessageDigest

// Reference: https://github.com/input-output-hk/atala-prism/blob/open-source-node/node/src/main/scala/io/iohk/atala/prism/node/crypto/CryptoUtils.scala
sealed trait Sha256Hash {
  def bytes: Vector[Byte]
  def hexEncoded: String = {
    bytes.map(byte => f"${byte & 0xff}%02x").mkString
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: Sha256Hash => bytes == other.bytes
    case _                 => false
  }

  override def hashCode(): Int = bytes.hashCode()
}

private[crypto] case class Sha256HashImpl(bytes: Vector[Byte]) extends Sha256Hash {
  require(bytes.size == 32)
}

object Sha256Hash {

  def fromBytes(arr: Array[Byte]): Sha256Hash = Sha256HashImpl(arr.toVector)

  def compute(bArray: Array[Byte]): Sha256Hash = {
    Sha256HashImpl(
      MessageDigest
        .getInstance("SHA-256")
        .digest(bArray)
        .toVector
    )
  }

  def fromHex(hexedBytes: String): Sha256Hash = {
    val HEX_STRING_RE = "^[0-9a-fA-F]{64}$".r
    if (HEX_STRING_RE.matches(hexedBytes)) Sha256HashImpl(hexToBytes(hexedBytes))
    else
      throw new IllegalArgumentException(
        "The given hex string doesn't correspond to a valid SHA-256 hash encoded as string"
      )
  }

  private def hexToBytes(hex: String): Vector[Byte] = {
    val HEX_ARRAY = "0123456789abcdef".toCharArray
    for {
      pair <- hex.grouped(2).toVector
      firstIndex = HEX_ARRAY.indexOf(pair(0))
      secondIndex = HEX_ARRAY.indexOf(pair(1))
      octet = firstIndex << 4 | secondIndex
    } yield octet.toByte
  }
}
