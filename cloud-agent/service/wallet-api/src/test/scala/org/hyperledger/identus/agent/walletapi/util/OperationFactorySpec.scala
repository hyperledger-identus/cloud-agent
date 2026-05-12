package org.hyperledger.identus.agent.walletapi.util

import org.hyperledger.identus.agent.walletapi.model.*
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.shared.crypto.{ApolloSpecHelper, Ed25519KeyPair, X25519KeyPair}
import org.hyperledger.identus.shared.models.{HexString, KeyId}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.language.implicitConversions

object OperationFactorySpec extends ZIOSpecDefault, ApolloSpecHelper {

  private val didExample = PrismDID.buildLongFormFromOperation(PrismDIDOperation.Create(Nil, Nil, Nil)).asCanonical

  private val previousOperationHash = didExample.stateHash.toByteArray

  private val seed = HexString
    .fromStringUnsafe(
      "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542"
    )
    .toByteArray

  private val operationFactory = OperationFactory(apollo)

  override def spec =
    suite("OperationFactory")(makeCreateOpeartionSpec, makeUpdateOperationSpec)

  private val makeCreateOpeartionSpec = suite("makeCreateOpeartionSpec")(
    test("make CrateOperation from same seed is deterministic") {
      val didTemplate = ManagedDIDTemplate(publicKeys = Nil, services = Nil, contexts = Nil)
      for {
        result1 <- operationFactory.makeCreateOperation(KeyId("master0"), seed)(0, didTemplate)
        (op1, hdKey1) = result1
        result2 <- operationFactory.makeCreateOperation(KeyId("master0"), seed)(0, didTemplate)
        (op2, hdKey2) = result2
      } yield assert(op1)(equalTo(op2)) &&
        assert(hdKey1)(equalTo(hdKey2))
    },
    test("make CreateOperation must contain 1 master key") {
      val didTemplate = ManagedDIDTemplate(publicKeys = Nil, services = Nil, contexts = Nil)
      for {
        result <- operationFactory.makeCreateOperation(KeyId("master-0"), seed)(0, didTemplate)
        (op, hdKey) = result
        pk = op.publicKeys.head.asInstanceOf[InternalPublicKey]
      } yield assert(op.publicKeys)(hasSize(equalTo(1))) &&
        assert(pk.id)(equalTo("master-0")) &&
        assert(pk.purpose)(equalTo(InternalKeyPurpose.Master))
    },
    test("make CreateOperation containing multiple key purposes") {
      val didTemplate = ManagedDIDTemplate(
        Seq(
          DIDPublicKeyTemplate("auth-0", VerificationRelationship.Authentication, EllipticCurve.SECP256K1),
          DIDPublicKeyTemplate("auth-1", VerificationRelationship.Authentication, EllipticCurve.SECP256K1),
          DIDPublicKeyTemplate("issue-0", VerificationRelationship.AssertionMethod, EllipticCurve.SECP256K1),
        ),
        services = Nil,
        contexts = Nil
      )
      for {
        result <- operationFactory.makeCreateOperation(KeyId("master-0"), seed)(0, didTemplate)
        (op, keys) = result
      } yield assert(op.publicKeys.length)(equalTo(4)) &&
        assert(keys.hdKeys.size)(equalTo(4)) &&
        assert(keys.randKeys)(isEmpty) &&
        assert(keys.hdKeys.get("master-0").get.keyIndex)(equalTo(0)) &&
        assert(keys.hdKeys.get("auth-0").get.keyIndex)(equalTo(0)) &&
        assert(keys.hdKeys.get("auth-1").get.keyIndex)(equalTo(1)) &&
        assert(keys.hdKeys.get("issue-0").get.keyIndex)(equalTo(0))
    },
    test("make CreateOperation containing multiple key types") {
      val didTemplate = ManagedDIDTemplate(
        Seq(
          DIDPublicKeyTemplate("auth-0", VerificationRelationship.Authentication, EllipticCurve.SECP256K1),
          DIDPublicKeyTemplate("auth-1", VerificationRelationship.Authentication, EllipticCurve.ED25519),
          DIDPublicKeyTemplate("comm-0", VerificationRelationship.KeyAgreement, EllipticCurve.X25519),
        ),
        services = Nil,
        contexts = Nil
      )
      for {
        result <- operationFactory.makeCreateOperation(KeyId("master-0"), seed)(0, didTemplate)
        (op, keys) = result
        publicKeyData = op.publicKeys.map {
          case PublicKey(id, _, publicKeyData)         => id -> publicKeyData
          case InternalPublicKey(id, _, publicKeyData) => id -> publicKeyData
        }.toMap
      } yield assert(publicKeyData.size)(equalTo(4)) &&
        assert(publicKeyData.get(KeyId("auth-0")).get)(
          isSubtype[PublicKeyData.ECCompressedKeyData](
            hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(33)) &&
              hasField("crv", _.crv, equalTo(EllipticCurve.SECP256K1))
          )
        ) &&
        assert(publicKeyData.get(KeyId("auth-1")).get)(
          isSubtype[PublicKeyData.ECCompressedKeyData](
            hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(32)) &&
              hasField("crv", _.crv, equalTo(EllipticCurve.ED25519))
          )
        ) &&
        assert(publicKeyData.get(KeyId("comm-0")).get)(
          isSubtype[PublicKeyData.ECCompressedKeyData](
            hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(32)) &&
              hasField("crv", _.crv, equalTo(EllipticCurve.X25519))
          )
        ) &&
        assert(keys.hdKeys.size)(equalTo(2)) &&
        assert(keys.randKeys.size)(equalTo(2)) &&
        assert(keys.hdKeys.get("master-0").get.keyIndex)(equalTo(0)) &&
        assert(keys.hdKeys.get("auth-0").get.keyIndex)(equalTo(0)) &&
        assert(keys.randKeys.get("auth-1").get.keyPair)(isSubtype[Ed25519KeyPair](anything)) &&
        assert(keys.randKeys.get("comm-0").get.keyPair)(isSubtype[X25519KeyPair](anything))
    }
  )

  private val makeUpdateOperationSpec = suite("makeUpdateOperationSpec")(
    test("make UpdateOperation from same seed is deterministic") {
      val counter = HdKeyIndexCounter.zero(0)
      val actions =
        Seq(
          UpdateManagedDIDAction.AddKey(
            DIDPublicKeyTemplate("issue-42", VerificationRelationship.AssertionMethod, EllipticCurve.SECP256K1)
          )
        )
      for {
        result1 <- operationFactory.makeUpdateOperation(seed)(didExample, previousOperationHash, actions, counter)
        (op1, hdKey1) = result1
        result2 <- operationFactory.makeUpdateOperation(seed)(didExample, previousOperationHash, actions, counter)
        (op2, hdKey2) = result2
      } yield assert(op1)(equalTo(op2)) && assert(hdKey1)(equalTo(hdKey2))
    },
    test("make UpdateOperation correctly construct operation and increment counter for derived keys") {
      val counter = HdKeyIndexCounter
        .zero(42)
        .copy(
          verificationRelationship = VerificationRelationshipCounter.zero.copy(
            authentication = 3,
            assertionMethod = 1,
          )
        )
      val actions = Seq(
        UpdateManagedDIDAction.AddKey(
          DIDPublicKeyTemplate("auth-42", VerificationRelationship.Authentication, EllipticCurve.SECP256K1)
        ),
        UpdateManagedDIDAction.AddKey(
          DIDPublicKeyTemplate("issue-42", VerificationRelationship.AssertionMethod, EllipticCurve.SECP256K1)
        ),
      )
      for {
        result <- operationFactory.makeUpdateOperation(seed)(didExample, previousOperationHash, actions, counter)
        (op, keys) = result
        addKeyActions = op.actions.collect { case UpdateDIDAction.AddKey(pk) => pk }
      } yield {
        // counter is correct
        assert(keys.counter.didIndex)(equalTo(42)) &&
        assert(keys.counter.verificationRelationship.authentication)(equalTo(4)) &&
        assert(keys.counter.verificationRelationship.assertionMethod)(equalTo(2)) &&
        assert(keys.counter.verificationRelationship.capabilityDelegation)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.capabilityDelegation)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.keyAgreement)(equalTo(0)) &&
        assert(keys.counter.internalKey.master)(equalTo(0)) &&
        assert(keys.counter.internalKey.revocation)(equalTo(0)) &&
        // path is correct
        assert(keys.hdKeys.size)(equalTo(2)) &&
        assert(keys.hdKeys.get("auth-42").get.keyIndex)(equalTo(3)) &&
        assert(keys.hdKeys.get("issue-42").get.keyIndex)(equalTo(1)) &&
        // rand key is correct
        assert(keys.randKeys)(isEmpty) &&
        // operation is correct
        assert(op.actions)(hasSize(equalTo(2))) &&
        assert(addKeyActions.map(_.id))(hasSameElements(Seq("auth-42", "issue-42"))) &&
        assert(addKeyActions.map(_.publicKeyData))(
          forall(
            isSubtype[PublicKeyData.ECCompressedKeyData](
              hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(33)) &&
                hasField("crv", _.crv, equalTo(EllipticCurve.SECP256K1))
            )
          )
        )
      }
    },
    test("make UpdateOperation correctly construct operation for generated keys") {
      val counter = HdKeyIndexCounter.zero(42)
      val actions = Seq(
        UpdateManagedDIDAction.AddKey(
          DIDPublicKeyTemplate("auth-42", VerificationRelationship.Authentication, EllipticCurve.ED25519)
        ),
        UpdateManagedDIDAction.AddKey(
          DIDPublicKeyTemplate("comm-42", VerificationRelationship.KeyAgreement, EllipticCurve.X25519)
        ),
      )
      for {
        result <- operationFactory.makeUpdateOperation(seed)(didExample, previousOperationHash, actions, counter)
        (op, keys) = result
        addKeyActions = op.actions.collect { case UpdateDIDAction.AddKey(pk) => pk }
      } yield {
        // counter is correct
        assert(keys.counter.didIndex)(equalTo(42)) &&
        assert(keys.counter.verificationRelationship.authentication)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.assertionMethod)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.capabilityDelegation)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.capabilityDelegation)(equalTo(0)) &&
        assert(keys.counter.verificationRelationship.keyAgreement)(equalTo(0)) &&
        assert(keys.counter.internalKey.master)(equalTo(0)) &&
        assert(keys.counter.internalKey.revocation)(equalTo(0)) &&
        // path is correct
        assert(keys.hdKeys.size)(equalTo(0)) &&
        // rand key is correct
        assert(keys.randKeys.size)(equalTo(2)) &&
        assert(keys.randKeys.get("auth-42").get.keyPair)(isSubtype[Ed25519KeyPair](anything)) &&
        assert(keys.randKeys.get("comm-42").get.keyPair)(isSubtype[X25519KeyPair](anything)) &&
        // operation is correct
        assert(op.actions)(hasSize(equalTo(2))) &&
        assert(addKeyActions.find(_.id == KeyId("auth-42")).get.publicKeyData)(
          isSubtype[PublicKeyData.ECCompressedKeyData](
            hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(32)) &&
              hasField("crv", _.crv, equalTo(EllipticCurve.ED25519))
          )
        ) &&
        assert(addKeyActions.find(_.id == KeyId("comm-42")).get.publicKeyData)(
          isSubtype[PublicKeyData.ECCompressedKeyData](
            hasField[PublicKeyData.ECCompressedKeyData, Int]("data", _.data.toByteArray.length, equalTo(32)) &&
              hasField("crv", _.crv, equalTo(EllipticCurve.X25519))
          )
        )
      }
    }
  )

}
