package org.hyperledger.identus.api

import sttp.tapir.Validator

package object http {
  case class Annotation[E](description: String, example: E, validator: Validator[E] = Validator.pass[E])

  val DIDRefRegex = """^did:(?<method>[a-z0-9]+(:[a-z0-9]+)*)\:(?<idstring>[^#?]*)$"""
  val DIDRegex = """^did:(?<method>[a-z0-9]+(:[a-z0-9]+)*)\:(?<idstring>[^#?]*)?(?<query>\?[^#]*)?(?<fragment>\#.*)?$"""
  val SemVerRegex =
    """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$"""
}
