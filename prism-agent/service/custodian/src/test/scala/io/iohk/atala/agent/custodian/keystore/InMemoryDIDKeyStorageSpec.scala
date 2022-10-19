package io.iohk.atala.agent.custodian.keystore

import io.iohk.atala.castor.core.model.did.DID
import io.iohk.atala.agent.custodian.model.*
import io.iohk.atala.agent.custodian.model.ECCoordinates.*
import zio.*
import zio.test.*
import zio.test.Assertion.*

object InMemoryDIDKeyStorageSpec extends ZIOSpecDefault {

  private val didExample = DID(
    method = "example",
    methodSpecificId = "abc"
  )

  def generateKeyPair(publicKey: (Int, Int) = (0, 0), privateKey: (Int, Int) = (0, 0)): ECKeyPair = ECKeyPair(
    publicKey = ECPublicKey(ECPoint(ECCoordinate.fromBigInt(publicKey._1), ECCoordinate.fromBigInt(publicKey._2))),
    privateKey = ECPrivateKey(ECPoint(ECCoordinate.fromBigInt(privateKey._1), ECCoordinate.fromBigInt(privateKey._2)))
  )

  override def spec = suite("InMemoryDIDKeyStorage")(
    listKeySpec,
    getKeySpec,
    upsertKeySpec,
    removeKeySpec
  ).provideLayer(InMemoryDIDKeyStorage.layer)

  private val listKeySpec = suite("listKeys")(
    test("initialize with empty list") {
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        keys <- storage.listKeys(didExample)
      } yield keys
      assertZIO(result)(isEmpty)
    },
    test("list all existing keys") {
      val keyPairs = Map(
        "key-1" -> generateKeyPair(publicKey = (1, 1)),
        "key-2" -> generateKeyPair(publicKey = (2, 2)),
        "key-3" -> generateKeyPair(publicKey = (3, 3))
      )
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- ZIO.foreachDiscard(keyPairs) { case (keyId, keyPair) =>
          storage.upsertKey(didExample, keyId, keyPair)
        }
        keys <- storage.listKeys(didExample)
      } yield keys
      assertZIO(result)(hasSameElements(keyPairs))
    }
  )

  private val getKeySpec = suite("getKey")(
    test("return stored key if exist") {
      val keyPair = generateKeyPair(publicKey = (1, 1))
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- storage.upsertKey(didExample, "key-1", keyPair)
        key <- storage.getKey(didExample, "key-1")
      } yield key
      assertZIO(result)(isSome(equalTo(keyPair)))
    },
    test("return None if stored key doesn't exist") {
      val keyPair = generateKeyPair(publicKey = (1, 1))
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- storage.upsertKey(didExample, "key-1", keyPair)
        key <- storage.getKey(didExample, "key-2")
      } yield key
      assertZIO(result)(isNone)
    }
  )

  private val upsertKeySpec = suite("upsertKey")(
    test("replace value for existing key") {
      val keyPair1 = generateKeyPair(publicKey = (1, 1))
      val keyPair2 = generateKeyPair(publicKey = (2, 2))
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- storage.upsertKey(didExample, "key-1", keyPair1)
        _ <- storage.upsertKey(didExample, "key-1", keyPair2)
        key <- storage.getKey(didExample, "key-1")
      } yield key
      assertZIO(result)(isSome(equalTo(keyPair2)))
    }
  )

  private val removeKeySpec = suite("removeKey")(
    test("remove existing key and return removed value") {
      val keyPair = generateKeyPair(publicKey = (1, 1))
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- storage.upsertKey(didExample, "key-1", keyPair)
        removedKey <- storage.removeKey(didExample, "key-1")
        keys <- storage.listKeys(didExample)
      } yield (removedKey, keys)
      assertZIO(result.map(_._1))(isSome(equalTo(keyPair))) &&
      assertZIO(result.map(_._2))(isEmpty)
    },
    test("remove non-existing key and return None for the removed value") {
      val keyPair = generateKeyPair(publicKey = (1, 1))
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- storage.upsertKey(didExample, "key-1", keyPair)
        removedKey <- storage.removeKey(didExample, "key-2")
        keys <- storage.listKeys(didExample)
      } yield (removedKey, keys)
      assertZIO(result.map(_._1))(isNone) &&
      assertZIO(result.map(_._2))(hasSize(equalTo(1)))
    },
    test("remove some of existing keys and keep other keys") {
      val keyPairs = Map(
        "key-1" -> generateKeyPair(publicKey = (1, 1)),
        "key-2" -> generateKeyPair(publicKey = (2, 2)),
        "key-3" -> generateKeyPair(publicKey = (3, 3))
      )
      val result = for {
        storage <- ZIO.service[DIDKeyStorage]
        _ <- ZIO.foreachDiscard(keyPairs) { case (keyId, keyPair) =>
          storage.upsertKey(didExample, keyId, keyPair)
        }
        _ <- storage.removeKey(didExample, "key-1")
        keys <- storage.listKeys(didExample)
      } yield keys
      assertZIO(result.map(_.keys))(hasSameElements(Seq("key-2", "key-3")))
    }
  )

}
