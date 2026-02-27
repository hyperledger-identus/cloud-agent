package org.hyperledger.identus.pollux.prex

import zio.*
import zio.json.{DecoderOps, DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.test.*

import scala.io.Source
import scala.util.Using

object PresentationSubmissionSpec extends ZIOSpecDefault {

  final case class ExampleTransportEnvelope(presentation_submission: PresentationSubmission)
  object ExampleTransportEnvelope {
    given JsonEncoder[ExampleTransportEnvelope] = DeriveJsonEncoder.gen
    given JsonDecoder[ExampleTransportEnvelope] = DeriveJsonDecoder.gen
  }

  override def spec = suite("PresentationSubmissionSpec")(
    test("parse presentation-submission exmaples from spec") {
      val resourcePaths = Seq(
        "ps/basic_presentation.json",
        "ps/nested_presentation.json",
      )
      ZIO
        .foreach(resourcePaths) { path =>
          ZIO
            .fromTry(Using(Source.fromResource(path))(_.mkString))
            .flatMap(json => ZIO.fromEither(json.fromJson[ExampleTransportEnvelope]))
            .map(_.presentation_submission)
        }
        .as(assertCompletes)
    }
  )

}
