package org.hyperledger.identus.mercury.model

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json
import zio.json.internal.Write

import java.util.Base64 as JBase64

/** @see
  *   data in attachments https://identity.foundation/didcomm-messaging/spec/#attachments
  */
sealed trait AttachmentData

final case class Header(kid: String)
object Header {
  given JsonEncoder[Header] = DeriveJsonEncoder.gen
  given JsonDecoder[Header] = DeriveJsonDecoder.gen
}

final case class Jws(header: Header, `protected`: String, signature: String)
object Jws {
  given JsonEncoder[Jws] = DeriveJsonEncoder.gen
  given JsonDecoder[Jws] = DeriveJsonDecoder.gen
}

final case class JwsData(base64: String, jws: Jws) extends AttachmentData
object JwsData {
  given JsonEncoder[JwsData] = DeriveJsonEncoder.gen
  given JsonDecoder[JwsData] = DeriveJsonDecoder.gen
}

final case class Base64(base64: String) extends AttachmentData
object Base64 {
  given JsonEncoder[Base64] = DeriveJsonEncoder.gen
  given JsonDecoder[Base64] = DeriveJsonDecoder.gen

}

final case class LinkData(links: Seq[String], hash: String) extends AttachmentData
object LinkData {
  given JsonEncoder[LinkData] = DeriveJsonEncoder.gen
  given JsonDecoder[LinkData] = DeriveJsonDecoder.gen

}

final case class JsonData(json: Json.Obj) extends AttachmentData
object JsonData {
  given JsonEncoder[JsonData] = DeriveJsonEncoder.gen
  given JsonDecoder[JsonData] = DeriveJsonDecoder.gen
}

object AttachmentData {
  given JsonEncoder[AttachmentData] = (a: AttachmentData, indent: Option[Int], out: Write) => {
    a match
      case data @ JwsData(_, _)  => JsonEncoder[JwsData].unsafeEncode(data, indent, out)
      case data @ Base64(_)      => JsonEncoder[Base64].unsafeEncode(data, indent, out)
      case data @ LinkData(_, _) => JsonEncoder[LinkData].unsafeEncode(data, indent, out)
      case data @ JsonData(_)    => JsonEncoder[JsonData].unsafeEncode(data, indent, out)
  }
  given JsonDecoder[AttachmentData] = JsonDecoder[Json].mapOrFail { json =>
    json
      .as[JwsData]
      .orElse(json.as[Base64])
      .orElse(json.as[LinkData])
      .orElse(json.as[JsonData])
      .left
      .map(error => s"Failed to decode AttachmentData: $error")
  }
}

