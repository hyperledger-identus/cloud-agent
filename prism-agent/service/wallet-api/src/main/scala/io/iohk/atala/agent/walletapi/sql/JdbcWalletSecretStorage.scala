package io.iohk.atala.agent.walletapi.sql

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import io.iohk.atala.agent.walletapi.model.WalletSeed
import io.iohk.atala.agent.walletapi.storage.WalletSecretStorage
import io.iohk.atala.shared.db.ContextAwareTask
import io.iohk.atala.shared.db.Implicits.{*, given}
import io.iohk.atala.shared.models.WalletAccessContext
import io.iohk.atala.shared.models.WalletId
import java.time.Instant
import zio.*

class JdbcWalletSecretStorage(xa: Transactor[ContextAwareTask]) extends WalletSecretStorage {

  override def setWalletSeed(seed: WalletSeed): RIO[WalletAccessContext, Unit] = {
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
      _ <- cxnIO(now, walletId).run.transactWallet(xa)
    } yield ()
  }

  override def getWalletSeed: RIO[WalletAccessContext, Option[WalletSeed]] = {
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
        case None => ZIO.none
        case Some(bytes) =>
          ZIO
            .fromEither(WalletSeed.fromByteArray(bytes))
            .mapError(Exception(_))
            .asSome
      }
  }

}

object JdbcWalletSecretStorage {
  val layer: URLayer[Transactor[ContextAwareTask], WalletSecretStorage] =
    ZLayer.fromFunction(new JdbcWalletSecretStorage(_))
}
