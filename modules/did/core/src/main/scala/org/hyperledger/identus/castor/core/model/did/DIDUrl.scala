package org.hyperledger.identus.castor.core.model.did

import scala.collection.immutable.ListMap

final case class DIDUrl(
    did: DID,
    path: Seq[String],
    parameters: ListMap[String, Seq[String]],
    fragment: Option[String]
) {

  override def toString: String = {
    val pathString = if (path.isEmpty) "" else path.mkString("/", "/", "")
    val queryString =
      if (parameters.isEmpty) ""
      else parameters.map { case (key, values) => s"$key=${values.mkString(",")}" }.mkString("?", "&", "")
    val fragmentString = fragment.map("#" + _).getOrElse("")

    s"did:${did.method}:${did.methodSpecificId}$pathString$queryString$fragmentString"
  }
}

object DIDUrl {
  def fromString(url: String): Either[String, DIDUrl] = {
    val DIDUrlPattern = """^(did:[^/?#&]*)(/[^?#]*)?(\?[^#]*)?(#.*)?""".r

    url match {
      case DIDUrlPattern(did, path, query, fragment) =>
        for {
          parsedDID <- DID.fromString(did)
        } yield {
          DIDUrl(
            did = parsedDID,
            path = Option(path).map(_.stripPrefix("/").split("/").toSeq).getOrElse(Seq.empty),
            parameters = Option(query).map(parseQuery).getOrElse(ListMap.empty),
            fragment = Option(fragment).map(_.stripPrefix("#"))
          )
        }
    }
  }

  // This method preserves the order of the query parameters to return absolutely the same result as the original query string for testing purposes
  private def parseQuery(query: String): ListMap[String, Seq[String]] = {
    val params = query
      .stripPrefix("?")
      .split("&")
      .toSeq
      .map { param =>
        param.split("=", 2) match {
          case Array(key, value) => key -> value
          case Array(key)        => key -> ""
        }
      }

    var listMap = ListMap.empty[String, Seq[String]]

    params.foreach { case (key, value) =>
      listMap = listMap.updated(key, listMap.getOrElse(key, Seq.empty) :+ value)
    }

    listMap
  }
}
