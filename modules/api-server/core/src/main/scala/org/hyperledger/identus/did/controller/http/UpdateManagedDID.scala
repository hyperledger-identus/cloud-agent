package org.hyperledger.identus.did.controller.http

import org.hyperledger.identus.wallet.model as walletDomain
import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.did.core.model.did as castorDomain
import org.hyperledger.identus.shared.utils.Traverse.*
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import scala.language.implicitConversions

final case class UpdateManagedDIDRequest(
    actions: Seq[UpdateManagedDIDRequestAction]
)

object UpdateManagedDIDRequest {
  given JsonEncoder[UpdateManagedDIDRequest] = DeriveJsonEncoder.gen[UpdateManagedDIDRequest]
  given JsonDecoder[UpdateManagedDIDRequest] = DeriveJsonDecoder.gen[UpdateManagedDIDRequest]
  given Schema[UpdateManagedDIDRequest] = Schema.derived
}

enum ActionType {
  case ADD_KEY extends ActionType
  case ADD_INTERNAL_KEY extends ActionType
  case REMOVE_INTERNAL_KEY extends ActionType
  case REMOVE_KEY extends ActionType
  case ADD_SERVICE extends ActionType
  case REMOVE_SERVICE extends ActionType
  case UPDATE_SERVICE extends ActionType
  case PATCH_CONTEXT extends ActionType
}

object ActionType {
  given JsonEncoder[ActionType] = JsonEncoder[String].contramap(_.toString)
  given JsonDecoder[ActionType] = JsonDecoder[String].mapOrFail { s =>
    ActionType.values.find(_.toString == s).toRight(s"Unknown action type: $s")
  }
  given Schema[ActionType] = Schema.derivedEnumeration.defaultStringBased
}

@description(UpdateManagedDIDRequestAction.annotations.description)
final case class UpdateManagedDIDRequestAction(
    actionType: ActionType,
    addKey: Option[ManagedDIDKeyTemplate] = None,
    addInternalKey: Option[ManagedDIDInternalKeyTemplate] = None,
    removeInternalKey: Option[RemoveEntryById] = None,
    removeKey: Option[RemoveEntryById] = None,
    addService: Option[Service] = None,
    removeService: Option[RemoveEntryById] = None,
    updateService: Option[UpdateManagedDIDServiceAction] = None,
    patchContext: Option[PatchContextAction] = None
)

object UpdateManagedDIDRequestAction {
  object annotations {
    val description =
      """A list of actions to perform on DID document.
        |The field `addKey`, `removeKey`, `addService`, `removeService`, `updateService`, `patchContext` must corresponds to
        |the `actionType` specified. For example, `addKey` must be present when `actionType` is `ADD_KEY`.""".stripMargin
  }

  given JsonEncoder[UpdateManagedDIDRequestAction] = DeriveJsonEncoder.gen[UpdateManagedDIDRequestAction]
  given JsonDecoder[UpdateManagedDIDRequestAction] = DeriveJsonDecoder.gen[UpdateManagedDIDRequestAction]
  given Schema[UpdateManagedDIDRequestAction] = Schema.derived

