package org.hyperledger.identus.castor.core.model.did

import org.hyperledger.identus.castor.core.util.UriUtils
import zio.json.ast.Json as ZioJson

sealed trait ServiceEndpoint {
  def normalize(): ServiceEndpoint
}

object ServiceEndpoint {

  opaque type UriValue = String

  object UriValue {
    def fromString(uri: String): Either[String, UriValue] = {
      val isUri = UriUtils.isValidUriString(uri)
      if (isUri) Right(uri) else Left(s"unable to parse service endpoint URI: \"$uri\"")
    }
  }

  extension (uri: UriValue) {
    def value: String = uri

    def normalize(): UriValue = {
      UriUtils.normalizeUri(uri).get // uri is already validated
    }
  }

  sealed trait UriOrJsonEndpoint {
    def normalize(): UriOrJsonEndpoint
  }

  object UriOrJsonEndpoint {
    final case class Uri(uri: UriValue) extends UriOrJsonEndpoint {
      override def normalize(): UriOrJsonEndpoint = copy(uri = uri.normalize())
    }

    final case class Json(json: ZioJson.Obj) extends UriOrJsonEndpoint {
      override def normalize(): UriOrJsonEndpoint = this
    }

    given Conversion[UriValue, UriOrJsonEndpoint] = Uri(_)
    given Conversion[ZioJson.Obj, UriOrJsonEndpoint] = Json(_)
  }

  final case class Single(value: UriOrJsonEndpoint) extends ServiceEndpoint {
    override def normalize(): Single = copy(value = value.normalize())
  }

  final case class Multiple(head: UriOrJsonEndpoint, tail: Seq[UriOrJsonEndpoint]) extends ServiceEndpoint {
    def values: Seq[UriOrJsonEndpoint] = head +: tail

    override def normalize(): Multiple = {
      Multiple(
        head = head.normalize(),
        tail = tail.map(_.normalize())
      )
    }
  }

}
