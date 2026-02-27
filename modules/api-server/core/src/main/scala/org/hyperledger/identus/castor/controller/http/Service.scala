package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.controller.http.Service.annotations
import org.hyperledger.identus.castor.core.model.{did as castorDomain, ProtoModelHelper}
import org.hyperledger.identus.castor.core.model.did.w3c
import org.hyperledger.identus.shared.utils.Traverse.*
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, EncoderOps, JsonDecoder, JsonEncoder}
import zio.json.ast.Json

import scala.language.implicitConversions

@description("A service that should appear in the DID document. https://www.w3.org/TR/did-core/#services")
final case class Service(
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: String,
    @description(annotations.`type`.description)
    @encodedExample(annotations.`type`.example)
    `type`: ServiceType,
    @description(annotations.serviceEndpoint.description)
    @encodedExample(annotations.serviceEndpoint.example)
    serviceEndpoint: ServiceEndpoint
)

object Service {

  object annotations {
    object id
        extends Annotation[String](
          description = """The id of the service.
              |Requires a URI fragment when use in create / update DID.
              |Returns the full ID (with DID prefix) when resolving DID""".stripMargin,
          example = "service-1"
        )

    object `type`
        extends Annotation[String](
          description =
            "Service type. Can contain multiple possible values as described in the [Create DID operation](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#create-did) under the construction section.",
          example = "LinkedDomains"
        )

    object serviceEndpoint
        extends Annotation[Json](
          description =
            "The service endpoint. Can contain multiple possible values as described in the [Create DID operation](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#create-did)",
          example = Json.Str("https://example.com")
        )
  }

  given encoder: JsonEncoder[Service] = DeriveJsonEncoder.gen[Service]
  given decoder: JsonDecoder[Service] = DeriveJsonDecoder.gen[Service]
  given schema: Schema[Service] = Schema
    .derived[Service]
    .modify(_.serviceEndpoint)(_.copy(isOptional = false))

  given Conversion[w3c.ServiceRepr, Service] = (service: w3c.ServiceRepr) =>
    Service(
      id = service.id,
      `type` = service.`type`,
      serviceEndpoint = ServiceEndpoint.fromJson(service.serviceEndpoint)
    )

  extension (service: Service) {
    def toDomain: Either[String, castorDomain.Service] = {
      for {
        serviceEndpoint <- service.serviceEndpoint.toDomain
        serviceType <- service.`type`.toDomain
      } yield castorDomain
        .Service(
          id = service.id,
          `type` = serviceType,
          serviceEndpoint = serviceEndpoint
        )
        .normalizeServiceEndpoint()
    }
  }
}

sealed trait ServiceType

object ServiceType {
  final case class Single(value: String) extends ServiceType
  final case class Multiple(values: Seq[String]) extends ServiceType

  given encoder: JsonEncoder[ServiceType] = JsonEncoder.string
    .orElseEither(JsonEncoder.array[String])
    .contramap[ServiceType] {
      case Single(value)    => Left(value)
      case Multiple(values) => Right(values.toArray)
    }

  given decoder: JsonDecoder[ServiceType] = JsonDecoder[String]
    .map(Single.apply)
    .orElse(
      JsonDecoder[Seq[String]].map(Multiple.apply)
    )

  given schema: Schema[ServiceType] = Schema
    .schemaForEither(Schema.schemaForString, Schema.schemaForArray[String])
    .map[ServiceType] {
      case Left(value)   => Some(ServiceType.Single(value))
      case Right(values) => Some(ServiceType.Multiple(values.toSeq))
    } {
      case Single(value)    => Left(value)
      case Multiple(values) => Right(values.toArray)
    }

  given Conversion[castorDomain.ServiceType, ServiceType] = {
    case t: castorDomain.ServiceType.Single   => Single(t.value.value)
    case t: castorDomain.ServiceType.Multiple => Multiple(t.values.map(_.value))
  }

  given Conversion[String | Seq[String], ServiceType] = {
    case s: String      => Single(s)
    case s: Seq[String] => Multiple(s)
  }

  extension (serviceType: ServiceType) {
    def toDomain: Either[String, castorDomain.ServiceType] = serviceType match {
      case Single(value) =>
        castorDomain.ServiceType.Name.fromString(value).map(castorDomain.ServiceType.Single.apply)
      case Multiple(values) =>
        values.toList match {
          case Nil          => Left("serviceType cannot be empty")
          case head :: tail =>
            for {
              parsedHead <- castorDomain.ServiceType.Name.fromString(head)
              parsedTail <- tail.traverse(s => castorDomain.ServiceType.Name.fromString(s))
            } yield castorDomain.ServiceType.Multiple(parsedHead, parsedTail)
        }
    }
  }
}

opaque type ServiceEndpoint = Json

object ServiceEndpoint {
  given encoder: JsonEncoder[ServiceEndpoint] = Json.encoder
  given decoder: JsonDecoder[ServiceEndpoint] = Json.decoder
  given schema: Schema[ServiceEndpoint] = Schema.any[ServiceEndpoint]

  def fromJson(json: Json): ServiceEndpoint = json

  extension (serviceEndpoint: ServiceEndpoint) {
    def toDomain: Either[String, castorDomain.ServiceEndpoint] = {
      val stringEncoded = serviceEndpoint.asString match {
        case Some(s) => s
        case None    => serviceEndpoint.toJson
      }
      ProtoModelHelper.parseServiceEndpoint(stringEncoded)
    }
  }
}
