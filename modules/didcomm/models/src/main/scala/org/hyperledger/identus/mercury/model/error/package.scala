package org.hyperledger.identus.mercury.model

import org.hyperledger.identus.shared.models.*

package object error {

  sealed trait MercuryError extends Failure {
    override val namespace: String = "MercuryError"
  }

  sealed case class SendMessageError(cause: Throwable, mData: Option[String] = None) extends MercuryError {
    override val statusCode = StatusCode.BadRequest
    override val userFacingMessage =
      s"Error when sending message: ${cause.getMessage};${mData.map(e => s" DATA:'$e'").getOrElse("")}. "
        + cause.getMessage
  }

}
