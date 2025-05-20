package org.hyperledger.identus.shared.json

import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.JsonLd
import org.hyperledger.identus.shared.json.Json.*
import zio.test.*
import zio.test.Assertion.*
import zio.ZIO

import java.io.ByteArrayInputStream

object JsonLdLoadSpec extends ZIOSpecDefault {

  private val jsonLdString = """{
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      {
        "id": "@id",
        "type": "@type",
        "name": "http://schema.org/name"
      }
    ],
    "id": "https://example.com/credentials/123",
    "type": ["VerifiableCredential", "UniversityDegreeCredential"],
    "issuer": {
      "id": "https://example.edu/issuers/565049",
      "name": "Example University"
    },
    "credentialSubject": {
      "id": "https://example.edu/students/alice",
      "degree": {
        "type": ["BachelorDegree", "UniversityDegree"],
        "name": "Bachelor of Science and Arts"
      }
    }
  }"""

  val jsonLDDocument = JsonDocument.of(new ByteArrayInputStream(jsonLdString.getBytes))

  def loadJsonLd: ZIO[Any, Throwable, Unit] = {
    ZIO.attempt {
      JsonLd
        .toRdf(jsonLDDocument)
        .loader(Json.lruDocumentLoader) // It fails with an error without the lru loader
        .get
    }.unit
  }

  override def spec = suite("JsonLdLoadSpec")(
    test("Execute JsonLd.toRdf 1000 times") {
      val loadJsonLdRepeatedly = ZIO.foreach(1 to 1000)(_ => loadJsonLd)
      assertZIO(loadJsonLdRepeatedly.exit)(succeeds(anything))
    }
  )
}
