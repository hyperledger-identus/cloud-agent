package org.hyperledger.identus.castor.core.util

import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.error.OperationValidationError
import org.hyperledger.identus.castor.core.util.DIDOperationValidator.Config
import org.hyperledger.identus.castor.core.util.Prelude.*
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.KeyId
import zio.*

import scala.collection.immutable.ArraySeq

object DIDOperationValidator {
  final case class Config(
      publicKeyLimit: Int,
      serviceLimit: Int,
      maxIdSize: Int,
      maxServiceTypeSize: Int,
      maxServiceEndpointSize: Int
  )

  object Config {
    val default: Config = Config(
      publicKeyLimit = 50,
      serviceLimit = 50,
      maxIdSize = 50,
      maxServiceTypeSize = 100,
      maxServiceEndpointSize = 300
    )
  }

  def layer(config: Config = Config.default): ULayer[DIDOperationValidator] =
    ZLayer.succeed(DIDOperationValidator(config))
}

class DIDOperationValidator(config: Config) extends BaseOperationValidator {
  def validate(operation: PrismDIDOperation): Either[OperationValidationError, Unit] = {
    operation match {
      case op: PrismDIDOperation.Create     => CreateOperationValidator.validate(config)(op)
      case op: PrismDIDOperation.Update     => UpdateOperationValidator.validate(config)(op)
      case op: PrismDIDOperation.Deactivate => DeactivateOperationValidator.validate(config)(op)
    }
  }
}

private object CreateOperationValidator extends BaseOperationValidator {
  def validate(config: Config)(operation: PrismDIDOperation.Create): Either[OperationValidationError, Unit] = {
    for {
      _ <- validateMaxPublicKeysAccess(config)(operation, extractKeyIds)
      _ <- validateMaxServiceAccess(config)(operation, extractServiceIds)
      _ <- validateUniquePublicKeyId(operation, extractKeyIds)
      _ <- validateUniqueServiceId(operation, extractServiceIds)
      _ <- validateMasterKeyIsSecp256k1(operation, extractKeyData)
      _ <- validateKeyIdIsUriFragment(operation, extractKeyIds)
      _ <- validateKeyIdLength(config)(operation, extractKeyIds)
      _ <- validateServiceIdIsUriFragment(operation, extractServiceIds)
      _ <- validateServiceIdLength(config)(operation, extractServiceIds)
      _ <- validateServiceEndpointLength(config)(operation, extractServiceEndpoint)
      _ <- validateServiceTypeLength(config)(operation, extractServiceType)
      _ <- validateUniqueContext(operation, _.context :: Nil)
      _ <- validateContextLength(operation, _.context :: Nil)
      _ <- validateContextIsUri(operation, _.context :: Nil)
      _ <- validateMasterKeyExists(operation)
    } yield ()
  }

  private def validateMasterKeyExists(operation: PrismDIDOperation.Create): Either[OperationValidationError, Unit] = {
    val masterKeys =
      operation.publicKeys.collect { case pk: InternalPublicKey => pk }.filter(_.purpose == InternalKeyPurpose.Master)
    if (masterKeys.nonEmpty) Right(())
    else Left(OperationValidationError.InvalidArgument("create operation must contain at least 1 master key"))
  }

  private def extractKeyIds(operation: PrismDIDOperation.Create): Seq[KeyId] = operation.publicKeys.map {
    case PublicKey(id, _, _)         => id
    case InternalPublicKey(id, _, _) => id
  }

  private def extractKeyData(
      operation: PrismDIDOperation.Create
  ): Seq[(KeyId, VerificationRelationship | InternalKeyPurpose, PublicKeyData)] =
    operation.publicKeys.map {
      case PublicKey(id, purpose, data)         => (id, purpose, data)
      case InternalPublicKey(id, purpose, data) => (id, purpose, data)
    }

  private def extractServiceIds(operation: PrismDIDOperation.Create): Seq[String] =
    operation.services.map(_.id)

  private def extractServiceEndpoint(operation: PrismDIDOperation.Create): Seq[(String, ServiceEndpoint)] =
    operation.services.map { s => (s.id, s.serviceEndpoint) }

  private def extractServiceType(operation: PrismDIDOperation.Create): Seq[(String, ServiceType)] =
    operation.services.map { s => (s.id, s.`type`) }

}

