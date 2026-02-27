package org.hyperledger.identus.castor.core.model.did

import java.time.Instant
import scala.collection.immutable.ArraySeq

final case class DIDData(
    id: CanonicalPrismDID,
    publicKeys: Seq[PublicKey],
    services: Seq[Service],
    internalKeys: Seq[InternalPublicKey],
    context: Seq[String]
)

final case class DIDMetadata(
    lastOperationHash: ArraySeq[Byte],
    canonicalId: Option[CanonicalPrismDID],
    deactivated: Boolean,
    created: Option[Instant],
    updated: Option[Instant]
)
