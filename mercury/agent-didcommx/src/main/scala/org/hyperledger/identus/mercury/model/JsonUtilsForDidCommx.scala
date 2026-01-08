package org.hyperledger.identus.mercury.model

import zio.json.ast.Json
import zio.json.DecoderOps

import scala.jdk.CollectionConverters.*

object JsonUtilsForDidCommx {
  private type JsonValue = Boolean | BigDecimal | String | Json.Null
  private type SubJsonFields[F] = Seq[F] | Map[String, F]
  private type JsonRecursiveType[F] = SubJsonFields[F] | JsonValue
  case class FixJson[F[_]](out: F[FixJson[F]])
  type MyJson = FixJson[JsonRecursiveType]
  type MyJsonTop = Map[String, MyJson]

  private def fromJson(body: Json.Obj): MyJsonTop =
    body.toMap.map { case (k, v) => (k, fromJsonAux(v)) }

  private def fromJsonAux(body: Json): MyJson = body match
    case Json.Null          => FixJson(Json.Null)
    case Json.Bool(value)   => FixJson(value)
    case Json.Num(value)    => FixJson(value)
    case Json.Str(value)    => FixJson(value)
    case Json.Arr(elements) => FixJson(elements.toSeq.map(json => fromJsonAux(json)))
    case Json.Obj(fields)   => FixJson(fields.toMap.map { case (k, v) => (k, fromJsonAux(v)) })

  def fromJsonToJavaMap(body: Json.Obj): java.util.Map[String, Any] = toJavaMap(fromJson(body))

  private def toJavaMap(json: MyJsonTop): java.util.Map[String, Any] = {
    def myJsonToMap(f: MyJson): Any = f match {
      case FixJson(Json.Null) => null // TODO Json.Null ->  An illegal reflective access operation has occurred
      case FixJson(jsonValue: JsonValue)              => jsonValue
      case FixJson(seq: Seq[MyJson] @unchecked)       => seq.map(myJsonToMap(_)).toArray
      case FixJson(m: Map[String, MyJson] @unchecked) => toJavaMap(m)
    }
    json.map { case (s, fixJson) => (s, myJsonToMap(fixJson)) }.asJava
  }

  def toJson(json: MyJsonTop): Json.Obj = {
    def myJsonToJson(f: MyJson): Json = f match {
      case FixJson(value: Json.Null)                      => value // TEST
      case FixJson(value: Boolean)                        => Json.Bool(value)
      case FixJson(value: BigDecimal)                     => Json.Num(value)
      case FixJson(value: String)                         => Json.Str(value)
      case FixJson(elements: Seq[MyJson] @unchecked)      => Json.Arr(elements.map(myJsonToJson): _*)
      case FixJson(items: Map[String, MyJson] @unchecked) =>
        Json.Obj(items.map { case (s, fixJson) => (s, myJsonToJson(fixJson)) }.toSeq: _*)
    }
    Json.Obj(json.map { case (s, fixJson) => (s, myJsonToJson(fixJson)) }.toSeq: _*)
  }

  def fromJavaMapToJsonAux(m: java.util.Map[String, Any]): MyJsonTop = {
    def auxMethod(f: Any): MyJson = f match {
      case a: com.nimbusds.jose.shaded.json.JSONArray => // TODO why do we need this
        FixJson(a.toArray.toSeq.map(e => auxMethod(e)))
      case null                                     => FixJson(Json.Null) // Can be remove if we use JsonNull
      case a: Array[Any]                            => FixJson(a.toSeq.map(e => auxMethod(e)))
      case e: java.util.Map[String, Any] @unchecked => FixJson(fromJavaMapToJsonAux(e))
      case value: JsonValue                         => FixJson(value)
    }
    m.asScala.toMap.map { case (s, any) => (s: String, auxMethod(any): MyJson) }
  }

  def fromJavaMapToJson(m: java.util.Map[String, Any]): Json.Obj = toJson(fromJavaMapToJsonAux(m))
}

@main def JsonUtilsForDidCommxMain() = {
  val json = """
    |{
    |  "a" : {"b": {"c": 1.1}},
    |  "type": "DIDCommMessaging",
    |  "serviceEndpoint": "http://localhost:8000/",
    |  "routingKeys": ["did:example:somemediator#somekey2"],
    |  "accept": ["didcomm/v2", "didcomm/aip2;env=rfc587"]
    |}""".stripMargin

  val x1 = json.fromJson[Json].toOption.flatMap(_.asObject).get
  println(x1)

  val x2 = JsonUtilsForDidCommx.fromJsonToJavaMap(x1)
  println(x2)

  val x3 = JsonUtilsForDidCommx.fromJavaMapToJsonAux(x2)
  println(x3)

  // val x4 = JsonUtilsForDidCommx.toMap(x3)
  // println(x4)

  val x5 = JsonUtilsForDidCommx.toJson(x3)
  println(x5)

}
