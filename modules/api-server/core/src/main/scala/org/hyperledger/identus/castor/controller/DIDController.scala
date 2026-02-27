package org.hyperledger.identus.castor.controller

import org.hyperledger.identus.castor.controller.http.{
  DIDDocument,
  DIDDocumentMetadata,
  DIDResolutionMetadata,
  DIDResolutionResult
}
import org.hyperledger.identus.castor.core.model.did.w3c.{
  makeW3CResolver,
  DIDDocumentMetadataRepr,
  DIDDocumentRepr,
  DIDResolutionErrorRepr
}
import org.hyperledger.identus.castor.core.service.DIDService
import zio.*

import scala.language.implicitConversions

trait DIDController {
  def getDID(did: String): UIO[DIDResolutionResult]
}

object DIDController {
  def toResolutionResult(
      didDocumentMetadata: DIDDocumentMetadataRepr,
      didDocument: DIDDocumentRepr
  ): DIDResolutionResult = {
    val isDeactivated = didDocumentMetadata.deactivated
    DIDResolutionResult(
      `@context` = "https://w3id.org/did-resolution/v1",
      didDocument = if (isDeactivated) None else Some(didDocument),
      didDocumentMetadata = didDocumentMetadata,
      didResolutionMetadata = DIDResolutionMetadata()
    )
  }

  def toResolutionResult(resolutionError: DIDResolutionErrorRepr): DIDResolutionResult = {
    DIDResolutionResult(
      `@context` = "https://w3id.org/did-resolution/v1",
      didDocument = None,
      didDocumentMetadata = DIDDocumentMetadata(),
      didResolutionMetadata = DIDResolutionMetadata(
        error = Some(resolutionError.value),
        errorMessage = resolutionError.errorMessage
      )
    )
  }
}

class DIDControllerImpl(service: DIDService) extends DIDController {

  override def getDID(did: String): UIO[DIDResolutionResult] = {
    for {
      result <- makeW3CResolver(service)(did).either
      resolutionResult = result.fold(
        DIDController.toResolutionResult,
        { case (metadata, document) =>
          DIDController.toResolutionResult(metadata, document)
        }
      )
    } yield resolutionResult
  }

}

object DIDControllerImpl {
  val layer: URLayer[DIDService, DIDController] = ZLayer.fromFunction(DIDControllerImpl(_))
}
