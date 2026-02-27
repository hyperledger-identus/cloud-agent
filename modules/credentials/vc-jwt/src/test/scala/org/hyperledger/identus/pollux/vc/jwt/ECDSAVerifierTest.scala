package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.Curve
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatest.matchers.should.Matchers

class ECDSAVerifierTest extends AnyFunSuite with Matchers {

  test("toECDSAVerifier should use BouncyCastleProviderSingleton") {
    val ecKey = ECKeyGenerator(Curve.SECP256K1).provider(BouncyCastleProviderSingleton.getInstance()).generate()
    val verifier = JWTVerification.toECDSAVerifier(ecKey.toPublicKey)
    val provider = verifier.getJCAContext.getProvider
    provider mustBe a[BouncyCastleProvider]
  }
}
