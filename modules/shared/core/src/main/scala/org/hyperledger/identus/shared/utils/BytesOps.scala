package org.hyperledger.identus.shared.utils

import java.nio.charset.StandardCharsets

object BytesOps {
  private val HexArray = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

  // Converts hex string to bytes representation
  def hexToBytes(hexEncoded: String): Array[Byte] = {
    require(hexEncoded.length % 2 == 0, "Hex length needs to be even")
    hexEncoded.grouped(2).toVector.map(hexToByte).toArray
  }

  // Converts bytes to hex string representation
  def bytesToHex(bytes: Iterable[Byte]): String = {
    val hexChars = new Array[Byte](bytes.size * 2)
    for ((byte, i) <- bytes.zipWithIndex) {
      val v = byte & 0xff
      hexChars(i * 2) = HexArray(v >>> 4)
      hexChars(i * 2 + 1) = HexArray(v & 0x0f)
    }
    new String(hexChars, StandardCharsets.UTF_8)
  }

  private def hexToByte(h: String): Byte = {
    Integer.parseInt(h, 16).toByte
  }
}
