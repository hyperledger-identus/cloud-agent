package org.hyperledger.identus.server.jobs

import org.hyperledger.identus.credentials.core.model.error.PresentationError
import org.hyperledger.identus.credentials.vc.jwt.{
  DIDResolutionFailed,
  DIDResolutionSucceeded,
  DidResolver as JwtDidResolver,
  *
}
import org.hyperledger.identus.did.core.model.did.{LongFormPrismDID, PrismDID}
import org.hyperledger.identus.shared.crypto.*
import org.hyperledger.identus.shared.models.Failure
import org.hyperledger.identus.wallet.model.{ManagedDIDState, PublicationState}
import org.hyperledger.identus.wallet.service.ManagedDIDService
import zio.ZIO

import java.util.Base64

trait DIDResolutionHelper {

  def getLongForm(
      did: PrismDID,
      allowUnpublishedIssuingDID: Boolean = false
  ): ZIO[ManagedDIDService & org.hyperledger.identus.shared.models.WalletAccessContext, Failure, LongFormPrismDID] = {
    for {
      managedDIDService <- ZIO.service[ManagedDIDService]
      didState <- managedDIDService
        .getManagedDIDState(did.asCanonical)
        .someOrFail(BackgroundJobError.InvalidState(s"Issuer DID does not exist in the wallet: $did"))
        .flatMap {
          case s @ ManagedDIDState(_, _, PublicationState.Published(_)) => ZIO.succeed(s)
          case s                                                        =>
            ZIO.cond(
              allowUnpublishedIssuingDID,
              s,
              BackgroundJobError.InvalidState(s"Issuer DID must be published: $did")
            )
        }
      longFormPrismDID = PrismDID.buildLongFormFromOperation(didState.createOperation)
    } yield longFormPrismDID
  }

  def resolveToEd25519PublicKey(did: String): ZIO[JwtDidResolver, PresentationError, Ed25519PublicKey] = {
    for {
      didResolverService <- ZIO.service[JwtDidResolver]
      didResolutionResult <- didResolverService.resolve(did)
      publicKeyBase64 <- didResolutionResult match {
        case failed: DIDResolutionFailed =>
          ZIO.fail(
            PresentationError.DIDResolutionFailed(did, failed.error.toString)
          )
        case succeeded: DIDResolutionSucceeded =>
          succeeded.didDocument.verificationMethod
            .find(vm => succeeded.didDocument.assertionMethod.contains(vm.id))
            .flatMap(_.publicKeyJwk.flatMap(_.x))
            .toRight(PresentationError.DIDDocumentMissing(did))
            .fold(ZIO.fail(_), ZIO.succeed(_))
      }
      ed25519PublicKey <- ZIO
        .fromTry {
          val decodedKey = Base64.getUrlDecoder.decode(publicKeyBase64)
          KmpEd25519KeyOps.publicKeyFromEncoded(decodedKey)
        }
        .mapError(t => PresentationError.PublicKeyDecodingError(t.getMessage))
    } yield ed25519PublicKey
  }
}
