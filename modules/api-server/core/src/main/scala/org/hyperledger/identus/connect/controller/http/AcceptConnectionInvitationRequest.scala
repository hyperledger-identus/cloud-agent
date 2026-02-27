package org.hyperledger.identus.connect.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.connect.controller.http.AcceptConnectionInvitationRequest.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

case class AcceptConnectionInvitationRequest(
    @description(annotations.invitation.description)
    @encodedExample(annotations.invitation.example)
    invitation: String
)

object AcceptConnectionInvitationRequest {

  object annotations {
    object invitation
        extends Annotation[String](
          description = "The base64-encoded raw out-of-band invitation.",
          example =
            "eyJAaWQiOiIzZmE4NWY2NC01NzE3LTQ1NjItYjNmYy0yYzk2M2Y2NmFmYTYiLCJAdHlwZSI6Imh0dHBzOi8vZGlkY29tbS5vcmcvbXktZmFtaWx5LzEuMC9teS1tZXNzYWdlLXR5cGUiLCJkaWQiOiJXZ1d4cXp0ck5vb0c5MlJYdnhTVFd2IiwiaW1hZ2VVcmwiOiJodHRwOi8vMTkyLjE2OC41Ni4xMDEvaW1nL2xvZ28uanBnIiwibGFiZWwiOiJCb2IiLCJyZWNpcGllbnRLZXlzIjpbIkgzQzJBVnZMTXY2Z21NTmFtM3VWQWpacGZrY0pDd0R3blpuNnozd1htcVBWIl0sInJvdXRpbmdLZXlzIjpbIkgzQzJBVnZMTXY2Z21NTmFtM3VWQWpacGZrY0pDd0R3blpuNnozd1htcVBWIl0sInNlcnZpY2VFbmRwb2ludCI6Imh0dHA6Ly8xOTIuMTY4LjU2LjEwMTo4MDIwIn0="
        )
  }

  given encoder: JsonEncoder[AcceptConnectionInvitationRequest] =
    DeriveJsonEncoder.gen[AcceptConnectionInvitationRequest]

  given decoder: JsonDecoder[AcceptConnectionInvitationRequest] =
    DeriveJsonDecoder.gen[AcceptConnectionInvitationRequest]

  given schema: Schema[AcceptConnectionInvitationRequest] = Schema.derived

}
