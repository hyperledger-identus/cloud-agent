package org.hyperledger.identus.shared.messaging

import org.hyperledger.identus.shared.models.WalletId

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.util.UUID

case class ByteArrayWrapper(ba: Array[Byte])

trait Serde[T] {
  def serialize(t: T): Array[Byte]
  def deserialize(ba: Array[Byte]): T
}

object Serde {
  given byteArraySerde: Serde[ByteArrayWrapper] = new Serde[ByteArrayWrapper] {
    override def serialize(t: ByteArrayWrapper): Array[Byte] = t.ba
    override def deserialize(ba: Array[Byte]): ByteArrayWrapper = ByteArrayWrapper(ba)
  }

  given stringSerde: Serde[String] = new Serde[String] {
    override def serialize(t: String): Array[Byte] = t.getBytes()
    override def deserialize(ba: Array[Byte]): String = new String(ba, StandardCharsets.UTF_8)
  }

  given intSerde: Serde[Int] = new Serde[Int] {
    override def serialize(t: Int): Array[Byte] = {
      val buffer = java.nio.ByteBuffer.allocate(4)
      buffer.putInt(t)
      buffer.array()
    }
    override def deserialize(ba: Array[Byte]): Int = ByteBuffer.wrap(ba).getInt()
  }

  given uuidSerde: Serde[UUID] = new Serde[UUID] {
    override def serialize(t: UUID): Array[Byte] = {
      val buffer = java.nio.ByteBuffer.allocate(16)
      buffer.putLong(t.getMostSignificantBits)
      buffer.putLong(t.getLeastSignificantBits)
      buffer.array()
    }
    override def deserialize(ba: Array[Byte]): UUID = {
      val byteBuffer = ByteBuffer.wrap(ba)
      val high = byteBuffer.getLong
      val low = byteBuffer.getLong
      new UUID(high, low)
    }
  }

  given walletIdSerde(using uuidSerde: Serde[UUID]): Serde[WalletId] = new Serde[WalletId] {
    override def serialize(w: WalletId): Array[Byte] = uuidSerde.serialize(w.toUUID)
    override def deserialize(ba: Array[Byte]): WalletId = WalletId.fromUUID(uuidSerde.deserialize(ba))
  }
}
