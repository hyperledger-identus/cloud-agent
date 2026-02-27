package org.hyperledger.identus.pollux.sdjwt

import org.bouncycastle.crypto.params.{Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters}
import org.bouncycastle.crypto.util.{PrivateKeyInfoFactory, SubjectPublicKeyInfoFactory}
import org.hyperledger.identus.shared.crypto.*
import sdjwtwrapper.*

opaque type IssuerPublicKey = String
object IssuerPublicKey {
  def fromPem(keyPem: String): IssuerPublicKey = keyPem

  def apply(key: Ed25519PublicKey): IssuerPublicKey = {
    val publicKeyParameters = new Ed25519PublicKeyParameters(key.getEncoded, 0)
    val publicKeyInfo = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKeyParameters)
    val pkcs8Bytes = publicKeyInfo.getEncoded()
    val data = CrytoUtils.publicKeyToPem(pkcs8Bytes)
    fromPem(data)
  }
  extension (pemKey: IssuerPublicKey)
    def value: String = pemKey
    def pem: String = pemKey
}

// Note about signAlg supported in json web token 9.2.0
// HS256 // HMAC using SHA-256
// HS384 // HMAC using SHA-384
// HS512 // HMAC using SHA-512
// ES256 // ECDSA using SHA-256
// ES384 // ECDSA using SHA-384
// RS256 // RSASSA-PKCS1-v1_5 using SHA-256
// RS384 // RSASSA-PKCS1-v1_5 using SHA-384
// RS512 // RSASSA-PKCS1-v1_5 using SHA-512
// PS256 // RSASSA-PSS using SHA-256
// PS384 // RSASSA-PSS using SHA-384
// PS512 // RSASSA-PSS using SHA-512
// EdDSA // Edwards-curve Digital Signature Algorithm (EdDSA)

case class IssuerPrivateKey(value: EncodingKeyValue, signAlg: String)
object IssuerPrivateKey {

  def fromEcPem(keyPem: String): IssuerPrivateKey =
    IssuerPrivateKey(EncodingKeyValue.Companion.fromEcPem(keyPem), "ES256")
  def fromEdPem(keyPem: String): IssuerPrivateKey =
    IssuerPrivateKey(EncodingKeyValue.Companion.fromEdPem(keyPem), "EdDSA")
  def apply(key: Ed25519PrivateKey): IssuerPrivateKey = {
    val privateKeyParameters = new Ed25519PrivateKeyParameters(key.getEncoded, 0)
    val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters)
    val pkcs8Bytes = privateKeyInfo.getEncoded()
    val data = CrytoUtils.privateKeyToPem(pkcs8Bytes)
    fromEdPem(data)
  }
  // def apply(key: X25519PrivateKey): IssuerPrivateKey = {
  //   val privateKeyParameters = new X25519PrivateKeyParameters(key.getEncoded, 0)
  //   val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters)
  //   val pkcs8Bytes = privateKeyInfo.getEncoded()
  //   EncodingKeyValue.Companion.fromEdPem(Utils.privateKeyToPem(pkcs8Bytes))
  // }
}

opaque type HolderPublicKey = String
object HolderPublicKey {
  def fromJWT(jwtString: String): HolderPublicKey = jwtString
  def apply(key: Ed25519PublicKey) = {
    val x = java.util.Base64.getUrlEncoder.withoutPadding().encodeToString(key.getEncoded)
    HolderPublicKey.fromJWT(s"""{"kty":"OKP","crv":"Ed25519","x":"$x"}""")
  }
  extension (jwtString: HolderPublicKey)
    def value: String = jwtString
    def jwt: JwkValue = JwkValue.apply(jwtString)
}

case class HolderPrivateKey(value: EncodingKeyValue, signAlg: String)
object HolderPrivateKey {

  def fromEcPem(keyPem: String): HolderPrivateKey =
    HolderPrivateKey(EncodingKeyValue.Companion.fromEcPem(keyPem), "ES256")
  def fromEdPem(keyPem: String): HolderPrivateKey =
    HolderPrivateKey(EncodingKeyValue.Companion.fromEdPem(keyPem), "EdDSA")
  def apply(key: Ed25519PrivateKey): HolderPrivateKey = {
    val privateKeyParameters = new Ed25519PrivateKeyParameters(key.getEncoded, 0)
    val privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKeyParameters)
    val pkcs8Bytes = privateKeyInfo.getEncoded()
    val data = CrytoUtils.privateKeyToPem(pkcs8Bytes)
    fromEdPem(data)
  }
}
