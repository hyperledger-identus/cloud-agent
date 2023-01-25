package io.iohk.atala.castor.core.util

import java.net.{URI, URL}
import io.iohk.atala.shared.models.HexStrings.*
import io.iohk.atala.shared.models.Base64UrlStrings.*
import io.iohk.atala.castor.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  InternalPublicKey,
  PrismDID,
  PrismDIDOperation,
  PublicKey,
  PublicKeyData,
  Service,
  ServiceType,
  UpdateDIDAction,
  VerificationRelationship
}
import io.iohk.atala.castor.core.model.error.DIDOperationError
import io.iohk.atala.castor.core.util.DIDOperationValidator.Config
import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.collection.immutable.ArraySeq

object DIDOperationValidatorSpec extends ZIOSpecDefault {

  override def spec = suite("DIDOperationValidator")(createOperationValidationSpec, updateOperationValidationSpec)

  private val publicKeyData = PublicKeyData.ECKeyData(
    crv = EllipticCurve.SECP256K1,
    x = Base64UrlString.fromStringUnsafe("00"),
    y = Base64UrlString.fromStringUnsafe("00")
  )

  private val createOperationValidationSpec = {
    def createPrismDIDOperation(
        publicKeys: Seq[PublicKey] = Nil,
        internalKeys: Seq[InternalPublicKey] = Seq(
          InternalPublicKey(
            id = "master0",
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        ),
        services: Seq[Service] = Nil
    ) =
      PrismDIDOperation.Create(publicKeys = publicKeys, internalKeys = internalKeys, services = services)

    suite("CreateOperation validation")(
      test("accept valid CreateOperation") {
        val op = createPrismDIDOperation()
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(isRight)
      },
      test("reject CreateOperation on too many DID publicKey access") {
        val publicKeys = (1 to 10).map(i =>
          PublicKey(
            id = s"key$i",
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val internalKeys = (1 to 10).map(i =>
          InternalPublicKey(
            id = s"master$i",
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys, internalKeys = internalKeys)
        assert(DIDOperationValidator(Config(15, 15)).validate(op))(
          isLeft(isSubtype[DIDOperationError.TooManyDidPublicKeyAccess](anything))
        )
      },
      test("reject CreateOperation on duplicated DID public key id") {
        val publicKeys = (1 to 10).map(i =>
          PublicKey(
            id = s"key$i",
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val internalKeys = Seq(
          InternalPublicKey(
            id = s"key1",
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys, internalKeys = internalKeys)
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("id for public-keys is not unique"))
            )
          )
        )
      },
      test("reject CreateOperation on too many service access") {
        val services = (1 to 20).map(i =>
          Service(
            id = s"service$i",
            `type` = ServiceType.MediatorService,
            serviceEndpoint = Seq(URI.create("http://example.com"))
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config(15, 15)).validate(op))(
          isLeft(isSubtype[DIDOperationError.TooManyDidServiceAccess](anything))
        )
      },
      test("reject CreateOperation on duplicated service id") {
        val services = (1 to 3).map(i =>
          Service(
            id = s"service0",
            `type` = ServiceType.MediatorService,
            serviceEndpoint = Seq(URI.create("http://example.com"))
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config(15, 15)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("id for services is not unique"))
            )
          )
        )
      },
      test("reject CreateOperation on invalid key-id") {
        val publicKeys = (1 to 2).map(i =>
          PublicKey(
            id = s"key $i",
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys)
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("public key id is invalid: [key 1, key 2]"))
            )
          )
        )
      },
      test("reject CreateOperation on invalid service-id") {
        val services = (1 to 2).map(i =>
          Service(
            id = s"service $i",
            `type` = ServiceType.MediatorService,
            serviceEndpoint = Seq(URI.create("http://example.com"))
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("service id is invalid: [service 1, service 2]"))
            )
          )
        )
      },
      test("reject CreateOperation when master does not exist") {
        val op = createPrismDIDOperation(internalKeys = Nil)
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("operation must contain at least 1 master key"))
            )
          )
        )
      }
    )
  }

  private val updateOperationValidationSpec = {
    def updatePrismDIDOperation(
        actions: Seq[UpdateDIDAction] = Nil
    ) =
      PrismDIDOperation.Update(did = PrismDID.buildCanonicalFromSuffix("0" * 64).toOption.get, ArraySeq.empty, actions)

    suite("UpdateOperation validation")(
      test("accept valid UpdateOperation") {
        val op = updatePrismDIDOperation(
          Seq(
            UpdateDIDAction.AddKey(PublicKey("key0", VerificationRelationship.Authentication, publicKeyData)),
            UpdateDIDAction.AddInternalKey(InternalPublicKey("master0", InternalKeyPurpose.Master, publicKeyData)),
            UpdateDIDAction.RemoveKey("key0"),
            UpdateDIDAction.AddService(
              Service("service0", ServiceType.MediatorService, Seq(URI.create("http://example.com")))
            ),
            UpdateDIDAction.RemoveService("service0"),
            UpdateDIDAction.UpdateService("service0", Some(ServiceType.MediatorService), Nil),
            UpdateDIDAction.UpdateService("service0", None, Seq(URI.create("http://example.com"))),
            UpdateDIDAction.UpdateService(
              "service0",
              Some(ServiceType.MediatorService),
              Seq(URI.create("http://example.com"))
            )
          )
        )
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(isRight)
      },
      test("reject UpdateOperation on too many DID publicKey access") {
        val addKeyActions = (1 to 10).map(i =>
          UpdateDIDAction.AddKey(
            PublicKey(
              id = s"key$i",
              purpose = VerificationRelationship.Authentication,
              publicKeyData = publicKeyData
            )
          )
        )
        val addInternalKeyActions = (1 to 10).map(i =>
          UpdateDIDAction.AddInternalKey(
            InternalPublicKey(
              id = s"master$i",
              purpose = InternalKeyPurpose.Master,
              publicKeyData = publicKeyData
            )
          )
        )
        val removeKeyActions = (1 to 10).map(i => UpdateDIDAction.RemoveKey(s"remove$i"))
        val op = updatePrismDIDOperation(addKeyActions ++ addInternalKeyActions ++ removeKeyActions)
        assert(DIDOperationValidator(Config(25, 25)).validate(op))(
          isLeft(isSubtype[DIDOperationError.TooManyDidPublicKeyAccess](anything))
        )
      },
      test("reject UpdateOperation on too many service access") {
        val addServiceActions = (1 to 10).map(i =>
          UpdateDIDAction.AddService(
            Service(
              id = s"service$i",
              `type` = ServiceType.MediatorService,
              serviceEndpoint = Seq(URI.create("http://example.com"))
            )
          )
        )
        val removeServiceActions = (1 to 10).map(i => UpdateDIDAction.RemoveService(s"remove$i"))
        val updateServiceActions = (1 to 10).map(i =>
          UpdateDIDAction.UpdateService(
            s"update$i",
            Some(ServiceType.MediatorService),
            Seq(URI.create("http://example.com"))
          )
        )
        val op = updatePrismDIDOperation(addServiceActions ++ removeServiceActions ++ updateServiceActions)
        assert(DIDOperationValidator(Config(25, 25)).validate(op))(
          isLeft(isSubtype[DIDOperationError.TooManyDidServiceAccess](anything))
        )
      },
      test("reject UpdateOperation on invalid key-id") {
        val action1 = UpdateDIDAction.AddKey(
          PublicKey(
            id = "key 1",
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val action2 = UpdateDIDAction.RemoveKey(id = "key 2")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("public key id is invalid: [key 1, key 2]"))
            )
          )
        )
      },
      test("reject UpdateOperation on invalid service-id") {
        val action1 = UpdateDIDAction.AddService(
          Service(
            id = "service 1",
            `type` = ServiceType.MediatorService,
            serviceEndpoint = Seq(URI.create("http://example.com"))
          )
        )
        val action2 = UpdateDIDAction.RemoveService(id = "service 2")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("service id is invalid: [service 1, service 2]"))
            )
          )
        )
      },
      test("reject UpdateOperation on empty update action") {
        val op = updatePrismDIDOperation(Nil)
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("operation must contain at least 1 update action"))
            )
          )
        )
      },
      test("reject UpdateOperation on UpdateService action empty") {
        val op = updatePrismDIDOperation(Seq(UpdateDIDAction.UpdateService("service0", None, Nil)))
        assert(DIDOperationValidator(Config(50, 50)).validate(op))(
          isLeft(
            isSubtype[DIDOperationError.InvalidArgument](
              hasField("msg", _.msg, containsString("must not have both 'type' and 'serviceEndpoints' empty"))
            )
          )
        )
      }
    )
  }

}
