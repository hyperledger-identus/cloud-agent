package org.hyperledger.identus.mercury.model

import zio.json.ast.Json
import zio.json.DecoderOps

import java.util.Base64

trait EncryptedMessage { // (private val msg: PackEncryptedResult) {
  def string: String // = msg.getPackedMessage
  def base64: String = Base64.getUrlEncoder.encodeToString(string.getBytes)
  def asJson: Json.Obj = {
    string
      .fromJson[Json]
      .flatMap(o =>
        o.asObject match
          case None        => Left(RuntimeException(s"Expecting Json Object got '$o'"))
          case Some(value) => Right(value)
      )
      .getOrElse(UnexpectedCodeExecutionPath)
  }
}
