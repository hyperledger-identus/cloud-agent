package org.hyperledger.identus.credentials.core.model

import org.hyperledger.identus.credentials.core.model.presentation.Options
import org.hyperledger.identus.credentials.prex.PresentationDefinition
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

final case class CredentialOfferAttachment(options: Options, presentation_definition: PresentationDefinition)

object CredentialOfferAttachment {
  given JsonEncoder[CredentialOfferAttachment] = DeriveJsonEncoder.gen
  given JsonDecoder[CredentialOfferAttachment] = DeriveJsonDecoder.gen
}
