package org.hyperledger.identus.pollux.vc.jwt

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import scala.annotation.unused

trait Verifiable(@unused proof: JwtProof)

// JwtProof2020 is not following the spec
case class JwtProof(`type`: String = "JwtProof2020", jwt: JWT)

object JwtProof {
  given JsonEncoder[JwtProof] = DeriveJsonEncoder.gen
  given JsonDecoder[JwtProof] = DeriveJsonDecoder.gen
}
