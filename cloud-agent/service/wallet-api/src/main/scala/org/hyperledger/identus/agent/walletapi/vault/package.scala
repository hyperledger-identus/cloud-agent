package org.hyperledger.identus.agent.walletapi

import com.nimbusds.jose.jwk.OctetKeyPair
import org.hyperledger.identus.agent.walletapi.model.WalletSeed
import org.hyperledger.identus.shared.crypto.jwk.JWK
import org.hyperledger.identus.shared.models.{HexString, WalletId}
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.Json.*

import scala.util.{Failure, Try}

package object vault {
  val SEMANTIC_PATH_METADATA_KEY: String = "semanticPath"

  private[vault] def walletBasePath(walletId: WalletId): String = {
    s"secret/${walletId.toUUID}"
  }

  trait KVCodec[T] {
    def encode(value: T): Map[String, String]
    def decode(kv: Map[String, String]): Try[T]
  }

  given KVCodec[WalletSeed] = new {
    override def encode(value: WalletSeed): Map[String, String] = {
      val bytes = value.toByteArray
      Map(
        "value" -> HexString.fromByteArray(bytes).toString
      )
    }

    override def decode(kv: Map[String, String]): Try[WalletSeed] = {
      kv.get("value") match {
        case None              => Failure(Exception("A property 'value' is missing from vault KV data"))
        case Some(encodedSeed) =>
          HexString
            .fromString(encodedSeed)
            .map(_.toByteArray)
            .flatMap(bytes => WalletSeed.fromByteArray(bytes).left.map(Exception(_)).toTry)
      }
    }
  }

  given KVCodec[OctetKeyPair] = new {
    override def encode(value: OctetKeyPair): Map[String, String] =
      Map("value" -> value.toJSONString())

    override def decode(kv: Map[String, String]): Try[OctetKeyPair] =
      for {
        jwk <- kv.get("value").toRight(Exception("A property 'value' is missing from vault KV data")).toTry
        keyPair <- Try(OctetKeyPair.parse(jwk))
      } yield keyPair
  }

  given KVCodec[Json] = new {
    override def encode(value: Json): Map[String, String] =
      Map("value" -> value.toJson)

    override def decode(kv: Map[String, String]): Try[Json] =
      for {
        json <- kv.get("value").toRight(Exception("A property 'value' is missing from vault KV data")).toTry
        keyPair <- json
          .fromJson[Json]
          .left
          .map(s => Exception(s"Fail to parse JSON from string: $s"))
          .toTry
      } yield keyPair
  }

  given KVCodec[JWK] = new {
    override def encode(value: JWK): Map[String, String] =
      Map("value" -> value.toJsonString)

    override def decode(kv: Map[String, String]): Try[JWK] =
      for {
        json <- kv.get("value").toRight(Exception("A property 'value' is missing from vault KV data")).toTry
        jwk <- JWK.fromString(json).left.map(Exception(_)).toTry
      } yield jwk
  }
}
