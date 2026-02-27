package org.hyperledger.identus.agent.walletapi.sql

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import org.hyperledger.identus.agent.walletapi.model.WalletSeed
import org.hyperledger.identus.agent.walletapi.storage.WalletSecretStorage
import org.hyperledger.identus.shared.db.ContextAwareTask
import org.hyperledger.identus.shared.db.Implicits.*
import org.hyperledger.identus.shared.db.Implicits.given
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.time.Instant

class JdbcWalletSecretStorage(xa: Transactor[ContextAwareTask]) extends WalletSecretStorage {

  override def setWalletSeed(seed: WalletSeed): URIO[WalletAccessContext, Unit] = {
    val cxnIO = (now: Instant, walletId: WalletId) => sql"""
        | INSERT INTO public.wallet_seed(
        |   wallet_id,
        |   seed,
        |   created_at
        | ) VALUES (
        |   ${walletId},
        |   ${seed.toByteArray},
        |   ${now}
        | )
        """.stripMargin.update

    for {
      now <- Clock.instant
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      _ <- cxnIO(now, walletId).run.transactWallet(xa).orDie
    } yield ()
  }

  override def findWalletSeed: URIO[WalletAccessContext, Option[WalletSeed]] = {
    val cxnIO =
      sql"""
        | SELECT seed
        | FROM public.wallet_seed
        """.stripMargin
        .query[Array[Byte]]
        .option

    cxnIO
      .transactWallet(xa)
      .flatMap {
        case None        => ZIO.none
        case Some(bytes) =>
          ZIO
            .fromEither(WalletSeed.fromByteArray(bytes))
            .mapError(Exception(_))
            .asSome
      }
      .orDie
  }

}

object JdbcWalletSecretStorage {
  val layer: URLayer[Transactor[ContextAwareTask], WalletSecretStorage] =
    ZLayer.fromFunction(new JdbcWalletSecretStorage(_))
}
