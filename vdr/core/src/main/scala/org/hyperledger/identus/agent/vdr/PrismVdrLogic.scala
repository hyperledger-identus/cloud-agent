package org.hyperledger.identus.agent.vdr

import com.google.protobuf.ByteString
import fmgp.did.method.prism.RefVDR
import hyperledger.identus.vdr.prism.DataAlreadyDeactivatedException
import io.grpc.{Status, StatusRuntimeException}
import io.iohk.atala.prism.protos.{node_api, node_models}
import org.hyperledger.identus.shared.models.HexString
import zio.*

/** Shared helpers for prism-like VDR backends (prism-node, scala-did, neoprism). */
final class PrismVdrLogic(
    client: PrismNodeClient
) {

  def mapStatusError(e: StatusRuntimeException): VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound =
    e.getStatus.getCode match
      case Status.Code.NOT_FOUND | Status.Code.UNKNOWN => VdrServiceError.VdrEntryNotFound(e)
      case Status.Code.FAILED_PRECONDITION => VdrServiceError.VdrEntryNotFound(e) // e.g., latest entry is deactivated
      case _                               => VdrServiceError.DriverNotFound(e)

  def logRequest(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[prism-like VDR] $name request: $payload")

  def logResponse(name: String, payload: String): UIO[Unit] =
    ZIO.logDebug(s"[prism-like VDR] $name response: $payload")

  def extractHash(url: String): String =
    url.split("#").lastOption.getOrElse(url)

  def scheduleSingle(
      signed: node_models.SignedAtalaOperation,
      method: String
  ): IO[VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound, node_api.OperationOutput] =
    client
      .scheduleOperations(node_api.ScheduleOperationsRequest(signedOperations = Seq(signed)))
      .mapBoth(
        {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => VdrServiceError.DriverNotFound(e)
        },
        resp => resp.outputs.headOption
      )
      .flatMap {
        case Some(out) if out.operationMaybe.isError =>
          ZIO.fail(VdrServiceError.DriverNotFound(new RuntimeException(out.getError)))
        case Some(out) => ZIO.succeed(out)
        case None      => ZIO.fail(VdrServiceError.DriverNotFound(new RuntimeException(s"$method returned no outputs")))
      }

  /** Fetch the current head for the immutable entry id (the hash in the URL). */
  def fetchLatestHead(
      entryIdHex: String
  ): IO[VdrServiceError.DriverNotFound | VdrServiceError.VdrEntryNotFound, node_api.VdrEntry] =
    for {
      entryIdBytes <- ZIO
        .fromTry(HexString.fromString(entryIdHex))
        .map(_.toByteArray)
        .mapError(VdrServiceError.DriverNotFound(_))
      _ <- logRequest("head", s"entryId=$entryIdHex")
      resp <- client
        .getVdrEntry(
          node_api.GetVdrEntryRequest(
            eventHash = ByteString.copyFrom(entryIdBytes)
          )
        )
        .mapError {
          case e: StatusRuntimeException => mapStatusError(e)
          case e: Throwable              => VdrServiceError.DriverNotFound(e)
        }
      entry = resp.getEntry
      _ <- ZIO
        .fail(VdrServiceError.VdrEntryNotFound(new DataAlreadyDeactivatedException(RefVDR(entryIdHex))))
        .when(entry.status == node_api.VdrEntryStatus.DEACTIVATED)
      _ <- logResponse(
        "head",
        s"status=${entry.status} hash=${HexString.fromByteArray(entry.eventHash.toByteArray)}"
      )
    } yield entry
}

object PrismVdrLogic {
  def apply(client: PrismNodeClient): PrismVdrLogic =
    new PrismVdrLogic(client)
}
