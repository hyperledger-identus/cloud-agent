package org.hyperledger.identus.castor.core.model.did

import zio.*
import zio.test.*
import zio.test.Assertion.*

object ServiceTypeSpec extends ZIOSpecDefault {

  override def spec = suite("ServiceType")(parseServiceTypeNameSpec)

  private def testParse(testName: String, serviceType: String, failAssertion: Option[Assertion[String]] = None) = {
    test(testName) {
      val parsed = ServiceType.Name.fromString(serviceType)
      failAssertion match {
        case Some(assertion) => assert(parsed)(isLeft(assertion))
        case None            => assert(parsed.map(_.value))(isRight(equalTo(serviceType)))
      }
    }
  }

  private val parseServiceTypeNameSpec = suite("parse ServiceType.Name")(
    testParse("parse valid name", "LinkedDomains"),
    testParse("parse valid name with allowed symbols", "-_-LinkedDomains-_-"),
    testParse("parse valid name with space in between", "Linked Domains"),
    testParse("parse valid name with multiple spaces in between", "Linked   Domains"),
    testParse("parse valid name only number", "123"),
    testParse("parse valid 1 character", "a"),
    testParse("reject empty name", "", Some(containsString("not a valid value"))),
    testParse("reject name containing only spaces", "    ", Some(containsString("not a valid value"))),
    testParse("reject name staring with space", " LinkedDomains", Some(containsString("not a valid value"))),
    testParse("reject name ending with space", "LinkedDomains ", Some(containsString("not a valid value"))),
    testParse(
      "reject name containing invalid characters",
      "LinkedDomains.com",
      Some(containsString("not a valid value"))
    ),
  )

}