/** https://identity.foundation/didcomm-messaging/spec/#attachments
  *
  * Example:
  * {{{
  *      {"id":"765d7fda-1b7f-4325-a828-f0c5523a6c19",
  *        "data":{"jws":null,"hash":null,"json":{
  *          "ciphertext":"c1kzLNSIZAuRILhaJg7KY3rx95czEy6VVBFVCN002OWw4D7bLK-ZuPTaTVEvwhfxlwkkwP1xuG9R22XMYlzLUIou6hx3gyeWgsUZS6OZkvuQlRqtPLh1yEZap24WH80cH4_DpX-srxsic5n7cEluuUlC0xvF5th-TLOZcmfUYySBPoKLzrSBcNIZH0GPyeePlnmLhqB5pi--mX3M17DcTpN5miQyJUNaRNv8hj3lKsKiRtGUCL_dzbV4UGRFEZ-fF-LWZtZfNco3LoEwIpX5099sqzc9ZrFi3GbgAWdyJUe075A5h89FgMHYdqdOrGp8HSQCoj4pRTv-SQJJ16APFo-u7GZOVd0kLeJvMBCQhwXlGT4DUpaeMV_52wrPj2FA9jDyqnzpUFPsb_IH7poc-VFrV32NJ6GLhzgwwc3k3vU_s16bHB3GeB-7_GgCu6hT","protected":"eyJlcGsiOnsia3R5IjoiT0tQIiwiY3J2IjoiWDI1NTE5IiwieCI6Ii1vY3RRYmFobFVYSF81aTQwOHRKN0Y3SmxHQU4yODY1Z1hvTHlHNTBNSHcifSwiYXB2IjoiYUptdEdPMHZIc3loQkpxVnRXU2dHNVN3YjMzVTJWdTRKTWhCS0ZiX0NHSSIsInNraWQiOiJkaWQ6ZXhhbXBsZTphbGljZSNrZXktYWdyZWVtZW50LTEiLCJhcHUiOiJaR2xrT21WNFlXMXdiR1U2WVd4cFkyVWphMlY1TFdGbmNtVmxiV1Z1ZEMweCIsInR5cCI6ImFwcGxpY2F0aW9uXC9kaWRjb21tLWVuY3J5cHRlZCtqc29uIiwiZW5jIjoiQTI1NkNCQy1IUzUxMiIsImFsZyI6IkVDREgtMVBVK0EyNTZLVyJ9",
  *          "recipients":[{
  *            "encrypted_key":"lWDvuQ37k6rotmnmOe7h1UF7Ao1RApL08aWmwjcijhlcH1_kvOvLYV8Dg2jXOZGsz2GsnM_W36JkLDFxM160g91ZsNAbz2rn",
  *            "header":{"kid":"did:example:bob#key-agreement-1"}
  *          }],"tag":"4pWPm37KdyRLZbWGIPJXqY9Mq55mKBHtSqBKUeHQHwc","iv":"THIgCu-Fq2aCiuwS-PcsfQ"
  *       }
  * }}}
  *
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/concepts/0017-attachments
  * @param id
  * @param media_type
  * @param data
  * @param filename
  * @param lastmod_time
  * @param byte_count
  * @param description
  */
final case class AttachmentDescriptor(
    id: String,
    media_type: Option[String] = None,
    data: AttachmentData = Base64(""),
    format: Option[String] = None,
    filename: Option[String] = None,
    lastmod_time: Option[Long] = None,
    byte_count: Option[Long] = None,
    description: Option[String] = None,
)

object AttachmentDescriptor {

  def buildBase64Attachment(
      id: String = java.util.UUID.randomUUID.toString,
      payload: Array[Byte],
      mediaType: Option[String] = None,
      format: Option[String] = None,
  ): AttachmentDescriptor = {
    val encoded = JBase64.getUrlEncoder.encodeToString(payload)
    AttachmentDescriptor(id, mediaType, Base64(encoded), format = format)
  }

  def buildJsonAttachment[A](
      id: String = java.util.UUID.randomUUID.toString,
      payload: A,
      mediaType: Option[String] = Some("application/json"),
      format: Option[String] = None,
  )(using JsonEncoder[A]): AttachmentDescriptor = {
    val jsonObject = payload.toJsonAST.toOption.flatMap(_.asObject).getOrElse(Json.Obj())
    AttachmentDescriptor(id, mediaType, JsonData(jsonObject), format = format) // use JsonData or Base64 by default?
  }

  given attachmentDescriptorEncoderV1: JsonEncoder[AttachmentDescriptor] =
    (a: AttachmentDescriptor, indent: Option[Int], out: Write) => {
      out.write("{")
      out.write("\"@id\":")
      JsonEncoder[String].unsafeEncode(a.id, indent, out)
      a.media_type.foreach { mt =>
        out.write(",\"mime_type\":")
        JsonEncoder[String].unsafeEncode(mt, indent, out)
      }
      out.write(",\"data\":")
      JsonEncoder[AttachmentData].unsafeEncode(a.data, indent, out)
      out.write("}")
    }

  given attachmentDescriptorEncoderV2: JsonEncoder[AttachmentDescriptor] = DeriveJsonEncoder.gen
  given JsonDecoder[AttachmentDescriptor] = DeriveJsonDecoder.gen

}
