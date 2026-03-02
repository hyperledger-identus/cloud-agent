package org.hyperledger.identus.oid4vci.storage

import org.hyperledger.identus.oid4vci.domain.IssuanceSession
import zio.{IO, ULayer, ZIO, ZLayer}

import scala.collection.concurrent.TrieMap

trait IssuanceSessionStorage {
  def start(issuanceSession: IssuanceSession): IO[IssuanceSessionStorage.Error, IssuanceSession]
  def getByNonce(nonce: String): IO[IssuanceSessionStorage.Error, Option[IssuanceSession]]
  def getByIssuerState(issuerState: String): IO[IssuanceSessionStorage.Error, Option[IssuanceSession]]
  def update(issuanceSession: IssuanceSession): IO[IssuanceSessionStorage.Error, IssuanceSession]
}

object IssuanceSessionStorage {
  trait Error {
    def message: String
  }

  object Errors {
    case class IssuanceSessionNotFound(nonce: String) extends Error {
      override def message: String = s"Issuance session with nonce $nonce not found"
    }

    case class IssuanceSessionAlreadyExists(nonce: String) extends Error {
      override def message: String = s"Issuance session with nonce $nonce already exists"
    }

    case class CouldNotStartIssuanceSession(issuanceSession: IssuanceSession, details: String) extends Error {
      override def message: String = s"Could not start issuance session $issuanceSession. Details: $details"
    }
  }
}

case class InMemoryIssuanceSessionService() extends IssuanceSessionStorage {
  private val issuanceSessions: TrieMap[String, IssuanceSession] = TrieMap.empty

  override def start(issuanceSession: IssuanceSession): IO[IssuanceSessionStorage.Error, IssuanceSession] = {
    issuanceSessions
      .put(issuanceSession.nonce, issuanceSession)
      .fold(ZIO.succeed(issuanceSession))(foundIssuanceSession =>
        ZIO.fail(IssuanceSessionStorage.Errors.IssuanceSessionAlreadyExists(issuanceSession.nonce))
      )
  }

  override def getByNonce(nonce: String): IO[IssuanceSessionStorage.Error, Option[IssuanceSession]] = {
    issuanceSessions.get(nonce) match {
      case Some(issuanceSession) => ZIO.succeed(Some(issuanceSession))
      case None                  => ZIO.succeed(None)
    }
  }

  override def getByIssuerState(issuerState: String): IO[IssuanceSessionStorage.Error, Option[IssuanceSession]] = {
    issuanceSessions.values.find(_.issuerState == issuerState) match {
      case Some(issuanceSession) => ZIO.succeed(Some(issuanceSession))
      case None                  => ZIO.succeed(None)
    }
  }

  override def update(issuanceSession: IssuanceSession): IO[IssuanceSessionStorage.Error, IssuanceSession] = {
    issuanceSessions
      .put(issuanceSession.nonce, issuanceSession)
      .fold(ZIO.fail(IssuanceSessionStorage.Errors.IssuanceSessionNotFound(issuanceSession.nonce)))(_ =>
        ZIO.succeed(issuanceSession)
      )
  }
}

object InMemoryIssuanceSessionService {
  val layer: ULayer[IssuanceSessionStorage] = ZLayer.succeed(InMemoryIssuanceSessionService())
}
