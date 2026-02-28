package org.hyperledger.identus.shared.utils.proto

sealed trait ValidationError {
  def name: String

  def path: Path

  def explanation: String

  def render = s"$name at ${path.dotRender}: $explanation"
}

object ValidationError {

  /** Error signifying that a value is missing at the path
    *
    * Note: As Protobuf 3 doesn't differentiate between empty and default values for primitives this error is to be used
    * for message fields only
    *
    * @param path
    *   Path where the problem occurred - list of field names
    */
  case class MissingValue(override val path: Path) extends ValidationError {
    override def name = "Missing Value"
    override def explanation = "missing value"
  }

  case class InvalidValue(
      override val path: Path,
      value: String,
      override val explanation: String
  ) extends ValidationError {
    override def name = "Invalid Value"
  }
}
