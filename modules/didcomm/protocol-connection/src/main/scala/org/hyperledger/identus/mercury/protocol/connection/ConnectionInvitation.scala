package org.hyperledger.identus.mercury.protocol.connection

import org.hyperledger.identus.mercury.model.DidId
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation
import org.hyperledger.identus.mercury.protocol.invitation.v2.Invitation.Body

object ConnectionInvitation {

  /** Make a invitation to establish a trust connection between two peers */
  def makeConnectionInvitation(from: DidId): Invitation = {
    makeConnectionInvitation(
      from = from,
      goalCode = Some("org.hyperledger.identus.connect"),
      goal = Some(s"Establish a trust connection between two peers using the protocol '${ConnectionRequest.`type`}'")
    )
  }

  def makeConnectionInvitation(from: DidId, goalCode: Option[String], goal: Option[String]): Invitation = {
    Invitation(
      from = from,
      body = Invitation.Body(
        goal_code = goalCode,
        goal = goal,
        Nil
      )
    )
  }

}
