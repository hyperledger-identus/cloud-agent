package org.hyperledger.identus.castor.core.util

import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.error.OperationValidationError
import org.hyperledger.identus.castor.core.util.DIDOperationValidator.Config
import org.hyperledger.identus.shared.models.{Base64UrlString, KeyId}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.collection.immutable.ArraySeq
import scala.language.implicitConversions

object DIDOperationValidatorSpec extends ZIOSpecDefault {

  given Conversion[String, ServiceType.Name] = ServiceType.Name.fromStringUnsafe
  given Conversion[String, ServiceEndpoint] = s =>
    ServiceEndpoint.Single(ServiceEndpoint.UriValue.fromString(s).toOption.get)

  override def spec = suite("DIDOperationValidator")(
    createOperationValidationSpec,
    updateOperationValidationSpec,
    deactivateOperationValidationSpec,
    uriNormalizationSpec
  )

  private val publicKeyData = PublicKeyData.ECKeyData(
    crv = EllipticCurve.SECP256K1,
    x = Base64UrlString.fromStringUnsafe("LlGgkX4eiiodghQ38ZO_9tNRYxZa2ruB-NBRHrwmdSY="),
    y = Base64UrlString.fromStringUnsafe("gyvCAIE6guYGrBAjufAYl39R08896d2tjQDztWL3yP4=")
  )

  private def invalidArgumentContainsString(text: String): Assertion[Either[Any, Any]] = isLeft(
    isSubtype[OperationValidationError.InvalidArgument](hasField("msg", _.msg, containsString(text)))
  )

  private val uriNormalizationSpec = {
    suite("URI normalization")(
      test("normalizeUri converts the scheme and host to lowercase") {
        val uriStr = "HTTP://User@Example.COM/Foo"
        val expected = "http://User@example.com/Foo"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri decodes percent-encoded triplets of unreserved characters") {
        val uriStr = "http://example.com/%7Efoo"
        val expected = "http://example.com/~foo"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri removes dot-segments from the path component") {
        val uriStr = "http://example.com/foo/./bar/baz/../qux"
        val expected = "http://example.com/foo/bar/qux"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri converts an empty path to a \"/\" path") {
        val uriStr = "http://example.com"
        val expected = "http://example.com/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri removes the default port from the URI http") {
        val uriStr = "http://example.com:80/"
        val expected = "http://example.com/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri removes the default port from the URI https") {
        val uriStr = "https://example.com:443/"
        val expected = "https://example.com/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri removes the default port from the URI ftp") {
        val uriStr = "ftp://example.com:21/"
        val expected = "ftp://example.com/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri removes the default port from the URI ws") {
        val uriStr = "ws://example.com:80/"
        val expected = "ws://example.com/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri does not remove port from abc") {
        val uriStr = "abc://example.com:80/"
        val expected = "abc://example.com:80/"
        assert(UriUtils.normalizeUri(uriStr))(isSome(equalTo(expected)))
      },
      test("normalizeUri normalizes URN") {
        val urnStr = "urn:ExAmPlE:animal:ferret:nose"
        val expected = "urn:example:animal:ferret:nose"
        assert(UriUtils.normalizeUri(urnStr))(isSome(equalTo(expected)))
      }
    )
  }

