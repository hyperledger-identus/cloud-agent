package org.hyperledger.identus.mercury.protocol.reportproblem.v2

import org.hyperledger.identus.mercury.model.{DidId, Message, PIURI}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.internal.Write
object ReportProblem {

  /** {{{
    *  {
    *   "type": "https://didcomm.org/report-problem/2.0/problem-report",
    *   "id": "7c9de639-c51c-4d60-ab95-103fa613c805",
    *   "pthid": "1e513ad4-48c9-444e-9e7e-5b8b45c5e325",
    *   "ack": ["1e513ad4-48c9-444e-9e7e-5b8b45c5e325"],
    *   "body": {
    *     "code": "e.p.xfer.cant-use-endpoint",
    *     "comment": "Unable to use the {1} endpoint for {2}.",
    *     "args": [ "https://agents.r.us/inbox", "did:sov:C805sNYhMrjHiqZDTUASHg" ],
    *     "escalate_to": "mailto:admin@foo.org"
    *   }
    * }
    * }}}
    */

  def `type`: PIURI = "https://didcomm.org/report-problem/2.0/problem-report"

  case class Body(
      code: ProblemCode,
      comment: Option[String] = None,
      args: Option[Seq[String]] = None,
      escalate_to: Option[String] = None,
  )

  object Body {
    given JsonEncoder[Body] = DeriveJsonEncoder.gen
    given JsonDecoder[Body] = DeriveJsonDecoder.gen
  }

  def build(
      fromDID: DidId,
      toDID: DidId,
      pthid: String,
      code: ProblemCode,
      comment: Option[String] = None,
  ): ReportProblem = {
    val body = Body(code, comment = comment)
    ReportProblem(from = fromDID, to = toDID, pthid = Some(pthid), body = body)
  }

  given JsonEncoder[ReportProblem] = DeriveJsonEncoder.gen
  given JsonDecoder[ReportProblem] = DeriveJsonDecoder.gen

  def fromMessage(message: Message): ReportProblem =
    val body = message.body.as[ReportProblem.Body].toOption.get // TODO get
    ReportProblem(
      id = message.id,
      `type` = message.piuri,
      body = body,
      thid = message.thid,
      from = message.from.get, // TODO get
      pthid = message.pthid,
      ack = message.ack,
      to = {
        assert(message.to.length == 1, "The recipient is ambiguous. Need to have only 1 recipient") // TODO return error
        message.to.head
      },
    )

}

/** ProblemCode
  *
  * @see
  *   https://identity.foundation/didcomm-messaging/spec/#problem-codes
  */

opaque type ProblemCode = String

object ProblemCode {
  def apply(value: String): ProblemCode = {
    assert(true) // TODO regex to check value
    value
  }
  given JsonEncoder[ProblemCode] = (a: ProblemCode, indent: Option[Int], out: Write) =>
    JsonEncoder.string.unsafeEncode(a, indent, out)
  given JsonDecoder[ProblemCode] = JsonDecoder.string.map(ProblemCode(_))

}

extension (problemCode: ProblemCode) {
  def sorter: Sorter = problemCode.charAt(0) match
    case 'e' => Sorter.e // error semantics
    case 'w' => Sorter.w // warning semantics
  def scope: Scope = problemCode.charAt(2) match
    case 'p' => Scope.p // error semantics
    case 'm' => Scope.m // warning semantics
  def descriptors: Array[Descriptor] = problemCode.split('.').drop(2)
  def value = problemCode
}

/** @see
  *   https://identity.foundation/didcomm-messaging/spec/#sorter
  *
  * @param e:
  *   This problem clearly defeats the intentions of at least one of the parties. It is therefore an error. A situation
  *   with error semantics might be that a protocol requires payment, but a payment attempt was rejected.
  *
  * @param w:
  *   The consequences of this problem are not obvious to the reporter; evaluating its effects requires judgment from a
  *   human or from some other party or system. Thus, the message constitutes a warning from the sender’s perspective. A
  *   situation with warning semantics might be that a sender is only able to encrypt a message for some of the
  *   recipient’s keyAgreement keys instead of all of them (perhaps due to an imperfect overlap of supported crypto
  *   types). The sender in such a situation might not know whether the recipient considers this an error.
  */
enum Sorter {
  case e extends Sorter
  case w extends Sorter
}

