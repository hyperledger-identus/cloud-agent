package org.hyperledger.identus.pollux.core.model

import org.hyperledger.identus.pollux.core.model.presentation.Options
import org.hyperledger.identus.pollux.prex.PresentationDefinition
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class CredentialOfferAttachment(options: Options, presentation_definition: PresentationDefinition)

object CredentialOfferAttachment {
  given JsonEncoder[CredentialOfferAttachment] = DeriveJsonEncoder.gen
  given JsonDecoder[CredentialOfferAttachment] = DeriveJsonDecoder.gen
}