  private val createOperationValidationSpec = {

    def createPrismDIDOperation(
        publicKeys: Seq[PublicKey] = Nil,
        internalKeys: Seq[InternalPublicKey] = Seq(
          InternalPublicKey(
            id = KeyId("master0"),
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        ),
        services: Seq[Service] = Nil,
        context: Seq[String] = Nil
    ) =
      PrismDIDOperation.Create(publicKeys = publicKeys ++ internalKeys, services = services, context = context)

    val testLayer = DIDOperationValidator.layer()

    suite("CreateOperation validation")(
      test("validates a Create operation successfully when using the provided ZLayer") {
        val operation = PrismDIDOperation.Create(
          publicKeys = Seq(
            PublicKey(KeyId("key1"), VerificationRelationship.Authentication, publicKeyData),
            InternalPublicKey(KeyId("master0"), InternalKeyPurpose.Master, publicKeyData)
          ),
          services = Seq(
            Service("service1", ServiceType.Single("LinkedDomains"), "http://example.com/")
          ),
          context = Seq()
        )
        for {
          result <- ZIO.serviceWith[DIDOperationValidator](validator => validator.validate(operation))
        } yield assert(result)(equalTo(DIDOperationValidator(Config.default).validate(operation)))
      },
      test("accept valid CreateOperation") {
        val op = createPrismDIDOperation()
        assert(DIDOperationValidator(Config.default).validate(op))(isRight)
      },
      test("reject CreateOperation on too many DID publicKey access") {
        val publicKeys = (1 to 10).map(i =>
          PublicKey(
            id = KeyId(s"key$i"),
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val internalKeys = (1 to 10).map(i =>
          InternalPublicKey(
            id = KeyId(s"master$i"),
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys, internalKeys = internalKeys)
        assert(DIDOperationValidator(Config.default.copy(publicKeyLimit = 15)).validate(op))(
          isLeft(isSubtype[OperationValidationError.TooManyDidPublicKeyAccess](anything))
        )
      },
      test("reject CreateOperation on duplicated DID public key id") {
        val publicKeys = (1 to 10).map(i =>
          PublicKey(
            id = KeyId(s"key$i"),
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val internalKeys = Seq(
          InternalPublicKey(
            id = KeyId(s"key1"),
            purpose = InternalKeyPurpose.Master,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys, internalKeys = internalKeys)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("id for public-keys is not unique")
        )
      },
      test("reject CreateOperation on too many service access") {
        val services = (1 to 20).map(i =>
          Service(
            id = s"service$i",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/"
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config.default.copy(serviceLimit = 15)).validate(op))(
          isLeft(isSubtype[OperationValidationError.TooManyDidServiceAccess](anything))
        )
      },
      test("reject CreateOperation on duplicated service id") {
        val services = (1 to 3).map(i =>
          Service(
            id = s"service0",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/"
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("id for services is not unique")
        )
      },
      test("reject CreateOperation on invalid key-id") {
        val publicKeys = (1 to 2).map(i =>
          PublicKey(
            id = KeyId(s"key $i"),
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val op = createPrismDIDOperation(publicKeys = publicKeys)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("public key id is invalid: [key 1, key 2]")
        )
      },
      test("reject CreateOperation on invalid service-id") {
        val services = (1 to 2).map(i =>
          Service(
            id = s"service $i",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/"
          )
        )
        val op = createPrismDIDOperation(services = services)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service id is invalid: [service 1, service 2]")
        )
      },
      test("reject CreateOperation on too long key-id") {
        val publicKey = PublicKey(
          id = KeyId(s"key-${"0" * 100}"),
          purpose = VerificationRelationship.Authentication,
          publicKeyData = publicKeyData
        )
        val op = createPrismDIDOperation(publicKeys = Seq(publicKey))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"public key id is too long: [${publicKey.id}]")
        )
      },
      test("reject CreateOperation on too long service-id") {
        val service = Service(
          id = s"service-${"0" * 100}",
          `type` = ServiceType.Single("LinkedDomains"),
          serviceEndpoint = "http://example.com/"
        )
        val op = createPrismDIDOperation(services = Seq(service))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"service id is too long: [${service.id}]")
        )
      },
      test("reject CreateOperation on duplicated context") {
        val op = createPrismDIDOperation(context = Seq("https://example.com", "https://example.com"))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is not unique")
        )
      },
      test("reject CreateOperation when context is not a URI") {
        val op = createPrismDIDOperation(context = Seq("not a URI"))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is not a valid URI")
        )
      },
      test("reject CreateOperation when context is too long") {
        val op = createPrismDIDOperation(context = Seq(s"http://example.com/${"0" * 100}"))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is too long")
        )
      },
      test("reject CreateOperation on too long serviceType") {
        val service = Service(
          id = "service",
          `type` = ServiceType.Single("0" * 101),
          serviceEndpoint = "http://example.com/"
        )
        val op = createPrismDIDOperation(services = Seq(service))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"service type is too long: [service]")
        )
      },
      test("reject CreateOperation on too long serviceEndpoint") {
        val service = Service(
          id = "service",
          `type` = ServiceType.Single("LinkedDomains"),
          serviceEndpoint = s"http://example.com/${"0" * 300}"
        )
        val op = createPrismDIDOperation(services = Seq(service))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"service endpoint is too long: [service]")
        )
      },
      test("reject CreateOperation when master key does not exist") {
        val op = createPrismDIDOperation(internalKeys = Nil)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("operation must contain at least 1 master key")
        )
      },
      test("accept CreateOperation when publicKeys is empty because master key always exist") {
        val op = createPrismDIDOperation(publicKeys = Nil)
        assert(DIDOperationValidator(Config.default).validate(op))(isRight)
      },
      test("accept CreateOperation when services is None") {
        val op = createPrismDIDOperation(services = Nil)
        assert(DIDOperationValidator(Config.default).validate(op))(isRight)
      },
      test("reject CreateOperation when service id is empty") {
        val op = createPrismDIDOperation(services =
          Seq(
            Service(
              id = "",
              `type` = ServiceType.Single("LinkedDomains"),
              serviceEndpoint = "http://example.com/"
            )
          )
        )
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service id is invalid: []")
        )
      },
      test("reject CreateOperation when one of the service ids has an invalid format") {
        val op = createPrismDIDOperation(
          publicKeys = Seq(
            PublicKey(
              id = KeyId("key-0"),
              purpose = VerificationRelationship.Authentication,
              publicKeyData = publicKeyData
            ),
            PublicKey(
              id = KeyId("key-1"),
              purpose = VerificationRelationship.AssertionMethod,
              publicKeyData = publicKeyData
            )
          ),
          services = Seq(
            Service(
              id = "service-0",
              `type` = ServiceType.Single("LinkedDomains"),
              serviceEndpoint = "http://example.com/login/../login"
            ),
            Service(
              id = "Wrong service",
              `type` = ServiceType.Single("LinkedDomains"),
              serviceEndpoint = "http://example.com/login/../login"
            )
          )
        )
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service id is invalid: [Wrong service]")
        )
      },
      test("reject CreateOperation when master key is not a secp256k1 key") {
        val op = createPrismDIDOperation(
          internalKeys = Seq(
            InternalPublicKey(
              id = KeyId("master0"),
              purpose = InternalKeyPurpose.Master,
              publicKeyData = PublicKeyData.ECKeyData(
                crv = EllipticCurve.ED25519,
                x = Base64UrlString.fromStringUnsafe("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"),
                y = Base64UrlString.fromStringUnsafe("")
              )
            ),
            InternalPublicKey(
              id = KeyId("master1"),
              purpose = InternalKeyPurpose.Master,
              publicKeyData = PublicKeyData.ECKeyData(
                crv = EllipticCurve.SECP256K1,
                x = Base64UrlString.fromStringUnsafe("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"),
                y = Base64UrlString.fromStringUnsafe("")
              )
            )
          )
        )
        assert(DIDOperationValidator(Config.default).validate(op))(
          isLeft(equalTo(OperationValidationError.InvalidMasterKeyData(Seq(KeyId("master0"), KeyId("master1")))))
        )
      }
    ).provideLayer(testLayer)
  }

  private val deactivateOperationValidationSpec = {
    def deactivatePrismDIDOperation(previousOperationHash: ArraySeq[Byte] = ArraySeq.fill(32)(0)) =
      PrismDIDOperation.Deactivate(
        PrismDID.buildCanonicalFromSuffix("0" * 64).toOption.get,
        previousOperationHash
      )

    suite("DeactivateOperation validation")(
      test("accept valid DeactivateOperation") {
        val op = deactivatePrismDIDOperation()
        assert(DIDOperationValidator(Config.default).validate(op))(isRight)
      }
    )
  }

  private val updateOperationValidationSpec = {
    def updatePrismDIDOperation(
        actions: Seq[UpdateDIDAction] = Nil,
        previousOperationHash: ArraySeq[Byte] = ArraySeq.fill(32)(0)
    ) =
      PrismDIDOperation.Update(
        PrismDID.buildCanonicalFromSuffix("0" * 64).toOption.get,
        previousOperationHash,
        actions
      )

    suite("UpdateOperation validation")(
      test("accept valid UpdateOperation") {
        val op = updatePrismDIDOperation(
          Seq(
            UpdateDIDAction.AddKey(PublicKey(KeyId("key0"), VerificationRelationship.Authentication, publicKeyData)),
            UpdateDIDAction.AddInternalKey(
              InternalPublicKey(KeyId("master0"), InternalKeyPurpose.Master, publicKeyData)
            ),
            UpdateDIDAction.RemoveKey("key0"),
            UpdateDIDAction.AddService(
              Service("service0", ServiceType.Single("LinkedDomains"), "http://example.com/")
            ),
            UpdateDIDAction.RemoveService("service0"),
            UpdateDIDAction.UpdateService("service0", Some(ServiceType.Single("LinkedDomains")), None),
            UpdateDIDAction.UpdateService("service0", None, Some("http://example.com/")),
            UpdateDIDAction.UpdateService(
              "service0",
              Some(ServiceType.Single("LinkedDomains")),
              Some("http://example.com/")
            )
          )
        )
        assert(DIDOperationValidator(Config.default).validate(op))(isRight)
      },
      test("reject UpdateOperation on too many DID publicKey access") {
        val addKeyActions = (1 to 10).map(i =>
          UpdateDIDAction.AddKey(
            PublicKey(
              id = KeyId(s"key$i"),
              purpose = VerificationRelationship.Authentication,
              publicKeyData = publicKeyData
            )
          )
        )
        val addInternalKeyActions = (1 to 10).map(i =>
          UpdateDIDAction.AddInternalKey(
            InternalPublicKey(
              id = KeyId(s"master$i"),
              purpose = InternalKeyPurpose.Master,
              publicKeyData = publicKeyData
            )
          )
        )
        val removeKeyActions = (1 to 10).map(i => UpdateDIDAction.RemoveKey(s"remove$i"))
        val op = updatePrismDIDOperation(addKeyActions ++ addInternalKeyActions ++ removeKeyActions)
        assert(DIDOperationValidator(Config.default.copy(publicKeyLimit = 25)).validate(op))(
          isLeft(isSubtype[OperationValidationError.TooManyDidPublicKeyAccess](anything))
        )
      },
      test("reject UpdateOperation on too many service access") {
        val addServiceActions = (1 to 10).map(i =>
          UpdateDIDAction.AddService(
            Service(
              id = s"service$i",
              `type` = ServiceType.Single("LinkedDomains"),
              serviceEndpoint = "http://example.com/"
            )
          )
        )
        val removeServiceActions = (1 to 10).map(i => UpdateDIDAction.RemoveService(s"remove$i"))
        val updateServiceActions = (1 to 10).map(i =>
          UpdateDIDAction.UpdateService(
            s"update$i",
            Some(ServiceType.Single("LinkedDomains")),
            Some("http://example.com/")
          )
        )
        val op = updatePrismDIDOperation(addServiceActions ++ removeServiceActions ++ updateServiceActions)
        assert(DIDOperationValidator(Config.default.copy(serviceLimit = 25)).validate(op))(
          isLeft(isSubtype[OperationValidationError.TooManyDidServiceAccess](anything))
        )
      },
      test("reject UpdateOperation on invalid key-id") {
        val action1 = UpdateDIDAction.AddKey(
          PublicKey(
            id = KeyId("key 1"),
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val action2 = UpdateDIDAction.RemoveKey(id = "key 2")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("public key id is invalid: [key 1, key 2]")
        )
      },
      test("reject UpdateOperation on invalid service-id") {
        val action1 = UpdateDIDAction.AddService(
          Service(
            id = "service 1",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/"
          )
        )
        val action2 = UpdateDIDAction.RemoveService(id = "service 2")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service id is invalid: [service 1, service 2]")
        )
      },
      test("reject UpdateOperation on too long key-id") {
        val action1 = UpdateDIDAction.AddKey(
          PublicKey(
            id = KeyId(s"key-${"0" * 100}"),
            purpose = VerificationRelationship.Authentication,
            publicKeyData = publicKeyData
          )
        )
        val action2 = UpdateDIDAction.RemoveKey(id = s"key-${"1" * 100}")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"public key id is too long: [${action1.publicKey.id}, ${action2.id}]")
        )
      },
      test("reject UpdateOperation on too long service-id") {
        val action1 = UpdateDIDAction.AddService(
          Service(
            id = s"service-${"0" * 100}",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/"
          )
        )
        val action2 = UpdateDIDAction.RemoveService(id = s"service-${"1" * 100}")
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString(s"service id is too long: [${action1.service.id}, ${action2.id}]")
        )
      },
      test("reject UpdateOperation on duplicated context") {
        val action1 = UpdateDIDAction.PatchContext(Seq.empty)
        val action2 = UpdateDIDAction.PatchContext(Seq.fill(2)("https://www.w3.org/ns/did/v1"))
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is not unique")
        )
      },
      test("reject UpdateOperation when context is not a URI") {
        val action1 = UpdateDIDAction.PatchContext(Seq("not a URI"))
        val op = updatePrismDIDOperation(Seq(action1))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is not a valid URI")
        )
      },
      test("reject UpdateOperation when context is too long") {
        val action1 = UpdateDIDAction.PatchContext(Seq(s"http://example.com/${"0" * 100}"))
        val op = updatePrismDIDOperation(Seq(action1))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("context is too long")
        )
      },
      test("reject UpdateOperation on too long serviceType") {
        val action = UpdateDIDAction.AddService(
          Service(
            id = "service",
            `type` = ServiceType.Single("a" * 101),
            serviceEndpoint = "http://example.com/"
          )
        )
        val op = updatePrismDIDOperation(Seq(action))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service type is too long: [service]")
        )
      },
      test("reject UpdateOperation on too long serviceEndpoint") {
        val action = UpdateDIDAction.AddService(
          Service(
            id = "service",
            `type` = ServiceType.Single("LinkedDomains"),
            serviceEndpoint = "http://example.com/" + "a" * 1001
          )
        )
        val op = updatePrismDIDOperation(Seq(action))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("service endpoint is too long: [service]")
        )
      },
      test("reject UpdateOperation on invalid previousOperationHash") {
        val op = updatePrismDIDOperation(previousOperationHash = ArraySeq.empty)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("previousOperationHash must have a size of")
        )
      },
      test("reject UpdateOperation on empty update action") {
        val op = updatePrismDIDOperation(Nil)
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("operation must contain at least 1 update action")
        )
      },
      test("reject UpdateOperation when action UpdateService have both type and serviceEndpoint empty") {
        val op = updatePrismDIDOperation(Seq(UpdateDIDAction.UpdateService("service-1", None, None)))
        assert(DIDOperationValidator(Config.default).validate(op))(
          invalidArgumentContainsString("must not have both 'type' and 'serviceEndpoints' empty")
        )
      },
      test("reject UpdateOperation when master key is not a secp256k1 key") {
        val action1 = UpdateDIDAction.AddInternalKey(
          InternalPublicKey(
            id = KeyId("master0"),
            purpose = InternalKeyPurpose.Master,
            publicKeyData = PublicKeyData.ECKeyData(
              crv = EllipticCurve.ED25519,
              x = Base64UrlString.fromStringUnsafe("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"),
              y = Base64UrlString.fromStringUnsafe("")
            )
          )
        )
        val action2 = UpdateDIDAction.AddInternalKey(
          InternalPublicKey(
            id = KeyId("master1"),
            purpose = InternalKeyPurpose.Master,
            publicKeyData = PublicKeyData.ECKeyData(
              crv = EllipticCurve.SECP256K1,
              x = Base64UrlString.fromStringUnsafe("11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"),
              y = Base64UrlString.fromStringUnsafe("")
            )
          )
        )
        val op = updatePrismDIDOperation(Seq(action1, action2))
        assert(DIDOperationValidator(Config.default).validate(op))(
          isLeft(equalTo(OperationValidationError.InvalidMasterKeyData(Seq(KeyId("master0"), KeyId("master1")))))
        )
      }
    )
  }

}
