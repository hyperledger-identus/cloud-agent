package org.hyperledger.identus.castor.core.model.did

import scala.util.matching.Regex

opaque type DIDMethod = String

object DIDMethod {

  private val DID_METHOD_REGEX: Regex = """^[a-z0-9]+$""".r

  extension (method: DIDMethod) {
    def toString: String = method
  }

  def fromStringUnsafe(s: String): DIDMethod = s

  def fromString(s: String): Either[String, DIDMethod] =
    DID_METHOD_REGEX
      .findFirstMatchIn(s)
      .toRight(s"The DID method $s does not conform to the syntax")
      .map(_ => s)

}

opaque type DIDMethodSpecificId = String

object DIDMethodSpecificId {

  private val DID_METHOD_ID_REGEX: Regex =
    """^(([A-Za-z0-9_\-\.]|(%[0-9A-F]{2}))*:)*([A-Za-z0-9_\-\.]|(%[0-9A-F]{2}))+$""".r

  extension (id: DIDMethodSpecificId) {
    def toString: String = id
  }

  def fromStringUnsafe(s: String): DIDMethodSpecificId = s

  def fromString(s: String): Either[String, DIDMethodSpecificId] =
    DID_METHOD_ID_REGEX
      .findFirstMatchIn(s)
      .toRight(s"The DID method specific id $s does not conform to the syntax")
      .map(_ => s)

}

final case class DID(
    method: DIDMethod,
    methodSpecificId: DIDMethodSpecificId
) {
  override def toString: String = s"did:$method:$methodSpecificId"
}

object DID {
  def fromString(s: String): Either[String, DID] = {
    for {
      _ <- Either.cond(s.startsWith("did:"), (), "DID syntax must start with 'did:' prefix")
      methodRaw = s.drop("did:".length).takeWhile(_ != ':')
      methodSpecificIdRaw = s.drop("did:".length).dropWhile(_ != ':').drop(1)
      method <- DIDMethod.fromString(methodRaw)
      methodSpecificId <- DIDMethodSpecificId.fromString(methodSpecificIdRaw)
    } yield DID(method, methodSpecificId)
  }
}