private object UpdateOperationValidator extends BaseOperationValidator {
  def validate(config: Config)(operation: PrismDIDOperation.Update): Either[OperationValidationError, Unit] = {
    for {
      _ <- validateMaxPublicKeysAccess(config)(operation, extractKeyIds)
      _ <- validateMaxServiceAccess(config)(operation, extractServiceIds)
      _ <- validateMasterKeyIsSecp256k1(operation, extractKeyData)
      _ <- validateKeyIdIsUriFragment(operation, extractKeyIds)
      _ <- validateKeyIdLength(config)(operation, extractKeyIds)
      _ <- validateServiceIdIsUriFragment(operation, extractServiceIds)
      _ <- validateServiceIdLength(config)(operation, extractServiceIds)
      _ <- validateServiceEndpointLength(config)(operation, extractServiceEndpoint)
      _ <- validateServiceTypeLength(config)(operation, extractServiceType)
      _ <- validateUniqueContext(operation, extractContexts)
      _ <- validateContextLength(operation, extractContexts)
      _ <- validateContextIsUri(operation, extractContexts)
      _ <- validatePreviousOperationHash(operation, _.previousOperationHash)
      _ <- validateNonEmptyUpdateAction(operation)
      _ <- validateUpdateServiceNonEmpty(operation)
    } yield ()
  }

  private def validateNonEmptyUpdateAction(
      operation: PrismDIDOperation.Update
  ): Either[OperationValidationError, Unit] = {
    val isActionNonEmpty = operation.actions.nonEmpty
    if (isActionNonEmpty) Right(())
    else Left(OperationValidationError.InvalidArgument("update operation must contain at least 1 update action"))
  }

  private def validateUpdateServiceNonEmpty(
      operation: PrismDIDOperation.Update
  ): Either[OperationValidationError, Unit] = {
    val isNonEmptyUpdateService = operation.actions.forall {
      case UpdateDIDAction.UpdateService(_, None, None) => false
      case _                                            => true
    }
    if (isNonEmptyUpdateService) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          "update operation with UpdateServiceAction must not have both 'type' and 'serviceEndpoints' empty"
        )
      )
  }

  private def extractKeyIds(operation: PrismDIDOperation.Update): Seq[KeyId] = operation.actions.collect {
    case UpdateDIDAction.AddKey(pk)         => pk.id
    case UpdateDIDAction.AddInternalKey(pk) => pk.id
    case UpdateDIDAction.RemoveKey(id)      => KeyId(id)
  }

  private def extractKeyData(
      operation: PrismDIDOperation.Update
  ): Seq[(KeyId, VerificationRelationship | InternalKeyPurpose, PublicKeyData)] =
    operation.actions.collect {
      case UpdateDIDAction.AddKey(pk)         => (pk.id, pk.purpose, pk.publicKeyData)
      case UpdateDIDAction.AddInternalKey(pk) => (pk.id, pk.purpose, pk.publicKeyData)
    }

  private def extractServiceIds(operation: PrismDIDOperation.Update): Seq[String] = operation.actions.collect {
    case UpdateDIDAction.AddService(service)     => service.id
    case UpdateDIDAction.RemoveService(id)       => id
    case UpdateDIDAction.UpdateService(id, _, _) => id
  }

  private def extractServiceEndpoint(operation: PrismDIDOperation.Update): Seq[(String, ServiceEndpoint)] =
    operation.actions.collect {
      case UpdateDIDAction.AddService(service)                  => service.id -> service.serviceEndpoint
      case UpdateDIDAction.UpdateService(id, _, Some(endpoint)) => id -> endpoint
    }

  private def extractServiceType(operatio: PrismDIDOperation.Update): Seq[(String, ServiceType)] =
    operatio.actions.collect {
      case UpdateDIDAction.AddService(service)                     => service.id -> service.`type`
      case UpdateDIDAction.UpdateService(id, Some(serviceType), _) => id -> serviceType
    }

  private def extractContexts(operation: PrismDIDOperation.Update): Seq[Seq[String]] =
    operation.actions.collect { case UpdateDIDAction.PatchContext(context) => context }
}