/** @see
  *   https://identity.foundation/didcomm-messaging/spec/#scope
  *
  * @param p:
  *   The protocol within which the error occurs (and any co-protocols started by and depended on by the protocol) is
  *   abandoned or reset. In simple two-party request-response protocols, the p reset scope is common and appropriate.
  *   However, if a protocol is complex and long-lived, the p reset scope may be undesirable. Consider a situation where
  *   a protocol helps a person apply for college, and the problem code is e.p.payment-failed. With such a p reset
  *   scope, the entire apply-for-college workflow (collecting letters of recommendation, proving qualifications,
  *   filling out various forms) is abandoned when the payment fails. The p scope is probably too aggressive for such a
  *   situation.
  *
  * @param m:
  *   The error was triggered by the previous message on the thread; the scope is one message. The outcome is that the
  *   problematic message is rejected (has no effect). If the protocol is a chess game, and the problem code is
  *   e.m.invalid-move, then someone’s invalid move is rejected, and it is still their turn.
  *
  * A formal state name from the sender’s state machine in the active protocol. This means the error represented a
  * partial failure of the protocol, but the protocol as a whole is not abandoned. Instead, the sender uses the scope to
  * indicate what state it reverts to. If the protocol is one that helps a person apply for college, and the problem
  * code is e.get-pay-details.payment-failed, then the sender is saying that, because of the error, it is moving back to
  * the get-pay-details state in the larger workflow.
  */
enum Scope {
  case p extends Scope
  case m extends Scope
}

/** Descriptors After the sorter and the scope, problem codes consist of one or more descriptors. These are kebab-case
  * tokens separated by the . character, where the semantics get progressively more detailed reading left to right.
  * Senders of problem reports SHOULD include at least one descriptor in their problem code, and SHOULD use the most
  * specific descriptor they can. Recipients MAY specialize their reactions to problems in a very granular way, or MAY
  * examine only a prefix of a problem code.
  *
  * The following descriptor tokens are defined. They can be used by themselves, or as prefixes to more specific
  * descriptors. Additional descriptors — particularly more granular ones — may be defined in individual protocols.
  *
  * @see
  *   https://identity.foundation/didcomm-messaging/spec/#descriptors
  *
  * | Token        | Value of comment string                           |
  * |:-------------|:--------------------------------------------------|
  * | trust        | Failed to achieve required trust.                 |
  * | trust.crypto | Cryptographic operation failed.                   |
  * | xfer         | Unable to transport data.                         |
  * | did          | DID is unusable.                                  |
  * | msg          | Bad message.                                      |
  * | me           | Internal error.                                   |
  * | me.res       | A required resource is inadequate or unavailable. |
  * | req          | Circumstances don’t satisfy requirements.         |
  * | req.time     | Failed to satisfy timing constraints.             |
  * | legal        | Failed for legal reasons.                         |
  */
type Descriptor = String

/** ReportProblem
  *
  * @see
  *   https://identity.foundation/didcomm-messaging/spec/#problem-reports
  *
  * @param pthid
  *   REQUIRED. The value is the thid of the thread in which the problem occurred. (Thus, the problem report begins a
  *   new child thread, of which the triggering context is the parent. The parent context can react immediately to the
  *   problem, or can suspend progress while troubleshooting occurs.)
  *
  * @param ack
  *   OPTIONAL. It SHOULD be included if the problem in question was triggered directly by a preceding message.
  *   (Contrast problems arising from a timeout or a user deciding to cancel a transaction, which can arise independent
  *   of a preceding message. In such cases, ack MAY still be used, but there is no strong recommendation.)
  *
  * @param code
  *   REQUIRED. Deserves a rich explanation; see Problem Codes below.
  *
  * @param comment
  *   OPTIONAL but recommended. Contains human-friendly text describing the problem. If the field is present, the text
  *   MUST be statically associated with code, meaning that each time circumstances trigger a problem with the same
  *   code, the value of comment will be the same. This enables localization and cached lookups, and it has some
  *   cybersecurity benefits. The value of comment supports simple interpolation with args (see next), where args are
  *   referenced as {1}, {2}, and so forth.
  *
  * @param args
  *   OPTIONAL. Contains situation-specific values that are interpolated into the value of comment, providing extra
  *   detail for human readers. Each unique problem code has a definition for the args it takes. In this example,
  *   e.p.xfer.cant-use-endpoint apparently expects two values in args: the first is a URL and the second is a DID.
  *   Missing or null args MUST be replaced with a question mark character (?) during interpolation; extra args MUST be
  *   appended to the main text as comma-separated values.
  *
  * @param escalate_to
  *   OPTIONAL. Provides a URI where additional help on the issue can be received.
  */
final case class ReportProblem(
    `type`: PIURI = ReportProblem.`type`,
    id: String = java.util.UUID.randomUUID().toString,
    from: DidId,
    to: DidId,
    thid: Option[String] = None,
    pthid: Option[String],
    ack: Option[Seq[String]] = None,
    body: ReportProblem.Body,
) {
  assert(`type` == ReportProblem.`type`)
  assert(pthid.nonEmpty)

  def toMessage: Message = Message(
    id = this.id,
    `type` = this.`type`,
    from = Some(this.from),
    to = Seq(this.to),
    thid = this.thid,
    pthid = this.pthid,
    body = this.body.toJsonAST.toOption.flatMap(_.asObject).get,
    ack = ack
  )
}
