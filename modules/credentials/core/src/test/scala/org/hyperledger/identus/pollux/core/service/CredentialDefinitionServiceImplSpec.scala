package org.hyperledger.identus.pollux.core.service

import zio.*
import zio.test.*
import zio.test.TestAspect.*

import java.time.OffsetDateTime

object CredentialDefinitionServiceImplSpec extends ZIOSpecDefault with CredentialDefinitionServiceSpecHelper {

  override def spec = {
    suite("CredentialServiceImpl")(
      test("createCredentialDefinition with valid definition creates a valid credential record") {
        check(
          Gen.string,
          Gen.string,
          Gen.string,
          Gen.string,
          Gen.string
        ) { (name, description, version, signatureType, tag) =>
          for {
            svc <- ZIO.service[CredentialDefinitionService]
            record <- svc.createRecord(
              name = name,
              description = description,
              version = version,
              signatureType = signatureType,
              tag = tag,
              author = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff",
              authored = Some(OffsetDateTime.parse("2022-03-10T12:00:00Z")),
              schemaId = "resource:///anoncred-schema-example.json",
              supportRevocation = true
            )
          } yield {
            assertTrue(record.name == name)
            assertTrue(record.description == description)
            assertTrue(record.version == version)
            assertTrue(record.signatureType == signatureType)
            assertTrue(record.tag == tag)
            assertTrue(record.supportRevocation)
            assertTrue(record.name == name)
          }
        }
      }
    ).provideLayer(credentialDefinitionServiceLayer)
  } @@ samples(1)
}