private object DeactivateOperationValidator extends BaseOperationValidator {
  def validate(config: Config)(operation: PrismDIDOperation.Deactivate): Either[OperationValidationError, Unit] =
    validatePreviousOperationHash(operation, _.previousOperationHash)
}

private trait BaseOperationValidator {

  type KeyIdExtractor[T] = T => Seq[KeyId]
  type ServiceIdExtractor[T] = T => Seq[String]
  type ServiceTypeExtractor[T] = T => Seq[(String, ServiceType)]
  type ServiceEndpointExtractor[T] = T => Seq[(String, ServiceEndpoint)]
  type ContextExtractor[T] = T => Seq[Seq[String]]
  type KeyDataExtractor[T] = T => Seq[(KeyId, VerificationRelationship | InternalKeyPurpose, PublicKeyData)]

  protected def validateMaxPublicKeysAccess[T <: PrismDIDOperation](
      config: Config
  )(operation: T, keyIdExtractor: KeyIdExtractor[T]): Either[OperationValidationError, Unit] = {
    val keyCount = keyIdExtractor(operation).length
    if (keyCount <= config.publicKeyLimit) Right(())
    else Left(OperationValidationError.TooManyDidPublicKeyAccess(config.publicKeyLimit, Some(keyCount)))
  }

  protected def validateMaxServiceAccess[T <: PrismDIDOperation](
      config: Config
  )(operation: T, serviceIdExtractor: ServiceIdExtractor[T]): Either[OperationValidationError, Unit] = {
    val serviceCount = serviceIdExtractor(operation).length
    if (serviceCount <= config.serviceLimit) Right(())
    else Left(OperationValidationError.TooManyDidServiceAccess(config.serviceLimit, Some(serviceCount)))
  }

  protected def validateUniquePublicKeyId[T <: PrismDIDOperation](
      operation: T,
      keyIdExtractor: KeyIdExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val ids = keyIdExtractor(operation)
    if (ids.isUnique) Right(())
    else Left(OperationValidationError.InvalidArgument("id for public-keys is not unique"))
  }

  protected def validateUniqueServiceId[T <: PrismDIDOperation](
      operation: T,
      serviceIdExtractor: ServiceIdExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val ids = serviceIdExtractor(operation)
    if (ids.isUnique) Right(())
    else Left(OperationValidationError.InvalidArgument("id for services is not unique"))
  }

  protected def validateUniqueContext[T <: PrismDIDOperation](
      operation: T,
      contextExtractor: ContextExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val contexts = contextExtractor(operation)
    val nonUniqueContextList = contexts.filterNot(_.isUnique)
    if (nonUniqueContextList.isEmpty) Right(())
    else Left(OperationValidationError.InvalidArgument("context is not unique"))
  }

