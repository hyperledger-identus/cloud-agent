package org.hyperledger.identus.castor.core.model.did

import org.hyperledger.identus.castor.core.model.error.DIDResolutionError
import org.hyperledger.identus.castor.core.service.DIDService
import zio.*

package object w3c {

  import W3CModelHelper.*

  /** A wrapper for DID resolution in W3C format */
  def makeW3CResolver(
      service: DIDService
  )(did: String): IO[DIDResolutionErrorRepr, (DIDDocumentMetadataRepr, DIDDocumentRepr)] = {
    import DIDResolutionError.*
    for {
      prismDID <- ZIO
        .fromEither(PrismDID.fromString(did))
        .mapError(e => DIDResolutionErrorRepr.InvalidDID(e))
      didData <- service
        .resolveDID(prismDID)
        .tapError {
          case ValidationError(_) => ZIO.unit
          case error              => ZIO.logError(error.toString)
        }
        .mapBoth(
          {
            case ex: DLTProxyError =>
              DIDResolutionErrorRepr.InternalError(s"Error occurred while connecting to Prism Node: ${ex.getMessage}")
            case UnexpectedDLTResult(msg) =>
              DIDResolutionErrorRepr.InternalError(s"Unexpected result obtained from Prism Node: $msg")
            case ValidationError(e) => DIDResolutionErrorRepr.InvalidDID(e.toString)
          },
          _.toRight(DIDResolutionErrorRepr.NotFound)
        )
        .absolve
    } yield {
      // https://www.w3.org/TR/did-core/#dfn-diddocument
      // The value of id in the resolved DID document MUST match the DID that was resolved.
      (didData._1.toW3C, didData._2.toW3C(prismDID))
    }
  }

}
