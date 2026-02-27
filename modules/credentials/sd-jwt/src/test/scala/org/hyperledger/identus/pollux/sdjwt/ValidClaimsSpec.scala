package org.hyperledger.identus.pollux.sdjwt

import zio.*
import zio.json.*
import zio.test.*
import zio.test.Assertion.*

object ValidClaimsSpec extends ZIOSpecDefault {

  override def spec = suite("ValidClaims")(
    test("ValidClaims query (empty query)") {
      val ret = for {
        claims <- """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
        expected <- """{}""".fromJson[ast.Json.Obj]
      } yield SDJWT.ValidClaims(claims).verifyDiscloseClaims(expected)
      assert(ret)(isRight(equalTo(SDJWT.ValidAnyMatch)))
    },
    test("ValidClaims query (path exist)") {
      val ret = for {
        claims <- """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
        expected <-
          """{
            |  "address": {
            |    "country":{}
            |  }
            |}
        """.stripMargin.fromJson[ast.Json.Obj]
      } yield SDJWT.ValidClaims(claims).verifyDiscloseClaims(expected)
      assert(ret)(isRight(equalTo(SDJWT.ValidAnyMatch)))
    },
    test("ValidClaims query (path does not exist)") {
      val ret = for {
        claims <- """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
        expected <-
          """{
            |  "address": {
            |    "potatoes":{}
            |  }
            |}
        """.stripMargin.fromJson[ast.Json.Obj]
      } yield SDJWT.ValidClaims(claims).verifyDiscloseClaims(expected)
      assert(ret)(isRight(equalTo(SDJWT.ClaimsDoNotMatch)))
    },
    test("ValidClaims query (check value)") {
      val ret = for {
        claims <- """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
        expected <-
          """{
            |  "address": {
            |    "country": "DE"
            |  }
            |}
        """.stripMargin.fromJson[ast.Json.Obj]
      } yield SDJWT.ValidClaims(claims).verifyDiscloseClaims(expected)
      assert(ret)(isRight(equalTo(SDJWT.ValidAnyMatch)))
    },
    test("ValidClaims query (check fail claim)") {
      val ret = for {
        claims <- """{"iss":"did:example:issuer","iat":1683000000,"exp":1883000000,"address":{"country":"DE"}}"""
          .fromJson[ast.Json.Obj]
        expected <-
          """{
            |  "address": {
            |    "country": "PT"
            |  }
            |}
        """.stripMargin.fromJson[ast.Json.Obj]
      } yield SDJWT.ValidClaims(claims).verifyDiscloseClaims(expected)
      assert(ret)(isRight(equalTo(SDJWT.ClaimsDoNotMatch)))
    }
  )

}