  extension (action: UpdateManagedDIDRequestAction) {
    def toDomain: Either[String, walletDomain.UpdateManagedDIDAction] = {
      import walletDomain.UpdateManagedDIDAction.*
      action.actionType match {
        case ActionType.ADD_KEY =>
          action.addKey
            .toRight("addKey property is missing from action type ADD_KEY")
            .map(template => AddKey(template))
        case ActionType.ADD_INTERNAL_KEY =>
          action.addInternalKey
            .toRight("addInternalKey property is missing from action type ADD_INTERNAL_KEY")
            .flatMap(_.toDomain)
            .map(template => AddInternalKey(template))
        case ActionType.REMOVE_INTERNAL_KEY =>
          action.removeInternalKey
            .toRight("removeInternalKey property is missing from action type REMOVE_INTERNAL_KEY")
            .map(i => RemoveInternalKey(i.id))
        case ActionType.REMOVE_KEY =>
          action.removeKey
            .toRight("removeKey property is missing from action type REMOVE_KEY")
            .map(i => RemoveKey(i.id))
        case ActionType.ADD_SERVICE =>
          action.addService
            .toRight("addservice property is missing from action type ADD_SERVICE")
            .flatMap(_.toDomain)
            .map(s => AddService(s))
        case ActionType.REMOVE_SERVICE =>
          action.removeService
            .toRight("removeService property is missing from action type REMOVE_SERVICE")
            .map(i => RemoveService(i.id))
        case ActionType.UPDATE_SERVICE =>
          action.updateService
            .toRight("updateService property is missing from action type UPDATE_SERVICE")
            .flatMap(_.toDomain)
            .map(s => UpdateService(s))
        case ActionType.PATCH_CONTEXT =>
          action.patchContext
            .toRight("patchContext property is missing from action type PATCH_CONTEXT")
            .map(i => PatchContext(i.contexts.map(_.value)))
      }
    }
  }
}

final case class RemoveEntryById(id: String)

object RemoveEntryById {
  object annotations {
    object id
        extends Annotation[String](
          description = "The id of the entry to remove",
          example = "key-1"
        )
  }

  given JsonEncoder[RemoveEntryById] = DeriveJsonEncoder.gen[RemoveEntryById]
  given JsonDecoder[RemoveEntryById] = DeriveJsonDecoder.gen[RemoveEntryById]
  given Schema[RemoveEntryById] = Schema.derived
}

@description("A patch to existing Service. 'type' and 'serviceEndpoint' cannot both be empty.")
final case class UpdateManagedDIDServiceAction(
    @description(UpdateManagedDIDServiceAction.annotations.id.description)
    @encodedExample(UpdateManagedDIDServiceAction.annotations.id.example)
    id: String,
    @description(UpdateManagedDIDServiceAction.annotations.`type`.description)
    @encodedExample(UpdateManagedDIDServiceAction.annotations.`type`.example)
    `type`: Option[ServiceType] = None,
    serviceEndpoint: Option[ServiceEndpoint] = None
)

object UpdateManagedDIDServiceAction {
  object annotations {
    object id
        extends Annotation[String](
          description = "The id of the service to update",
          example = "service-1"
        )

    object `type`
        extends Annotation[String](
          description = "The type of the service",
          example = "LinkedDomains"
        )
  }

  given JsonEncoder[UpdateManagedDIDServiceAction] = DeriveJsonEncoder.gen[UpdateManagedDIDServiceAction]
  given JsonDecoder[UpdateManagedDIDServiceAction] = DeriveJsonDecoder.gen[UpdateManagedDIDServiceAction]
  given Schema[UpdateManagedDIDServiceAction] = Schema.derived

  extension (servicePatch: UpdateManagedDIDServiceAction) {
    def toDomain: Either[String, walletDomain.UpdateServicePatch] =
      for {
        serviceType <- servicePatch.`type`.fold[Either[String, Option[castorDomain.ServiceType]]](Right(None))(s =>
          s.toDomain.map(Some(_))
        )
        serviceEndpoint <- servicePatch.serviceEndpoint.fold[Either[String, Option[castorDomain.ServiceEndpoint]]](
          Right(None)
        )(endpoint => endpoint.toDomain.map(Some(_)))
      } yield walletDomain.UpdateServicePatch(
        id = servicePatch.id,
        serviceType = serviceType,
        serviceEndpoints = serviceEndpoint.map(_.normalize())
      )
  }
}

final case class PatchContextAction(contexts: Seq[Context])

object PatchContextAction {
  given JsonEncoder[PatchContextAction] = DeriveJsonEncoder.gen[PatchContextAction]
  given JsonDecoder[PatchContextAction] = DeriveJsonDecoder.gen[PatchContextAction]
  given Schema[PatchContextAction] = Schema.derived
}
