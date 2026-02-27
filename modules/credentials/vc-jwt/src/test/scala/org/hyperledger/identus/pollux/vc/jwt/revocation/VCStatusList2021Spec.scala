package org.hyperledger.identus.pollux.vc.jwt.revocation

import org.hyperledger.identus.castor.core.model.did.DID
import org.hyperledger.identus.pollux.vc.jwt.{ES256KSigner, Issuer, JwtCredential}
import org.hyperledger.identus.shared.crypto.KmpSecp256k1KeyOps
import zio.{UIO, ZIO}
import zio.json.ast.JsonCursor
import zio.test.{assertTrue, ZIOSpecDefault}

object VCStatusList2021Spec extends ZIOSpecDefault {

  private val VC_ID = "https://example.com/credentials/status/3"

  private def generateIssuer(): UIO[Issuer] = {

    val keyPair = KmpSecp256k1KeyOps.generateKeyPair
    val javaSKey = keyPair.privateKey.toJavaPrivateKey
    val javaPKey = keyPair.publicKey.toJavaPublicKey

    ZIO.succeed(
      Issuer(
        did = DID.fromString("did:issuer:MDP8AsFhHzhwUvGNuYkX7T").toOption.get,
        signer = ES256KSigner(javaSKey),
        publicKey = javaPKey
      )
    )
  }

  override def spec = suite("VCStatusList2021")(
    test("Should generate status list VC as JSON with embedded proof") {
      for {
        issuer <- generateIssuer()
        bitString <- BitString.getInstance()
        statusList <- VCStatusList2021.build(VC_ID, issuer, bitString)
        json <- statusList.toJsonWithEmbeddedProof
      } yield {
        assertTrue(json.get(JsonCursor.field("proof")).isRight)
      }
    },
    test("Generate VC contains required fields in 'credentialSubject'") {
      for {
        issuer <- generateIssuer()
        bitString <- BitString.getInstance()
        statusList <- VCStatusList2021.build(VC_ID, issuer, bitString)
        encodedJwtVC <- statusList.encoded
        jwtVCPayload <- ZIO.fromTry(JwtCredential.decodeJwt(encodedJwtVC, issuer.publicKey))
        credentialSubjectKeys <- ZIO.fromOption(jwtVCPayload.credentialSubject.asObject.map(_.keys.toSet))
      } yield {
        assertTrue(credentialSubjectKeys.toSet == Set("type", "statusPurpose", "encodedList"))
      }
    },
    test("Generated VC is valid") {
      for {
        issuer <- generateIssuer()
        bitString <- BitString.getInstance()
        statusList <- VCStatusList2021.build(VC_ID, issuer, bitString)
        encodedJwtVC <- statusList.encoded
        valid <- ZIO.succeed(JwtCredential.validateEncodedJwt(encodedJwtVC, issuer.publicKey))
      } yield {
        assertTrue(valid)
      }
    },
    test("Revocation state is preserved during encoding/decoding") {
      for {
        issuer <- generateIssuer()
        bitString <- BitString.getInstance()
        _ <- bitString.setRevokedInPlace(1234, true)
        statusList <- VCStatusList2021.build(VC_ID, issuer, bitString)
        encodedJwtVC <- statusList.encoded
        decodedStatusList <- VCStatusList2021.decode(encodedJwtVC, issuer)
        decodedBS <- decodedStatusList.getBitString
        revokedCount <- decodedBS.revokedCount()
        isRevoked1 <- decodedBS.isRevoked(1233)
        isRevoked2 <- decodedBS.isRevoked(1234)
      } yield {
        assertTrue(revokedCount == 1) &&
        assertTrue(!isRevoked1) &&
        assertTrue(isRevoked2)
      }
    }
  )
}
