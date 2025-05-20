package org.hyperledger.identus.mercury.model

import org.didcommx.didcomm.message.{Attachment as XAttachment, MessageBuilder}
import org.didcommx.didcomm.model.*
import zio.json.ast.Json

import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

given Conversion[PackEncryptedResult, EncryptedMessage] with {
  def apply(msg: PackEncryptedResult): EncryptedMessage = EncryptedMessageImp(msg)
}

given Conversion[Message, org.didcommx.didcomm.message.Message] with {
  def apply(msg: Message): org.didcommx.didcomm.message.Message = {
    val attachmentsAux = msg.attachments.toSeq.flatten.map { e => e: XAttachment } // cast
    val aux = new MessageBuilder(
      msg.id,
      // msg.body.toMap.asJava,
      JsonUtilsForDidCommx.fromJsonToJavaMap(msg.body),
      msg.piuri
    )

    msg.createdTime.foreach(e => aux.createdTime(e))

    (msg.createdTime, msg.expiresTimePlus) match {
      case (Some(t), Some(delta)) => aux.expiresTime(t + delta)
      case _                      => // nothing
    }
    aux.attachments(attachmentsAux.toList.asJava)

    // .customHeader("return_route", "all")

    msg.from.foreach(did => aux.from(did.value))
    msg.to.foreach(did => aux.to(Seq(did.value).asJava))

    msg.pleaseAck.foreach { seq => // https://identity.foundation/didcomm-messaging/spec/#acks
      aux.pleaseAck(true) // NOTE lib limitation the field pleaseAck MUST be a Array of string
    }
    msg.ack.flatMap(_.headOption).foreach(str => aux.ack(str)) // NOTE: headOption because DidCommx only support one ack
    msg.thid.foreach(str => aux.thid(str))
    msg.pthid.foreach(str => aux.pthid(str))
    aux.build()
  }

}

def json2Map(json: Json): Any = json match {
  case e @ Json.Arr(_)  => e.asArray.get.toList.map(j => json2Map(j)).asJava
  case e @ Json.Bool(_) => e.asBoolean.get
  case e @ Json.Num(_)  => e.asNumber.map(_.value).get
  case e @ Json.Obj(_)  => e.asObject.get.toMap.view.mapValues(json2Map).toMap.asJava
  case e @ Json.Str(_)  => e.asString.get
  case e @ Json.Null    => null
}

def mapValueToJson(obj: java.lang.Object): Json = {
  obj match {
    case null                 => Json.Null
    case b: java.lang.Boolean => Json.Bool(b)
    case i: java.lang.Integer => Json.Num(i)
    case d: java.lang.Double  => Json.Num(d)
    case l: java.lang.Long    => Json.Num(l)
    case s: java.lang.String  => Json.Str(String.valueOf(s))
    case array: com.nimbusds.jose.shaded.json.JSONArray => {
      Json.Arr(array.iterator().asScala.map(mapValueToJson).toList: _*)
    }
    case joseObject: com.nimbusds.jose.shaded.json.JSONObject =>
      Json.Obj(
        joseObject
          .asInstanceOf[java.util.Map[String, Object]]
          .asScala
          .toMap
          .view
          .mapValues(mapValueToJson)
          .toArray: _*
      )
  }
}

given Conversion[AttachmentDescriptor, XAttachment] with {
  def apply(attachment: AttachmentDescriptor): XAttachment = {
    val id = attachment.id

    val data =
      attachment.data match {
        case JsonData(d) => {
          val hack: Map[String, ?] = d.toMap.view.mapValues(json2Map).toMap
          val hack2 = Map[String, Any]("jws" -> null, "hash" -> null, "json" -> hack.asJava) // OMG
          XAttachment.Data.Companion.parse(hack2.asJava)
        }
        case LinkData(links, hash) => new XAttachment.Data.Links(links.asJava, hash, null)
        case Base64(d)             => new XAttachment.Data.Base64(d, null, null)
        case _                     => FeatureNotImplemented
      }

    new XAttachment.Builder(id, data)
      .format(attachment.format match
        case Some(format) => format
        case None         => null)
      .build()
  }
}

given Conversion[XAttachment, AttachmentDescriptor] with {
  def apply(attachment: XAttachment): AttachmentDescriptor = {
    val data: AttachmentData = attachment.getData().toJSONObject.asScala.toMap match {
      case e if e contains ("json") =>
        val aux = e("json")
        val x = aux.asInstanceOf[java.util.Map[String, Object]].asScala.toMap.view.mapValues(mapValueToJson)
        JsonData(Json.Obj(x.toArray: _*))
      case e if e contains ("base64") =>
        val tmp = e("base64").asInstanceOf[String] // ...
        Base64(tmp)
      case e if e contains ("links") =>
        val aux = e("links")
        val list = aux.asInstanceOf[java.util.AbstractList[String]]
        val linksSeq = list.iterator().asScala.toSeq
        LinkData(linksSeq, hash = attachment.getData().getHash())
      case e if e contains ("jws") => FeatureNotImplemented
    }

    AttachmentDescriptor(
      id = attachment.getId(),
      media_type = Option(attachment.getMediaType()),
      data = data,
      filename = Option(attachment.getFilename()),
      lastmod_time = Option(attachment.getLastModTime()),
      byte_count = Option(attachment.getByteCount()),
      description = Option(attachment.getDescription()),
      format = Option(attachment.getFormat)
    )
  }
}

given Conversion[PackSignedResult, SignedMesage] with {
  def apply(msg: PackSignedResult): SignedMesage = SignedMessageImp(msg)
}

given Conversion[UnpackResult, UnpackMessage] with {
  def apply(msg: UnpackResult): UnpackMessage = UnpackMessageImp(msg)
}
