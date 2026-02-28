package org.hyperledger.identus.wallet.util

import org.hyperledger.identus.did.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  Service as DidDocumentService,
  VerificationRelationship
}
import org.hyperledger.identus.wallet.model.ManagedDIDTemplate
import org.hyperledger.identus.wallet.service.ManagedDIDService

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
    val allowedByCurve: Map[EllipticCurve, Set[VerificationRelationship]] = Map(
      EllipticCurve.ED25519 -> Set(
        VerificationRelationship.Authentication,
        VerificationRelationship.AssertionMethod
      ),
      EllipticCurve.X25519 -> Set(VerificationRelationship.KeyAgreement),
      // SECP256K1 should not be used for key-agreement
      EllipticCurve.SECP256K1 -> Set(
        VerificationRelationship.Authentication,
        VerificationRelationship.AssertionMethod,
        VerificationRelationship.CapabilityDelegation,
        VerificationRelationship.CapabilityInvocation
      )
    )

    val disallowedKeys = template.publicKeys.collect {
      case k if allowedByCurve.get(k.curve).exists(!_.contains(k.purpose)) => k.id
    }

    if (disallowedKeys.isEmpty) Right(())
    else {
      val messages = allowedByCurve
        .map { case (curve, purposes) => s"$curve -> ${purposes.mkString("[", ", ", "]")}" }
        .mkString("; ")
      Left(
        s"Invalid key purpose for key(s) ${disallowedKeys.mkString("[", ", ", "]")}. " +
          s"Allowed combinations: $messages"
      )
    }
  }

  private def validateInternalKeyPurpose(template: ManagedDIDTemplate): Either[String, Unit] = {
    val unsupported = template.internalKeys.filterNot(_.purpose == InternalKeyPurpose.VDR).map(_.id)
    if (unsupported.isEmpty) Right(())
    else Left(s"Unsupported internal key purpose for key(s): ${unsupported.mkString("[", ", ", "]")}")
  }

}
