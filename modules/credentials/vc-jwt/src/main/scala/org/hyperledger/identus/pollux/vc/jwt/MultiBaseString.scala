package org.hyperledger.identus.pollux.vc.jwt

import org.hyperledger.identus.shared.utils.Base64Utils
import scodec.bits.ByteVector
import zio.json.{JsonDecoder, JsonEncoder}

case class MultiBaseString(header: MultiBaseString.Header, data: String) {
  def toMultiBaseString: String = s"${header.value}$data"

  def getBytes: Either[String, Array[Byte]] = header match {
    case MultiBaseString.Header.Base64Url => Right(Base64Utils.decodeURL(data))
    case MultiBaseString.Header.Base58Btc =>
      ByteVector.fromBase58(data).map(_.toArray).toRight(s"Invalid base58 string: $data")
  }
}

object MultiBaseString {
  enum Header(val value: Char) {
    case Base64Url extends Header('u')
    case Base58Btc extends Header('z')
  }

  def fromString(str: String): Either[String, MultiBaseString] = {
    val header = Header.fromValue(str.head)
    header match {
      case Some(value) => Right(MultiBaseString(value, str.tail))
      case None        => Left(s"$str - is not a multi base string")
    }
  }

  object Header {
    def fromValue(value: Char): Option[Header] = value match {
      case 'u' => Some(Header.Base64Url)
      case 'z' => Some(Header.Base58Btc)
      case _   => None
    }
  }

  given JsonEncoder[MultiBaseString] = JsonEncoder.string.contramap(_.toMultiBaseString)

  given JsonDecoder[MultiBaseString] = JsonDecoder[String].mapOrFail { str =>
    val header = MultiBaseString.Header.fromValue(str.head)
    header match {
      case Some(value) => Right(MultiBaseString(value, str.tail))
      case None        => Left(s"no enum value matched for $str")
    }
  }
}
