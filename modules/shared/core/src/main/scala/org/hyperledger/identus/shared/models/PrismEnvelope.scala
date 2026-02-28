package org.hyperledger.identus.shared.models
import zio.json.*

trait PrismEnvelope {
  val resource: String
  val url: String
}

case class PrismEnvelopeData(resource: String, url: String) extends PrismEnvelope
object PrismEnvelopeData {
  given encoder: JsonEncoder[PrismEnvelopeData] =
    DeriveJsonEncoder.gen[PrismEnvelopeData]

  given decoder: JsonDecoder[PrismEnvelopeData] =
    DeriveJsonDecoder.gen[PrismEnvelopeData]
}
