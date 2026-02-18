package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import hyperledger.identus.vdr.prism
import io.grpc.StatusRuntimeException
import org.hyperledger.identus.shared.models.{Failure, StatusCode}

sealed trait VdrServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "VdrServiceError"
}

object VdrServiceError {
  final case class DriverNotFound(cause: Throwable)
      extends VdrServiceError(
        StatusCode.BadRequest,
        s"The driver with provided specification could not be found"
      )
  final case class MissingVdrKey(cause: Throwable)
      extends VdrServiceError(
        StatusCode.BadRequest,
        s"No VDR signing key available for the current wallet/DID"
      )
  final case class VdrEntryNotFound(
      cause: InMemoryDriver.DataCouldNotBeFoundException | DatabaseDriver.DataCouldNotBeFoundException |
        prism.DataAlreadyDeactivatedException | prism.DataCouldNotBeFoundException | prism.DataNotInitializedException |
        StatusRuntimeException
  ) extends VdrServiceError(
        StatusCode.NotFound,
        s"The data could not be found from a provided URL"
      )
}
