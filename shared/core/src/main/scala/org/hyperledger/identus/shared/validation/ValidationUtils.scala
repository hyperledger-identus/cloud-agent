package org.hyperledger.identus.shared.validation

import zio.prelude.*

object ValidationUtils {

  def validateLengthOptional(
      fieldName: String,
      value: Option[String],
      min: Int,
      max: Int
  ): Validation[String, Option[String]] = {
    value match
      case None    => Validation.succeed(value)
      case Some(v) =>
        val len = v.length
        if (min <= len && max >= len) Validation.succeed(value)
        else Validation.fail(s"Invalid length for '$fieldName': expected=[$min -> $max], actual=$len")
  }

}
