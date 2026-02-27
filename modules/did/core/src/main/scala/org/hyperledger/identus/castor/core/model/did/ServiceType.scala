package org.hyperledger.identus.castor.core.model.did

sealed trait ServiceType

object ServiceType {

  opaque type Name = String

  object Name {
    def fromStringUnsafe(name: String): Name = name

    def fromString(name: String): Either[String, Name] = {
      val pattern = """^[A-Za-z0-9\-_]+(\s*[A-Za-z0-9\-_])*$""".r
      pattern
        .findFirstIn(name)
        .toRight(
          s"The service type '$name' is not a valid value."
        )
    }
  }

  extension (name: Name) {
    def value: String = name
  }

  final case class Single(value: Name) extends ServiceType
  final case class Multiple(head: Name, tail: Seq[Name]) extends ServiceType {
    def values: Seq[Name] = head +: tail
  }
}
