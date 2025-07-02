package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import org.hyperledger.identus.shared.models.{Failure, StatusCode}
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException

sealed trait VdrServiceError(
    val statusCode: StatusCode,
    val userFacingMessage: String
) extends Failure {
  override val namespace: String = "VdrServiceError"
}

object VdrServiceError {
  final case class DriverNotFound(cause: NoDriverWithThisSpecificationsException)
      extends VdrServiceError(
        StatusCode.BadRequest,
        s"The driver with provided specification could not be found"
      )
  final case class VdrEntryNotFound(
      cause: InMemoryDriver.DataCouldNotBeFoundException | DatabaseDriver.DataCouldNotBeFoundException
  ) extends VdrServiceError(
        StatusCode.NotFound,
        s"The data could not be found from a provided URL"
      )
}
