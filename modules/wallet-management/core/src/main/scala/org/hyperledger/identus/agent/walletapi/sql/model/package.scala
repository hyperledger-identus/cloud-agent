package org.hyperledger.identus.agent.walletapi.sql

import io.getquill.MappedEncoding
import org.hyperledger.identus.shared.models.WalletId

import java.net.{URI, URL}
import java.util.UUID

package object model {
  given MappedEncoding[WalletId, UUID] = MappedEncoding[WalletId, UUID](_.toUUID)
  given MappedEncoding[UUID, WalletId] = MappedEncoding[UUID, WalletId](WalletId.fromUUID)

  given MappedEncoding[URL, String] = MappedEncoding[URL, String](_.toString)
  given MappedEncoding[String, URL] = MappedEncoding[String, URL](URI(_).toURL)
}
