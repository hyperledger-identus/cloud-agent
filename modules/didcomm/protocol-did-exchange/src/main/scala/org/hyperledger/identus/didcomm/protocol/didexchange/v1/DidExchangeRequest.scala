package org.hyperledger.identus.didcomm.protocol.didexchange.v1

import org.hyperledger.identus.didcomm.model.{AttachmentDescriptor, PIURI}

final case class Thread(thid: String, pthid: String)

final case class DidExchangeRequest(
    `@id`: String,
    `@type`: PIURI,
    `~thread`: Thread,
    label: String,
    goal_code: String,
    goal: String,
    `did_doc~attach`: Option[AttachmentDescriptor]
)
