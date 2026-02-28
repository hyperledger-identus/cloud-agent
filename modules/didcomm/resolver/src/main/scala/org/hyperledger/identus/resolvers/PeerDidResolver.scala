package org.hyperledger.identus.resolvers

import org.didcommx.didcomm.common.*
import org.didcommx.didcomm.diddoc.{DIDCommService, DIDDoc, VerificationMethod}
import org.didcommx.peerdid.PeerDIDResolver.resolvePeerDID
import org.didcommx.peerdid.VerificationMaterialFormatPeerDID
import zio.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

import scala.jdk.CollectionConverters.*

trait PeerDidResolver {
  def resolve(did: String): UIO[String]
  def resolveDidAsJson(did: String): UIO[Option[Json]]
}

case class PeerDidResolverImpl() extends PeerDidResolver {

  def resolve(did: String): UIO[String] = {
    ZIO.succeed { resolvePeerDID(did, VerificationMaterialFormatPeerDID.MULTIBASE) }
  }

  def resolveDidAsJson(did: String): UIO[Option[Json]] = {
    ZIO.succeed {
      resolvePeerDID(did, VerificationMaterialFormatPeerDID.MULTIBASE).fromJson[Json].toOption
    }
  }
}

private case class _DIDDocument(
    id: String,
    service: List[_DIDCommService],
    authentication: List[_VerificationMethod],
    keyAgreement: List[_VerificationMethod]
) {
  def toDIDCommX: DIDDoc = {
    val didCommServices = service.map(_.toDIDCommX)
    val authVerificationMethods = authentication.map(_.toDIDCommX)
    val keyAgreementVerificationMethods = keyAgreement.map(_.toDIDCommX)
    new DIDDoc(
      id,
      keyAgreement.map(_.id).asJava,
      authentication.map(_.id).asJava,
      (keyAgreementVerificationMethods ++ authVerificationMethods).asJava,
      didCommServices.asJava
    )
  }
}

private object _DIDDocument {
  given JsonEncoder[_DIDDocument] = DeriveJsonEncoder.gen

  given JsonDecoder[_DIDDocument] = DeriveJsonDecoder.gen
}

private case class _DIDCommService(id: String, serviceEndpoint: _ServiceEndpoint) {
  def toDIDCommX: DIDCommService =
    new DIDCommService(id, serviceEndpoint.uri, serviceEndpoint.routingKeys.asJava, serviceEndpoint.accept.asJava)
}

private object _DIDCommService {
  given JsonEncoder[_DIDCommService] = DeriveJsonEncoder.gen

  given JsonDecoder[_DIDCommService] = DeriveJsonDecoder.gen
}

private case class _ServiceEndpoint(
    uri: String,
    routingKeys: List[String] = List.empty,
    accept: List[String] = List.empty
)

private object _ServiceEndpoint {
  given JsonEncoder[_ServiceEndpoint] = DeriveJsonEncoder.gen

  given JsonDecoder[_ServiceEndpoint] = DeriveJsonDecoder.gen
}

private case class _VerificationMethod(id: String, controller: String, publicKeyJwk: Json) {
  def toDIDCommX: VerificationMethod = {
    new VerificationMethod(
      id,
      VerificationMethodType.JSON_WEB_KEY_2020,
      new VerificationMaterial(VerificationMaterialFormat.JWK, publicKeyJwk.toJson),
      controller
    )
  }
}

private object _VerificationMethod {
  given JsonEncoder[_VerificationMethod] = DeriveJsonEncoder.gen

  given JsonDecoder[_VerificationMethod] = DeriveJsonDecoder.gen
}

object PeerDidResolver {

  def getDIDDoc(didPeer: String): DIDDoc = {
    resolvePeerDID(didPeer, VerificationMaterialFormatPeerDID.JWK)
      .fromJson[_DIDDocument]
      .toOption
      .get
      .toDIDCommX
  }

  val layer: ULayer[PeerDidResolver] = {
    ZLayer.succeedEnvironment(
      ZEnvironment(PeerDidResolverImpl())
    )
  }

  def resolve(did: String): URIO[PeerDidResolver, String] = {
    ZIO.serviceWithZIO(_.resolve(did))
  }

  def resolveDidAsJson(did: String): URIO[PeerDidResolver, Option[Json]] = {
    ZIO.serviceWithZIO(_.resolveDidAsJson(did))
  }
}
