package org.hyperledger.identus.pollux.sdjwt

import zio.json.*

private[sdjwt] trait ModelsExtensionMethods {

  def jwtHeader: Header
  def jwtPayload: Payload
  def jwtSignature: Signature
  def disclosures: Seq[Disclosure]
  def kbJWT: Option[KBJWT]

  def compact: String =
    jwtHeader + "." + jwtPayload + "." + jwtSignature +
      disclosures.map("~" + _).mkString("") + // note the case where disclosures is empty
      "~" + kbJWT.map(e => e._1 + "." + e._2 + "." + e._3).getOrElse("")

  def payloadAsJsonObj: Either[String, zio.json.ast.Json.Obj] =
    new String(java.util.Base64.getUrlDecoder.decode(jwtPayload))
      .fromJson[ast.Json]
      .map(_.asObject)
      .flatMap {
        case None          => Left("The payload in PresentationCompact must the a Json Object")
        case Some(jsonObj) => Right(jsonObj)
      }

  def iss: Either[String, String] =
    payloadAsJsonObj.flatMap {
      _.get("iss") match
        case None                    => Left("The payload in PresentationCompact must have the field 'iss'")
        case Some(ast.Json.Str(iss)) => Right(iss)
        case Some(_)                 => Left("PresentationCompact must have the field 'iss' as a String")
    }

  def sub: Either[String, String] =
    payloadAsJsonObj.flatMap {
      _.get("sub") match
        case None                    => Left("The payload in PresentationCompact must have the field 'sub'")
        case Some(ast.Json.Str(sub)) => Right(sub)
        case Some(_)                 => Left("PresentationCompact must have the field 'sub' as a String")
    }

  def iat: Either[String, BigDecimal] =
    payloadAsJsonObj.flatMap {
      _.get("iat") match
        case None                    => Left("The payload in PresentationCompact must have the field 'iat'")
        case Some(ast.Json.Num(iat)) => Right(iat)
        case Some(_)                 => Left("PresentationCompact must have the field 'iat' as a Num")
    }

  def exp: Either[String, BigDecimal] =
    payloadAsJsonObj.flatMap {
      _.get("exp") match
        case None                    => Left("The payload in PresentationCompact must have the field 'exp'")
        case Some(ast.Json.Num(exp)) => Right(exp)
        case Some(_)                 => Left("PresentationCompact must have the field 'exp' as a Num")
    }

}
