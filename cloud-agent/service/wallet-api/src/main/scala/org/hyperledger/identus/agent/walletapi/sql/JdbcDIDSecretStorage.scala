package org.hyperledger.identus.agent.walletapi.sql

import com.nimbusds.jose.jwk.OctetKeyPair
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import org.hyperledger.identus.agent.walletapi.storage.DIDSecretStorage
import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.shared.crypto.jwk.{FromJWK, JWK}
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext}
import zio.*

import java.time.Instant
import scala.language.implicitConversions

class JdbcDIDSecretStorage(xa: Transactor[ContextAwareTask]) extends DIDSecretStorage {

  case class InstantAsBigInt(value: Instant)

  given instantGet: Get[InstantAsBigInt] = Get[Long].map(Instant.ofEpochSecond).map(InstantAsBigInt.apply)

  given instantPut: Put[InstantAsBigInt] = Put[Long].contramap(_.value.getEpochSecond())

  given didIdGet: Get[DidId] = Get[String].map(DidId(_))

  given didIdPut: Put[DidId] = Put[String].contramap(_.value)

  override def getKey(did: DidId, keyId: KeyId): RIO[WalletAccessContext, Option[OctetKeyPair]] = {
    val cxnIO = sql"""
         | SELECT key_pair
         | FROM public.peer_did_rand_key
         | WHERE
         |   did = $did
         |   AND key_id = ${keyId.value}
        """.stripMargin
      .query[OctetKeyPair]
      .option

    cxnIO.transactWallet(xa)
  }

  override def insertKey(did: DidId, keyId: KeyId, keyPair: OctetKeyPair): RIO[WalletAccessContext, Int] = {
    val cxnIO = (now: InstantAsBigInt) => sql"""
        | INSERT INTO public.peer_did_rand_key(
        |   did,
        |   created_at,
        |   key_id,
        |   key_pair
        | ) values (
        |   ${did},
        |   ${now},
        |   ${keyId.value},
        |   ${keyPair}
        | )
        """.stripMargin.update

    for {
      now <- Clock.instant
      result <- cxnIO(InstantAsBigInt(now)).run.transactWallet(xa)
    } yield result
  }

  override def insertPrismDIDKeyPair[K](
      did: PrismDID,
      keyId: KeyId,
      operationHash: Array[Byte],
      keyPair: K
  )(using c: Conversion[K, JWK]): URIO[WalletAccessContext, Unit] = {
    val jwk = c(keyPair)
    val cxnIO = (now: Instant) => sql"""
        | INSERT INTO public.prism_did_rand_key(
        |   did,
        |   created_at,
        |   key_id,
        |   operation_hash,
        |   key_pair
        | ) values (
        |   ${did},
        |   ${now},
        |   ${keyId.value},
        |   ${operationHash},
        |   ${jwk}
        | )
        """.stripMargin.update

    for {
      now <- Clock.instant
      _ <- cxnIO(now).run.transactWallet(xa).orDie
    } yield ()
  }

  override def getPrismDIDKeyPair[K](did: PrismDID, keyId: KeyId, operationHash: Array[Byte])(using
      c: FromJWK[K]
  ): URIO[WalletAccessContext, Option[K]] = {
    val cxnIO = sql"""
         | SELECT key_pair
         | FROM public.prism_did_rand_key
         | WHERE
         |   did = $did
         |   AND operation_hash = $operationHash
         |   AND key_id = ${keyId.value}
        """.stripMargin
      .query[JWK]
      .option

    cxnIO
      .transactWallet(xa)
      .flatMap {
        case None      => ZIO.none
        case Some(jwk) =>
          ZIO
            .fromEither(c.from(jwk))
            .mapError(msg => Exception(s"Failed to parse key pair from JWK: $msg"))
            .asSome
      }
      .orDie
  }
}

object JdbcDIDSecretStorage {
  val layer: URLayer[Transactor[ContextAwareTask], DIDSecretStorage] =
    ZLayer.fromFunction(new JdbcDIDSecretStorage(_))
}
