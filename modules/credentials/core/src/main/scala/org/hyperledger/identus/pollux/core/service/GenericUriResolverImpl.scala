package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.service.uriResolvers.*
import org.hyperledger.identus.pollux.vc.jwt.DidResolver
import org.hyperledger.identus.shared.http.{DataUrlResolver, GenericUriResolver, GenericUriResolverError, UriResolver}
import zio.*
import zio.http.*

class GenericUriResolverImpl(client: Client, didResolver: DidResolver) extends UriResolver {
  private val httpUrlResolver = HttpUrlResolver(client)
  private val genericUriResolver = new GenericUriResolver(
    Map(
      "http" -> httpUrlResolver,
      "https" -> httpUrlResolver,
      "data" -> DataUrlResolver(),
      "resource" -> ResourceUrlResolver(Map.empty),
      "did" -> DidUrlResolver(httpUrlResolver, didResolver)
    )
  )
  override def resolve(uri: String): IO[GenericUriResolverError, String] = {

    genericUriResolver.resolve(uri)
  }

}

object GenericUriResolverImpl {
  val layer: URLayer[Client & DidResolver, UriResolver] = ZLayer.fromFunction(GenericUriResolverImpl(_, _))
}
