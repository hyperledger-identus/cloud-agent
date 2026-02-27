package org.hyperledger.identus.shared.utils.proto

/** Representation of sequence of field names pointing to some place in protobuf message
  */
case class Path(path: Vector[String]) extends AnyVal {
  def /(axis: String): Path = Path(path :+ axis)

  def dotRender: String = path.mkString(".")
}

object Path {
  def root: Path = Path(Vector.empty)
  def apply(): Path = root
}
