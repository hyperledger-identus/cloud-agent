package org.hyperledger.identus.api.http.codec

import org.hyperledger.identus.api.http.model.Order
import org.hyperledger.identus.api.http.model.Order.Direction
import sttp.tapir.{Codec, DecodeResult}
import sttp.tapir.Codec.PlainCodec

import java.util.Base64

object OrderCodec {
  implicit def orderCodec: PlainCodec[Order] = {
    def decode(s: String): DecodeResult[Order] =
      try {
        val s2 = new String(Base64.getDecoder.decode(s))
        val order = s2.split(".", 2) match {
          case Array()                 => Order.empty
          case Array(field)            => Order(field)
          case Array(field, "")        => Order(field)
          case Array(field, direction) =>
            Order(field, Some(Direction.valueOf(direction)))
          case _ => sys.error("impossible")
        }
        DecodeResult.Value(order)
      } catch {
        case e: Exception => DecodeResult.Error(s, e)
      }

    def encode(order: Order): String =
      Base64.getEncoder
        .encodeToString(
          s"${order.field}.${order.direction.getOrElse(Order.DefaultDirection).toString}"
            .getBytes("UTF-8")
        )

    Codec.string.mapDecode(decode)(encode)
  }
}
