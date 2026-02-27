package org.hyperledger.identus.pollux.vc.jwt.revocation

import org.hyperledger.identus.pollux.vc.jwt.revocation.BitStringError.{IndexOutOfBounds, InvalidSize}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object BitStringSpec extends ZIOSpecDefault {

  private val MIN_SIZE_SL2021_WITH_NO_REVOCATION =
    "H4sIAAAAAAAA_-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA"

  override def spec = suite("Revocation BitString test suite")(
    test("A default bit string instance has zero revoked items") {
      for {
        bitString <- BitString.getInstance()
        revokedCount <- bitString.revokedCount()
      } yield {
        assertTrue(revokedCount == 0)
      }
    },
    test("A default bit string instance is correctly encoded/decoded") {
      for {
        initialBS <- BitString.getInstance()
        encodedBS <- initialBS.encoded
        decodedBS <- BitString.valueOf(encodedBS)
        decodedRevokedCount <- decodedBS.revokedCount()
        reencodedBS <- decodedBS.encoded
      } yield {
        assertTrue(encodedBS == MIN_SIZE_SL2021_WITH_NO_REVOCATION)
        && assertTrue(decodedBS.size == BitString.MIN_SL2021_SIZE)
        && assertTrue(decodedBS.size == initialBS.size)
        && assertTrue(decodedRevokedCount == 0)
        && assertTrue(encodedBS == reencodedBS)
      }
    },
    test("A bit string with custom size and revoked items is correctly encoded") {
      for {
        initialBS <- BitString.getInstance(800)
        _ <- initialBS.setRevokedInPlace(753, true)
        _ <- initialBS.setRevokedInPlace(45, true)
        encodedBS <- initialBS.encoded
        decodedBS <- BitString.valueOf(encodedBS)
        decodedRevokedCount <- decodedBS.revokedCount()
        isDecodedRevoked1 <- decodedBS.isRevoked(753)
        isDecodedRevoked2 <- decodedBS.isRevoked(45)
        isDecodedRevoked3 <- decodedBS.isRevoked(32)
      } yield {
        assertTrue(decodedRevokedCount == 2)
        && assertTrue(isDecodedRevoked1)
        && assertTrue(isDecodedRevoked2)
        && assertTrue(!isDecodedRevoked3)
      }
    },
    test("A custom bit string size is a multiple of 8") {
      for {
        bitString <- BitString.getInstance(31).exit
      } yield assert(bitString)(failsWithA[InvalidSize])
    },
    test("The first index is 0 and last index at 'size - 1'") {
      for {
        bitString <- BitString.getInstance(24)
        _ <- bitString.setRevokedInPlace(0, true)
        _ <- bitString.setRevokedInPlace(bitString.size - 1, true)
        result <- bitString.setRevokedInPlace(bitString.size, true).exit
      } yield assert(result)(failsWithA[IndexOutOfBounds])
    },
    test("Revoking with a negative index fails") {
      for {
        bitString <- BitString.getInstance(8)
        result <- bitString.setRevokedInPlace(-1, true).exit
      } yield assert(result)(failsWithA[IndexOutOfBounds])
    },
    test("Revoking with an index above the range fails") {
      for {
        bitString <- BitString.getInstance(8)
        result <- bitString.setRevokedInPlace(20, false).exit
      } yield assert(result)(failsWithA[IndexOutOfBounds])
    },
    test("Getting revocation state with a negative index fails") {
      for {
        bitString <- BitString.getInstance(8)
        result <- bitString.isRevoked(-1).exit
      } yield assert(result)(failsWithA[IndexOutOfBounds])
    },
    test("Getting revocation state with an index above the range fails") {
      for {
        bitString <- BitString.getInstance(8)
        result <- bitString.isRevoked(20).exit
      } yield assert(result)(failsWithA[IndexOutOfBounds])
    }
  )

}