  protected def validateContextIsUri[T <: PrismDIDOperation](
      operation: T,
      contextExtractor: ContextExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val contexts = contextExtractor(operation)
    val nonUriContexts = contexts.flatten.filterNot(UriUtils.isValidUriString)
    if (nonUriContexts.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          s"context is not a valid URI: ${nonUriContexts.mkString("[", ", ", "]")}"
        )
      )
  }

  protected def validateContextLength[T <: PrismDIDOperation](
      operation: T,
      contextExtractor: ContextExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val contexts = contextExtractor(operation)
    val invalidContexts = contexts.flatten.filter(_.length > 100) // FIXME: confirm this value  with the spec
    if (invalidContexts.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(s"context is too long: ${invalidContexts.mkString("[", ", ", "]")}")
      )
  }

  protected def validateKeyIdIsUriFragment[T <: PrismDIDOperation](
      operation: T,
      keyIdExtractor: KeyIdExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val ids = keyIdExtractor(operation)
    val invalidIds = ids.map(_.value).filterNot(UriUtils.isValidUriFragment)
    if (invalidIds.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(s"public key id is invalid: ${invalidIds.mkString("[", ", ", "]")}")
      )
  }

  protected def validateServiceIdIsUriFragment[T <: PrismDIDOperation](
      operation: T,
      serviceIdExtractor: ServiceIdExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val ids = serviceIdExtractor(operation)
    val invalidIds = ids.filterNot(UriUtils.isValidUriFragment)
    if (invalidIds.isEmpty) Right(())
    else
      Left(OperationValidationError.InvalidArgument(s"service id is invalid: ${invalidIds.mkString("[", ", ", "]")}"))
  }

  protected def validateKeyIdLength[T <: PrismDIDOperation](
      config: Config
  )(operation: T, keyIdExtractor: KeyIdExtractor[T]): Either[OperationValidationError, Unit] = {
    val ids = keyIdExtractor(operation)
    val invalidIds = ids.filter(_.value.length > config.maxIdSize)
    if (invalidIds.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          s"public key id is too long: ${invalidIds.mkString("[", ", ", "]")}"
        )
      )
  }

  protected def validateServiceIdLength[T <: PrismDIDOperation](
      config: Config
  )(operation: T, serviceIdExtractor: ServiceIdExtractor[T]): Either[OperationValidationError, Unit] = {
    val ids = serviceIdExtractor(operation)
    val invalidIds = ids.filter(_.length > config.maxIdSize)
    if (invalidIds.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          s"service id is too long: ${invalidIds.mkString("[", ", ", "]")}"
        )
      )
  }

  protected def validateServiceTypeLength[T <: PrismDIDOperation](
      config: Config
  )(operation: T, serviceTypeExtractor: ServiceTypeExtractor[T]): Either[OperationValidationError, Unit] = {
    import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
    val serviceTypes = serviceTypeExtractor(operation)
    val invalidServiceTypes = serviceTypes.filter(_._2.toProto.length > config.maxServiceTypeSize)
    if (invalidServiceTypes.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          s"service type is too long: ${invalidServiceTypes.map(_._1).mkString("[", ", ", "]")}"
        )
      )
  }

  protected def validateServiceEndpointLength[T <: PrismDIDOperation](
      config: Config
  )(operation: T, serviceEndpointExtractor: ServiceEndpointExtractor[T]): Either[OperationValidationError, Unit] = {
    import org.hyperledger.identus.castor.core.model.ProtoModelHelper.*
    val serviceEndpoints = serviceEndpointExtractor(operation)
    val invalidServiceEndpoints = serviceEndpoints.filter(_._2.toProto.length > config.maxServiceEndpointSize)
    if (invalidServiceEndpoints.isEmpty) Right(())
    else
      Left(
        OperationValidationError.InvalidArgument(
          s"service endpoint is too long: ${invalidServiceEndpoints.map(_._1).mkString("[", ", ", "]")}"
        )
      )
  }

  protected def validatePreviousOperationHash[T <: PrismDIDOperation](
      operation: T,
      previousOperationHashExtractor: T => ArraySeq[Byte]
  ): Either[OperationValidationError, Unit] =
    if (previousOperationHashExtractor(operation).length == 32) Right(())
    else Left(OperationValidationError.InvalidArgument(s"previousOperationHash must have a size of 32 bytes"))

  /** @return true if a given uri is normalized */
  protected def isUriNormalized(uri: String): Boolean =
    UriUtils.normalizeUri(uri).contains(uri)

  protected def validateMasterKeyIsSecp256k1[T <: PrismDIDOperation](
      operation: T,
      keyDataExtractor: KeyDataExtractor[T]
  ): Either[OperationValidationError, Unit] = {
    val keys = keyDataExtractor(operation)
    val masterKeys = keys.collect { case (id, InternalKeyPurpose.Master, keyData) => id -> keyData }
    val vdrKeys = keys.collect { case (id, InternalKeyPurpose.VDRSigning, keyData) => id -> keyData }
    val invalidKeyIds = (masterKeys ++ vdrKeys)
      .filter { case (_, pk) =>
        pk match {
          case PublicKeyData.ECKeyData(EllipticCurve.SECP256K1, x, y) =>
            Apollo.default.secp256k1.publicKeyFromCoordinate(x.toByteArray, y.toByteArray).isFailure
          case PublicKeyData.ECCompressedKeyData(EllipticCurve.SECP256K1, data) =>
            Apollo.default.secp256k1.publicKeyFromEncoded(data.toByteArray).isFailure
          case _ => true // master key must be secp256k1
        }
      }
      .map(_._1)

    if (invalidKeyIds.isEmpty) Right(())
    else Left(OperationValidationError.InvalidMasterKeyData(invalidKeyIds))
  }

}
