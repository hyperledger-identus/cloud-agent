package org.hyperledger.identus.castor.core.model.did.w3c

import org.hyperledger.identus.castor.core.model.did.{
  CanonicalPrismDID,
  DIDData,
  InternalKeyPurpose,
  PrismDID,
  PrismDIDOperation,
  VerificationRelationship
}
import org.hyperledger.identus.castor.core.util.GenUtils
import org.hyperledger.identus.shared.models.KeyId
import zio.*
import zio.test.*
import zio.test.Assertion.*

object W3CModelHelperSpec extends ZIOSpecDefault {

  import W3CModelHelper.*

  private def generateInternalPublicKey(id: KeyId, purpose: InternalKeyPurpose = InternalKeyPurpose.Master) =
    GenUtils.internalPublicKey
      .map(_.copy(id = id, purpose = purpose))
      .runCollectN(1)
      .map(_.head)

  private def generatePublicKey(id: KeyId, purpose: VerificationRelationship) =
    GenUtils.publicKey.map(_.copy(id = id, purpose = purpose)).runCollectN(1).map(_.head)

  private def generateService(id: String) =
    GenUtils.service.map(_.copy(id = id)).runCollectN(1).map(_.head)

  private def generateDIDData(
      did: CanonicalPrismDID,
      masterKeyId: KeyId = KeyId("master-0"),
      keyIds: Seq[(KeyId, VerificationRelationship)] = Seq.empty,
      serviceIds: Seq[String] = Seq.empty,
      context: Seq[String] = Seq.empty
  ) =
    for {
      masterKey <- generateInternalPublicKey(masterKeyId)
      keys <- ZIO.foreach(keyIds) { case (id, purpose) => generatePublicKey(id, purpose) }
      services <- ZIO.foreach(serviceIds)(generateService)
    } yield DIDData(did, keys, services, Seq(masterKey), context)

  override def spec = suite("W3CModelHelper")(
    test("convert DIDData to w3c DID document representation") {
      for {
        did <- ZIO.fromEither(PrismDID.buildCanonicalFromSuffix("0" * 64))
        didData <- generateDIDData(
          did = did,
          keyIds = Seq(
            KeyId("auth-0") -> VerificationRelationship.Authentication,
            KeyId("iss-0") -> VerificationRelationship.AssertionMethod,
            KeyId("comm-0") -> VerificationRelationship.KeyAgreement,
            KeyId("capinv-0") -> VerificationRelationship.CapabilityInvocation,
            KeyId("capdel-0") -> VerificationRelationship.CapabilityDelegation
          ),
          serviceIds = Seq("service-0")
        )
        didDoc = didData.toW3C(did)
      } yield assert(didDoc.id)(equalTo(did.toString)) &&
        assert(didDoc.controller)(equalTo(did.toString)) &&
        assert(didDoc.context)(contains("https://www.w3.org/ns/did/v1")) &&
        assert(didDoc.authentication)(equalTo(Seq(s"$did#auth-0"))) &&
        assert(didDoc.assertionMethod)(equalTo(Seq(s"$did#iss-0"))) &&
        assert(didDoc.keyAgreement)(equalTo(Seq(s"$did#comm-0"))) &&
        assert(didDoc.capabilityInvocation)(equalTo(Seq(s"$did#capinv-0"))) &&
        assert(didDoc.capabilityDelegation)(equalTo(Seq(s"$did#capdel-0"))) &&
        assert(didDoc.verificationMethod.map(_.id))(
          equalTo(Seq("auth-0", "iss-0", "comm-0", "capinv-0", "capdel-0").map(id => s"$did#$id"))
        )
    },
    test("no publicKey in DID document if contain only internal keys") {
      for {
        did <- ZIO.fromEither(PrismDID.buildCanonicalFromSuffix("0" * 64))
        didData <- generateDIDData(did)
        didDoc = didData.toW3C(did)
      } yield assert(
        Seq(
          didDoc.verificationMethod,
          didDoc.authentication,
          didDoc.assertionMethod,
          didDoc.keyAgreement,
          didDoc.capabilityInvocation,
          didDoc.capabilityDelegation
        )
      )(forall(isEmpty))
    },
    test("use DID that is given to the resolver for id and controller") {
      for {
        did <- ZIO.fromEither(PrismDID.buildCanonicalFromSuffix("0" * 64))
        longFormDID = PrismDID.buildLongFormFromOperation(PrismDIDOperation.Create(Nil, Nil, Nil))
        didData <- generateDIDData(
          did = did,
          keyIds = Seq(KeyId("auth-0") -> VerificationRelationship.Authentication)
        )
        didDoc = didData.toW3C(longFormDID)
      } yield assert(didDoc.id)(equalTo(longFormDID.toString)) &&
        assert(didDoc.controller)(equalTo(longFormDID.toString)) &&
        assert(didDoc.verificationMethod.map(_.id))(forall(startsWithString(longFormDID.toString)))
    },
    test("derive context based on DIDData key, services, and user defined context") {
      for {
        did <- ZIO.fromEither(PrismDID.buildCanonicalFromSuffix("0" * 64))
        didData <- generateDIDData(
          did = did,
          keyIds = Seq(KeyId("auth-0") -> VerificationRelationship.Authentication),
          serviceIds = Seq("service-0"),
          context = Seq("user-defined-context")
        )
        didDataNoKeys = didData.copy(publicKeys = Seq())
        didDataNoService = didData.copy(services = Seq())
      } yield assert(didData.toW3C(did).context)(
        hasSameElements(
          Seq(
            "https://www.w3.org/ns/did/v1",
            "https://identity.foundation/.well-known/did-configuration/v1",
            "https://w3id.org/security/suites/jws-2020/v1",
            "user-defined-context"
          )
        )
      ) &&
        assert(didDataNoKeys.toW3C(did).context)(
          hasSameElements(
            Seq(
              "https://www.w3.org/ns/did/v1",
              "https://identity.foundation/.well-known/did-configuration/v1",
              "user-defined-context"
            )
          )
        ) &&
        assert(didDataNoService.toW3C(did).context)(
          hasSameElements(
            Seq(
              "https://www.w3.org/ns/did/v1",
              "https://w3id.org/security/suites/jws-2020/v1",
              "user-defined-context"
            )
          )
        )
    }
  )

}
