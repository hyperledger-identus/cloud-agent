package org.hyperledger.identus.shared.utils

import com.google.protobuf.ByteString
import org.hyperledger.identus.shared.utils.proto.ValidationError.{InvalidValue, MissingValue}

package object proto {

  /** Value with path explaining its location if protobuf message */
  case class ValueAtPath[M](value: M, path: Path) {

    /** Extracts the value, potentially applying some transformation on it */
    def apply[T](f: M => T): T = f(value)

    /** Extracts child from the value
      *
      * @param f
      *   function used to extract the child value
      * @param axis
      *   name of the child - field name or array index
      * @tparam MM
      *   type of the child
      * @return
      *   ValueAtPath representing the child
      */
    def child[MM](f: M => MM, axis: String): ValueAtPath[MM] =
      ValueAtPath(f(value), path / axis)

    /** Extracts child from the value
      *
      * @param f
      *   function used to extract the child value
      * @param axis
      *   name of the child - field name or array index
      * @tparam MM
      *   type of the child
      * @return
      *   ValueAtPath representing the child
      */
    def children[MM](f: M => Seq[MM], axis: String): Seq[ValueAtPath[MM]] = {
      f(value).zipWithIndex.map { case (v, i) =>
        ValueAtPath(v, path / axis / i.toString)
      }
    }

    /** Variant of child extracting it from option
      *
      * @param f
      *   function to extract option of child value
      * @param axis
      *   name of the child - field name or array index
      * @tparam MM
      *   type of the child
      * @return
      *   ValueAtPath representing the child or MissingValue error if f returns empty Option
      */
    def childGet[MM](
        f: M => Option[MM],
        axis: String
    ): Either[MissingValue, ValueAtPath[MM]] = {
      f(value)
        .map(ValueAtPath(_, path / axis))
        .toRight(MissingValue(path / axis))
    }

    /** Generates InvalidValue error for this path */
    def invalid(message: String): InvalidValue =
      InvalidValue(path, value.toString, message)

    /** Generates MissingValue error for this path */
    def missing(): MissingValue = MissingValue(path)

    // to string with special case for ByteString, which is useful, as it often occurs in protobuf
    protected def valueToString(value: Any): String = {
      value match {
        case v: ByteString =>
          if (v.isEmpty) "0x0"
          else "0x" + v.toByteArray.map("%02x".format(_)).mkString("")
        case v => v.toString
      }
    }

    /** Attempts to apply potentially failing transform to the value
      *
      * @param f
      *   the transform, should return either result or error message
      * @tparam MM
      *   the result type of the transform
      * @return
      *   transformed value or InvalidError for this path with provided message
      */
    def parse[MM](f: M => Either[String, MM]): Either[InvalidValue, MM] = {
      f(value).left.map(message => InvalidValue(path, valueToString(value), message))
    }
  }
}
