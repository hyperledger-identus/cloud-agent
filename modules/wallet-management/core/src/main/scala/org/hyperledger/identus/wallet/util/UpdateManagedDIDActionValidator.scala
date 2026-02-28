package org.hyperledger.identus.wallet.util

import org.hyperledger.identus.did.core.model.did.{EllipticCurve, VerificationRelationship}
import org.hyperledger.identus.wallet.model.UpdateManagedDIDAction
import org.hyperledger.identus.wallet.service.ManagedDIDService

object UpdateManagedDIDActionValidator {

  def validate(actions: Seq[UpdateManagedDIDAction]): Either[String, Unit] =
    for {
      _ <- validateReservedKeyId(actions)
      _ <- validateCurveUsage(actions)
    } yield ()

  private def validateReservedKeyId(actions: Seq[UpdateManagedDIDAction]): Either[String, Unit] = {
    val keyIds = actions.flatMap {
      case UpdateManagedDIDAction.AddKey(template)         => Some(template.id)
      case UpdateManagedDIDAction.AddInternalKey(template) => Some(template.id)
      case UpdateManagedDIDAction.RemoveKey(id)            => Some(id)
      case UpdateManagedDIDAction.RemoveInternalKey(id)    => Some(id)
      case UpdateManagedDIDAction.AddService(_)            => None
      case UpdateManagedDIDAction.RemoveService(_)         => None
      case UpdateManagedDIDAction.UpdateService(_)         => None
      case UpdateManagedDIDAction.PatchContext(_)          => None
    }
    val reservedKeyIds = keyIds.filter(id => ManagedDIDService.reservedKeyIds.contains(id))
    if (reservedKeyIds.nonEmpty)
      Left(s"DID update actions cannot contain reserved key name: ${reservedKeyIds.mkString("[", ", ", "]")}")
    else Right(())
  }

  private def validateCurveUsage(actions: Seq[UpdateManagedDIDAction]): Either[String, Unit] = {
    val ed25519AllowedUsage = Set(VerificationRelationship.Authentication, VerificationRelationship.AssertionMethod)
    val x25519AllowedUsage = Set(VerificationRelationship.KeyAgreement)
    val publicKeys = actions.collect { case UpdateManagedDIDAction.AddKey(template) => template }
    val disallowedKeys = publicKeys
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

}
