package org.hyperledger.identus.shared.messaging

import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}

import java.nio.charset.StandardCharsets
import java.util.UUID

case class WalletIdAndRecordId(walletId: UUID, recordId: UUID)

object WalletIdAndRecordId {
  given encoder: JsonEncoder[WalletIdAndRecordId] = DeriveJsonEncoder.gen[WalletIdAndRecordId]
  given decoder: JsonDecoder[WalletIdAndRecordId] = DeriveJsonDecoder.gen[WalletIdAndRecordId]
  given ser: Serde[WalletIdAndRecordId] = new Serde[WalletIdAndRecordId] {
    override def serialize(t: WalletIdAndRecordId): Array[Byte] = t.toJson.getBytes(StandardCharsets.UTF_8)
    override def deserialize(ba: Array[Byte]): WalletIdAndRecordId =
      new String(ba, StandardCharsets.UTF_8)
        .fromJson[WalletIdAndRecordId]
        .getOrElse(throw RuntimeException("Deserialization Error WalletIdAndRecordId"))
  }
}
