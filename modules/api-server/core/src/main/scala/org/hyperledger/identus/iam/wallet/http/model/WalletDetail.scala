package org.hyperledger.identus.iam.wallet.http.model

import org.hyperledger.identus.agent.walletapi.model.Wallet
import org.hyperledger.identus.api.http.Annotation
import sttp.tapir.*
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.time.Instant
import java.util.UUID

final case class WalletDetail(
    @description(WalletDetail.annotations.id.description)
    @encodedExample(WalletDetail.annotations.id.example)
    id: UUID,
    @description(WalletDetail.annotations.name.description)
    @encodedExample(WalletDetail.annotations.name.example)
    name: String,
    @description(WalletDetail.annotations.createdAt.description)
    @encodedExample(WalletDetail.annotations.createdAt.example)
    createdAt: Instant,
    @description(WalletDetail.annotations.updatedAt.description)
    @encodedExample(WalletDetail.annotations.updatedAt.example)
    updatedAt: Instant
)

object WalletDetail {
  given encoder: JsonEncoder[WalletDetail] = DeriveJsonEncoder.gen
  given decoder: JsonDecoder[WalletDetail] = DeriveJsonDecoder.gen
  given schema: Schema[WalletDetail] = Schema.derived

  given Conversion[Wallet, WalletDetail] = (wallet: Wallet) => {
    WalletDetail(
      id = wallet.id.toUUID,
      name = wallet.name,
      createdAt = wallet.createdAt,
      updatedAt = wallet.updatedAt
    )
  }

  object annotations {
    object id
        extends Annotation[UUID](
          description = "A wallet ID",
          example = UUID.fromString("00000000-0000-0000-0000-000000000000")
        )

    object name
        extends Annotation[String](
          description = "The name of the wallet",
          example = "my-wallet-1"
        )

    object createdAt
        extends Annotation[Instant](
          description = "The `createdAt` timestamp of the wallet.",
          example = Instant.parse("2023-01-01T00:00:00Z")
        )

    object updatedAt
        extends Annotation[Instant](
          description = "The `updateddAt` timestamp of the wallet.",
          example = Instant.parse("2023-01-01T00:00:00Z")
        )
  }
}

final case class WalletDetailPage(
    self: String,
    kind: String = "WalletPage",
    pageOf: String,
    next: Option[String] = None,
    previous: Option[String] = None,
    contents: Seq[WalletDetail]
)

object WalletDetailPage {
  given encoder: JsonEncoder[WalletDetailPage] = DeriveJsonEncoder.gen[WalletDetailPage]
  given decoder: JsonDecoder[WalletDetailPage] = DeriveJsonDecoder.gen[WalletDetailPage]
  given schema: Schema[WalletDetailPage] = Schema.derived
}
