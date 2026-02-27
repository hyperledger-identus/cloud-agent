package org.hyperledger.identus.shared.crypto.jwk

import com.nimbusds.jose.jwk.{Curve, OctetKeyPair}
import com.nimbusds.jose.util.Base64URL
import org.hyperledger.identus.shared.crypto.*
import zio.json.*
import zio.json.ast.Json

import scala.language.implicitConversions
import scala.util.Try

opaque type JWK = Json

object JWK {
  def fromString(json: String): Either[String, JWK] = json.fromJson[Json]
  extension (jwk: JWK) {
    def toJsonString: String = jwk.toJson
    def toJsonAst: Json = jwk
  }

  given Conversion[Ed25519KeyPair, JWK] = keyPair => {
    val x = Base64URL.encode(keyPair.publicKey.getEncoded)
    val d = Base64URL.encode(keyPair.privateKey.getEncoded)
    val octetKey = OctetKeyPair.Builder(Curve.Ed25519, x).d(d).build()
    octetKey.toJSONString().fromJson[Json].toOption.get // .get cannot fail
  }

  given Conversion[X25519KeyPair, JWK] = keyPair => {
    val x = Base64URL.encode(keyPair.publicKey.getEncoded)
    val d = Base64URL.encode(keyPair.privateKey.getEncoded)
    val octetKey = OctetKeyPair.Builder(Curve.X25519, x).d(d).build()
    octetKey.toJSONString().fromJson[Json].toOption.get // .get cannot fail
  }
}

trait FromJWK[K] {
  def from(jwk: JWK): Either[String, K]
}

object FromJWK {
  given FromJWK[Ed25519KeyPair] = jwk => {
    val keyPair = for {
      okp <- Try(OctetKeyPair.parse(jwk.toJsonString))
      d <- Try(okp.getD().decode())
      privateKey <- Apollo.default.ed25519.privateKeyFromEncoded(d)
      publicKey = privateKey.toPublicKey
    } yield Ed25519KeyPair(publicKey, privateKey)
    keyPair.toEither.left.map(_.getMessage())
  }

  given FromJWK[X25519KeyPair] = jwk => {
    val keyPair = for {
      okp <- Try(OctetKeyPair.parse(jwk.toJsonString))
      d <- Try(okp.getD().decode())
      privateKey <- Apollo.default.x25519.privateKeyFromEncoded(d)
      publicKey = privateKey.toPublicKey
    } yield X25519KeyPair(publicKey, privateKey)
    keyPair.toEither.left.map(_.getMessage())
  }
}
