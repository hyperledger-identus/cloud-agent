package org.hyperledger.identus.mercury.protocol.coordinatemediation

import org.hyperledger.identus.mercury.model.PIURI
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class MediateRequest(
    id: String = java.util.UUID.randomUUID.toString(),
    `type`: PIURI = MediateRequest.`type`
) { assert(`type` == MediateRequest.`type`) }
object MediateRequest {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/mediate-request"
  given JsonEncoder[MediateRequest] = DeriveJsonEncoder.gen
  given JsonDecoder[MediateRequest] = DeriveJsonDecoder.gen
}

final case class MediateDeny(id: String, `type`: PIURI) { assert(`type` == MediateDeny.`type`) }
object MediateDeny {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/mediate-deny"
  given JsonEncoder[MediateDeny] = DeriveJsonEncoder.gen
  given JsonDecoder[MediateDeny] = DeriveJsonDecoder.gen
}

final case class MediateGrant(id: String, `type`: PIURI, body: MediateGrant.Body) {
  assert(`type` == MediateGrant.`type`)
}
object MediateGrant {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/mediate-grant"
  given JsonEncoder[MediateGrant] = DeriveJsonEncoder.gen
  given JsonDecoder[MediateGrant] = DeriveJsonDecoder.gen

  /** @param routing_did
    *   DID of the mediator where forwarded messages should be sent. The recipient may use this DID as an enpoint as
    *   explained in Using a DID as an endpoint section of the specification.
    *
    * FIXME https://github.com/roots-id/didcomm-mediator/issues/17
    */
  final case class Body(routing_did: String) // Seq[String])

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }
}

final case class KeylistUpdate(id: String, `type`: PIURI, body: KeylistUpdate.Body) {
  assert(`type` == KeylistUpdate.`type`)
}
object KeylistUpdate {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/keylist-update"
  given JsonEncoder[KeylistUpdate] = DeriveJsonEncoder.gen[KeylistUpdate]
  given JsonDecoder[KeylistUpdate] = DeriveJsonDecoder.gen
  given JsonEncoder[Update] = DeriveJsonEncoder.gen
  given JsonDecoder[Update] = DeriveJsonDecoder.gen

  final case class Body(updates: Seq[Update])

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  /** @param recipient_did
    *   DID subject of the update.
    * @param action
    *   one of add or remove.
    */
  final case class Update(recipient_did: String, action: Action)
  enum Action:
    case add extends Action
    case remove extends Action
  object Action {
    given JsonEncoder[Action] = DeriveJsonEncoder.gen
    given JsonDecoder[Action] = DeriveJsonDecoder.gen
  }
}

final case class KeylistResponse(id: String, `type`: PIURI, body: KeylistResponse.Body) {
  assert(`type` == KeylistResponse.`type`)
}
object KeylistResponse {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/keylist-update-response"
  given JsonEncoder[KeylistResponse] = DeriveJsonEncoder.gen
  given JsonDecoder[KeylistResponse] = DeriveJsonDecoder.gen
  given JsonEncoder[Update] = DeriveJsonEncoder.gen
  given JsonDecoder[Update] = DeriveJsonDecoder.gen

  final case class Body(updated: Seq[Update])

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  /** @param recipient_did
    *   DID subject of the update.
    * @param action
    *   one of add or remove.
    * @param result
    *   one of client_error, server_error, no_change, success; describes the resulting state of the keylist update.
    */
  final case class Update(recipient_did: String, action: Action, result: Result)
  enum Action:
    case add extends Action
    case remove extends Action
  object Action {
    given JsonEncoder[Action] = DeriveJsonEncoder.gen
    given JsonDecoder[Action] = DeriveJsonDecoder.gen
  }

  enum Result:
    case client_error extends Result
    case server_error extends Result
    case no_change extends Result
    case success extends Result
  object Result {
    given JsonEncoder[Result] = DeriveJsonEncoder.gen
    given JsonDecoder[Result] = DeriveJsonDecoder.gen
  }
}

final case class KeylistQuery(id: String, `type`: PIURI, body: KeylistQuery.Body) {
  assert(`type` == KeylistQuery.`type`)
}
object KeylistQuery {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/keylist-query"
  given JsonEncoder[KeylistQuery] = DeriveJsonEncoder.gen
  given JsonDecoder[KeylistQuery] = DeriveJsonDecoder.gen

  final case class Body(paginate: Option[Paginate] = None)
  final case class Paginate(limit: Int, offset: Int)
  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }
  object Paginate {
    given JsonEncoder[Paginate] = DeriveJsonEncoder.gen
    given JsonDecoder[Paginate] = DeriveJsonDecoder.gen
  }
}

final case class Keylist(id: String, `type`: PIURI, body: Keylist.Body) {
  assert(`type` == Keylist.`type`)
}
object Keylist {
  def `type`: PIURI = "https://didcomm.org/coordinate-mediation/2.0/keylist"
  given JsonEncoder[Keylist] = DeriveJsonEncoder.gen
  given JsonDecoder[Keylist] = DeriveJsonDecoder.gen
  given JsonEncoder[Key] = DeriveJsonEncoder.gen
  given JsonDecoder[Key] = DeriveJsonDecoder.gen

  final case class Body(keys: Seq[Key], pagination: Option[Pagination])
  final case class Key(recipient_did: String)
  final case class Pagination(count: Int, offset: Int, remaining: Int)

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }
  object Key {
    given JsonEncoder[Key] = DeriveJsonEncoder.gen
    given JsonDecoder[Key] = DeriveJsonDecoder.gen
  }
  object Pagination {
    given JsonEncoder[Pagination] = DeriveJsonEncoder.gen
    given JsonDecoder[Pagination] = DeriveJsonDecoder.gen
  }
}
