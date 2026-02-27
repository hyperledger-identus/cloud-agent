package org.hyperledger.identus.resolvers

import org.didcommx.didcomm.diddoc.*
import org.hyperledger.identus.mercury.model.DidId
import zio.*

import java.util.Optional
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

trait DIDResolver {
  def resolveDID(did: DidId): Task[DIDDoc] // TODO return Task[DIDDocument]

  def didCommServices(did: DidId): Task[Seq[DIDCommService]] =
    resolveDID(did).map(_.getDidCommServices().asScala.toSeq)
}
object DIDResolver {
  val layer = ZLayer.succeed(UniversalDidResolver)
}

object UniversalDidResolver extends DIDDocResolver with DIDResolver {

  override def resolveDID(did: DidId): Task[DIDDoc] =
    ZIO.attempt(resolve(did.value).toScala).flatMap {
      case None        => ZIO.fail(new java.lang.RuntimeException("resolve fail"))
      case Some(value) => ZIO.succeed(value)
    }

  override def resolve(did: String): Optional[DIDDoc] = {
    val regex = "(did:peer:.+)".r
    did match {
      case regex(peer)    => Some(PeerDidResolver.getDIDDoc(peer)).toJava
      case anydid: String => None.toJava // new DIDDocResolverInMemory(diddocs.asJava).resolve(anydid)
    }
  }

}
