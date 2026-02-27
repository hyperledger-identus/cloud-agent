package org.hyperledger.identus.pollux.sdjwt

import java.util.Base64

// TODO move to apollo
private[sdjwt] object CrytoUtils {

  def privateKeyToPem(encodedPrivateKey: Array[Byte]): String = {
    val base64Encoded = Base64.getEncoder.encodeToString(encodedPrivateKey)
    val pemHeader = "-----BEGIN PRIVATE KEY-----"
    val pemFooter = "-----END PRIVATE KEY-----"
    val pemBody = base64Encoded.grouped(64).mkString("\n") // Split into lines of 64 characters
    pemHeader + "\n" + pemBody + "\n" + pemFooter
  }

  def publicKeyToPem(encodedPublicKey: Array[Byte]): String = {
    val base64Encoded = Base64.getEncoder.encodeToString(encodedPublicKey)
    val pemHeader = "-----BEGIN PUBLIC KEY-----"
    val pemFooter = "-----END PUBLIC KEY-----"
    val pemBody = base64Encoded.grouped(64).mkString("\n") // Split into lines of 64 characters
    pemHeader + "\n" + pemBody + "\n" + pemFooter
  }
}
