package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.pollux.prex.PresentationDefinition
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*

import java.util.UUID

trait PresentationExchangeRepository {
  def createPresentationDefinition(pd: PresentationDefinition): URIO[WalletAccessContext, Unit]
  def findPresentationDefinition(id: UUID): UIO[Option[PresentationDefinition]]
  def listPresentationDefinition(
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): URIO[WalletAccessContext, (Seq[PresentationDefinition], Int)]
}
