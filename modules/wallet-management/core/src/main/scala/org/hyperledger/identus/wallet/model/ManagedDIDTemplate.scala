package org.hyperledger.identus.wallet.model

import org.hyperledger.identus.did.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  Service,
  ServiceEndpoint,
  ServiceType,
  VerificationRelationship
}

final case class ManagedDIDTemplate(
    publicKeys: Seq[DIDPublicKeyTemplate],
    internalKeys: Seq[ManagedInternalDIDKeyTemplate] = Seq.empty,
    services: Seq[Service],
    contexts: Seq[String]
)

final case class DIDPublicKeyTemplate(
    id: String,
    purpose: VerificationRelationship,
    curve: EllipticCurve
)

final case class ManagedInternalDIDKeyTemplate(
    id: String,
    purpose: InternalKeyPurpose
)

sealed trait UpdateManagedDIDAction

object UpdateManagedDIDAction {
  final case class AddKey(template: DIDPublicKeyTemplate) extends UpdateManagedDIDAction
  final case class AddInternalKey(template: ManagedInternalDIDKeyTemplate) extends UpdateManagedDIDAction
  final case class RemoveKey(id: String) extends UpdateManagedDIDAction

  /** Remove an internal key (only VDR purpose is currently supported). */
  final case class RemoveInternalKey(id: String) extends UpdateManagedDIDAction
  final case class AddService(service: Service) extends UpdateManagedDIDAction
  final case class RemoveService(id: String) extends UpdateManagedDIDAction
  final case class UpdateService(patch: UpdateServicePatch) extends UpdateManagedDIDAction
  final case class PatchContext(context: Seq[String]) extends UpdateManagedDIDAction
}

final case class UpdateServicePatch(
    id: String,
    serviceType: Option[ServiceType],
    serviceEndpoints: Option[ServiceEndpoint]
)
