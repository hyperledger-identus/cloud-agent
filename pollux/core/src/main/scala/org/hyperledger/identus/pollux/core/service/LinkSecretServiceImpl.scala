package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.agent.walletapi.storage.{GenericSecret, GenericSecretStorage}
import org.hyperledger.identus.pollux.anoncreds.{AnoncredLinkSecret, AnoncredLinkSecretWithId}
import org.hyperledger.identus.pollux.core.model.error.LinkSecretError
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio.*
import zio.json.ast.Json

import scala.util.Try

class LinkSecretServiceImpl(genericSecretStorage: GenericSecretStorage) extends LinkSecretService {

  import LinkSecretServiceImpl.given

  type Result[T] = ZIO[WalletAccessContext, LinkSecretError, T]

  override def fetchOrCreate(): URIO[WalletAccessContext, AnoncredLinkSecretWithId] = {
    genericSecretStorage
      .get[String, AnoncredLinkSecret](LinkSecretServiceImpl.defaultLinkSecretId)
      .flatMap {
        case Some(secret) => ZIO.succeed(secret)
        case None         =>
          val linkSecret = AnoncredLinkSecret()
          genericSecretStorage
            .set[String, AnoncredLinkSecret](LinkSecretServiceImpl.defaultLinkSecretId, linkSecret)
            .as(linkSecret)
      }
      .map(linkSecret => AnoncredLinkSecretWithId(LinkSecretServiceImpl.defaultLinkSecretId, linkSecret))
      .orDie
  }
}

object LinkSecretServiceImpl {
  val defaultLinkSecretId = "default-link-secret-id"

  val layer: URLayer[
    GenericSecretStorage,
    LinkSecretService
  ] =
    ZLayer.fromFunction(LinkSecretServiceImpl(_))

  given GenericSecret[String, AnoncredLinkSecret] = new {
    override def keyPath(id: String): String = s"link-secret/${id.toString}"

    override def encodeValue(secret: AnoncredLinkSecret): Json = Json.Str(secret.data)

    override def decodeValue(json: Json): Try[AnoncredLinkSecret] = json match {
      case Json.Str(data) => Try(AnoncredLinkSecret(data))
      case _              => scala.util.Failure(new Exception("Invalid JSON format for LinkSecret"))
    }
  }
}
