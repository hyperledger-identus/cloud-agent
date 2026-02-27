package org.hyperledger.identus.pollux.vc.jwt.revocation

import org.hyperledger.identus.pollux.vc.jwt.revocation.BitStringError.{DecodingError, EncodingError, IndexOutOfBounds}
import zio.{IO, UIO, ZIO}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import java.util.Base64

class BitString private (val bitSet: util.BitSet, val size: Int) {
  def setRevokedInPlace(index: Int, value: Boolean): IO[IndexOutOfBounds, Unit] =
    if (index >= size) ZIO.fail(IndexOutOfBounds(s"bitIndex >= $size: $index"))
    else ZIO.attempt(bitSet.set(index, value)).mapError(t => IndexOutOfBounds(t.getMessage))

  def isRevoked(index: Int): IO[IndexOutOfBounds, Boolean] =
    if (index >= size) ZIO.fail(IndexOutOfBounds(s"bitIndex >= $size: $index"))
    else ZIO.attempt(bitSet.get(index)).mapError(t => IndexOutOfBounds(t.getMessage))

  def revokedCount(): UIO[Int] = ZIO.succeed(bitSet.stream().count().toInt)

  def encoded: IO[EncodingError, String] = {

    for {
      bitSetByteArray <- ZIO.succeed(bitSet.toByteArray.map(x => BitString.reverseBits(x).toByte))

      /*
      This is where the size constructor parameter comes into play (i.e. the initial bitstring size requested by the user).
      Interestingly, the underlying 'bitSet.toByteArray()' method only returns the byte array that are 'in use', which means the bytes needed to hold the current bits that are set to true.
      E.g. Calling toByteArray on a BitSet of size 64, where all bits are false, will return an empty array. The same BitSet with the fourth bit set to true will return 1 byte. And so on...
      So, the paddingByteArray is used to fill the gap between what BitSet returns and what was requested by the user.
      If the BitString size is 131.072 and no VC is revoked, the final encoding (as per the spec) should account for all bits, and no only those that are revoked.
      The (x + 7) / 8) is used to calculate the number of bytes needed to store a bit array of size x.
       */
      paddingByteArray = new Array[Byte](((size + 7) / 8) - bitSetByteArray.length)
      baos = new ByteArrayOutputStream()
      _ <- (for {
        gzipOutputStream <- ZIO.attempt(new GZIPOutputStream(baos))
        _ <- ZIO.attempt(gzipOutputStream.write(bitSetByteArray))
        _ <- ZIO.attempt(gzipOutputStream.write(paddingByteArray))
        _ <- ZIO.attempt(gzipOutputStream.close())
      } yield ()).mapError(t => EncodingError(t.getMessage))
    } yield {
      Base64.getUrlEncoder.encodeToString(baos.toByteArray)
    }
  }
}

object BitString {
  /*
   The minimum size of the bit string according to the VC Status List 2021 specification.
   As per the spec "... a minimum revocation bitstring of 131.072, or 16KB uncompressed... is enough to give holders an adequate amount of herd privacy"
   Cf. https://www.w3.org/TR/vc-status-list/#revocation-bitstring-length
   */
  val MIN_SL2021_SIZE: Int = 131072

  private def reverseBits(b: Int): Int = {
    var result: Int = 0
    for (i <- 0 until 8) {
      val bit = (b >> i) & 1
      result = (result << 1) | bit
    }
    result
  }

  def getInstance(): IO[BitStringError, BitString] = getInstance(MIN_SL2021_SIZE)

  def getInstance(size: Int): IO[BitStringError, BitString] = {
    if (size % 8 != 0) ZIO.fail(BitStringError.InvalidSize("Bit string size should be a multiple of 8"))
    else ZIO.succeed(BitString(new util.BitSet(size), size))
  }

  def valueOf(b64Value: String): IO[DecodingError, BitString] = {
    for {
      ba <- ZIO.attempt(Base64.getUrlDecoder.decode(b64Value)).mapError(t => DecodingError(t.getMessage))
    } yield {
      val bais = new ByteArrayInputStream(ba)
      val gzipInputStream = new GZIPInputStream(bais)
      val byteArray = gzipInputStream.readAllBytes().map(x => BitString.reverseBits(x).toByte)
      BitString(util.BitSet.valueOf(byteArray), byteArray.length * 8)
    }
  }
}

sealed trait BitStringError

object BitStringError {
  final case class InvalidSize(message: String) extends BitStringError
  final case class EncodingError(message: String) extends BitStringError
  final case class DecodingError(message: String) extends BitStringError
  final case class IndexOutOfBounds(message: String) extends BitStringError
}
