package org.hyperledger.identus.agent.walletapi.util

import org.hyperledger.identus.agent.walletapi.model.ManagedDIDTemplate
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.castor.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  Service as DidDocumentService,
  VerificationRelationship
}

object ManagedDIDTemplateValidator {

  def validate(
      template: ManagedDIDTemplate,
      defaultDidDocumentServices: Set[DidDocumentService] = Set.empty
  ): Either[String, Unit] =
    for {
      _ <- validateReservedKeyId(template)
      _ <- validateUniqueKeyIds(template)
      _ <- validateCurveUsage(template)
      _ <- validateInternalKeyPurpose(template)
      _ <- validatePresenceOfDefaultDidServices(template.services, defaultDidDocumentServices)
    } yield ()

  private def validatePresenceOfDefaultDidServices(
      services: Seq[DidDocumentService],
      defaultDidDocumentServices: Set[DidDocumentService]
  ): Either[String, Unit] = {

    services.map(_.id).intersect(defaultDidDocumentServices.toSeq.map(_.id)) match {
      case Nil => Right(())
      case x   => Left(s"Default DID services cannot be overridden: ${x.mkString("[", ", ", "]")}")
    }
  }

  private def validateReservedKeyId(template: ManagedDIDTemplate): Either[String, Unit] = {
    val keyIds = template.publicKeys.map(_.id) ++ template.internalKeys.map(_.id)
    val reservedKeyIds = keyIds.filter(id => ManagedDIDService.reservedKeyIds.contains(id))
    if (reservedKeyIds.nonEmpty)
      Left(s"DID template cannot contain reserved key name: ${reservedKeyIds.mkString("[", ", ", "]")}")
    else Right(())
  }

  private def validateUniqueKeyIds(template: ManagedDIDTemplate): Either[String, Unit] = {
    val ids = template.publicKeys.map(_.id) ++ template.internalKeys.map(_.id)
    val duplicates = ids.groupBy(identity).collect { case (id, occurrences) if occurrences.size > 1 => id }.toSeq
    if (duplicates.nonEmpty)
      Left(s"DID template cannot contain duplicated key ids: ${duplicates.mkString("[", ", ", "]")}")
    else Right(())
  }

  private def validateCurveUsage(template: ManagedDIDTemplate): Either[String, Unit] = {
    val ed25519AllowedUsage = Set(VerificationRelationship.Authentication, VerificationRelationship.AssertionMethod)
    val x25519AllowedUsage = Set(VerificationRelationship.KeyAgreement)
    val disallowedKeys = template.publicKeys
      .filter { k =>
        k.curve match {
          case EllipticCurve.ED25519 => !ed25519AllowedUsage.contains(k.purpose)
          case EllipticCurve.X25519  => !x25519AllowedUsage.contains(k.purpose)
          case _                     => false
        }
      }
      .map(_.id)

    if (disallowedKeys.isEmpty) Right(())
    else
      Left(
        s"Invalid key purpose for key ${disallowedKeys.mkString("[", ", ", "]")}. " +
          s"Ed25519 must be used in ${ed25519AllowedUsage.mkString("[", ", ", "]")}. " +
          s"X25519 must be used in ${x25519AllowedUsage.mkString("[", ", ", "]")}"
      )
  }

  private def validateInternalKeyPurpose(template: ManagedDIDTemplate): Either[String, Unit] = {
    val unsupported = template.internalKeys.filterNot(_.purpose == InternalKeyPurpose.VDR).map(_.id)
    if (unsupported.isEmpty) Right(())
    else Left(s"Unsupported internal key purpose for key(s): ${unsupported.mkString("[", ", ", "]")}")
  }

}
