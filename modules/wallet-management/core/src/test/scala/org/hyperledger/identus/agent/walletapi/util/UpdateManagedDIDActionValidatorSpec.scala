package org.hyperledger.identus.agent.walletapi.util

import org.hyperledger.identus.agent.walletapi.model.{DIDPublicKeyTemplate, UpdateManagedDIDAction}
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.did.core.model.did.{EllipticCurve, VerificationRelationship}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.language.implicitConversions

object UpdateManagedDIDActionValidatorSpec extends ZIOSpecDefault {

  override def spec = suite("UpdateManagedDIDActionValidator")(
    test("reject actions if contain reserved key-id") {
      val reservedAdd = Seq(
        UpdateManagedDIDAction.AddKey(
          DIDPublicKeyTemplate(
            id = ManagedDIDService.DEFAULT_MASTER_KEY_ID,
            purpose = VerificationRelationship.Authentication,
            curve = EllipticCurve.SECP256K1
          )
        )
      )
      val reservedRemoveInternal =
        Seq(UpdateManagedDIDAction.RemoveInternalKey(ManagedDIDService.DEFAULT_MASTER_KEY_ID))
      assert(UpdateManagedDIDActionValidator.validate(reservedAdd))(isLeft) &&
      assert(UpdateManagedDIDActionValidator.validate(reservedRemoveInternal))(isLeft)
    },
    test("reject actions if key usage is not allowed") {
      val makeActions = (keyTemplate: DIDPublicKeyTemplate) => Seq(UpdateManagedDIDAction.AddKey(keyTemplate))
      val actions1 = makeActions(
        DIDPublicKeyTemplate(
          id = "key-1",
          purpose = VerificationRelationship.KeyAgreement,
          curve = EllipticCurve.ED25519
        )
      )
      val actions2 = makeActions(
        DIDPublicKeyTemplate(
          id = "key-1",
          purpose = VerificationRelationship.Authentication,
          curve = EllipticCurve.X25519
        )
      )
      assert(UpdateManagedDIDActionValidator.validate(actions1))(isLeft) &&
      assert(UpdateManagedDIDActionValidator.validate(actions2))(isLeft)
    }
  )

}
