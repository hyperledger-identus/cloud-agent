package org.hyperledger.identus.shared.crypto

import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.Curve

object JwtSignerImplicits {
  extension (secp256k1PrivateKey: Secp256k1PrivateKey) {
    def asJwtSigner: JWSSigner = {
      val ecdsaSigner = ECDSASigner(secp256k1PrivateKey.toJavaPrivateKey, Curve.SECP256K1)
      ecdsaSigner.getJCAContext.setProvider(BouncyCastleProviderSingleton.getInstance)
      ecdsaSigner
    }
  }
}
