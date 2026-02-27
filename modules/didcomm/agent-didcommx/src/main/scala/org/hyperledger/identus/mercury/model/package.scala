package org.hyperledger.identus.mercury.model

import org.didcommx.didcomm.model.*

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

final case class EncryptedMessageImp(private val msg: PackEncryptedResult) extends EncryptedMessage {
  def string: String = msg.getPackedMessage
}

final case class SignedMessageImp(private val msg: PackSignedResult) extends SignedMesage {
  def string: String = msg.getPackedMessage
}

final case class UnpackMessageImp(private val msg: UnpackResult) extends UnpackMessage {
  def message = UnpackMessageImp.message(msg.getMessage)
}

object UnpackMessageImp {
  def message(msg: org.didcommx.didcomm.message.Message): Message = {

    val thisbody = JsonUtilsForDidCommx.fromJavaMapToJson(msg.getBody)

    val attachments: Seq[AttachmentDescriptor] = Option(msg.getAttachments()).toSeq
      .flatMap(_.asScala.toSeq)
      .map(e => e) // using the given Conversion

    Message(
      `type` = msg.getType(),
      from = Option(msg.getFrom()).map(DidId(_)),
      to = Option(msg.getTo()).toSeq
        .map(_.asScala)
        .flatMap(_.toSeq.map(e => DidId(e))),
      body = thisbody,
      id = msg.getId(),
      createdTime = { Option(msg.getCreatedTime()): Option[java.lang.Long] }.map(i => i),
      expiresTimePlus = { Option(msg.getExpiresTime()): Option[java.lang.Long] }.map(i => i),
      attachments = Option(attachments).filter(!_.isEmpty),
      thid = Option(msg.getThid()).filter(!_.isEmpty()),
      pthid = Option(msg.getPthid()).filter(!_.isEmpty()),
      ack = Option(msg.getAck()).map(Seq(_)),
      pleaseAck = Option(msg.getPleaseAck())
        .flatMap {
          // https://identity.foundation/didcomm-messaging/spec/#acks
          case java.lang.Boolean.TRUE =>
            Some(Seq.empty) // NOTE lib limitation the field pleaseAck MUST be a Array of string
          case java.lang.Boolean.FALSE => None
        }
    )
  }
}
