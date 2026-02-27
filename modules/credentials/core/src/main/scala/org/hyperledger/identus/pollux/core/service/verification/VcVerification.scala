package org.hyperledger.identus.pollux.core.service.verification

import java.time.OffsetDateTime

sealed trait VcVerification

object VcVerification {
  case object SignatureVerification extends VcVerification

  case class IssuerIdentification(iss: String) extends VcVerification

  case class ExpirationCheck(dateTime: OffsetDateTime) extends VcVerification

  case class NotBeforeCheck(dateTime: OffsetDateTime) extends VcVerification

  case class AudienceCheck(aud: String) extends VcVerification

  case object SubjectVerification extends VcVerification

  case object IntegrityOfClaims extends VcVerification

  case object ComplianceWithStandards extends VcVerification

  case object RevocationCheck extends VcVerification

  case object AlgorithmVerification extends VcVerification

  case object SchemaCheck extends VcVerification

  case object SemanticCheckOfClaims extends VcVerification
}
