package org.hyperledger.identus.pollux.core.service.uriResolvers

import io.circe.*
import io.lemonlabs.uri.Url
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.crypto.Sha256Hash
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

object DidUrlResolverSpec extends ZIOSpecDefault {

  private val schema = """
                         |{
                         |   "guid":"ef3e4135-8fcf-3ce7-b5bb-df37defc13f6",
                         |   "id":"e33a6de7-1f93-404f-9f12-9bd7b397fd2c",
                         |   "longId":"did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a/e33a6de7-1f93-404f-9f12-9bd7b397fd2c?version=1.0.0",
                         |   "name":"driving-license",
                         |   "version":"1.0.0",
                         |   "tags":[
                         |      "driving",
                         |      "license"
                         |   ],
                         |   "description":"Driving License Schema",
                         |   "type":"https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
                         |   "schema":{
                         |      "$id":"https://example.com/driving-license-1.0.0",
                         |      "$schema":"https://json-schema.org/draft/2020-12/schema",
                         |      "description":"Driving License",
                         |      "type":"object",
                         |      "properties":{
                         |         "emailAddress":{
                         |            "type":"string",
                         |            "format":"email"
                         |         },
                         |         "givenName":{
                         |            "type":"string"
                         |         },
                         |         "familyName":{
                         |            "type":"string"
                         |         },
                         |         "dateOfIssuance":{
                         |            "type":"string",
                         |            "format":"date-time"
                         |         },
                         |         "drivingLicenseID":{
                         |            "type":"string"
                         |         },
                         |         "drivingClass":{
                         |            "type":"integer"
                         |         }
                         |      },
                         |      "required":[
                         |         "emailAddress",
                         |         "familyName",
                         |         "dateOfIssuance",
                         |         "drivingLicenseID",
                         |         "drivingClass"
                         |      ],
                         |      "additionalProperties":true
                         |   },
                         |   "author":"did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
                         |   "authored":"2024-06-20T15:17:41.049526Z",
                         |   "kind":"CredentialSchema",
                         |   "self":"/schema-registry/schemas/ef3e4135-8fcf-3ce7-b5bb-df37defc13f6"
                         |}
                         |""".stripMargin

  class MockHttpUrlResolver extends HttpUrlResolver(null) {
    // Mock implementation, always resolves some schema
    override def resolve(uri: String) = ZIO.succeed(schema)
  }

  private val didResolverLayer = ZLayer.succeed(new DidResolver {
    // mock implementation, alwasys resolves some DID
    override def resolve(didUrl: String) = ZIO.succeed(
      DIDResolutionSucceeded(
        DIDDocument(
          id = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
          alsoKnowAs = Vector.empty[String],
          controller = Vector("did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a"),
          verificationMethod = Vector(
            VerificationMethod(
              id = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a#auth-1",
              `type` = "JsonWebKey2020",
              controller = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
              publicKeyBase58 = Option.empty,
              publicKeyBase64 = Option.empty,
              publicKeyJwk = Some(
                JsonWebKey(
                  crv = Some("secp256k1"),
                  x = Some("HFmBco2W7GT7n-JTx6R0Cd3fV0GpOxuWWC0Uu-B4vik"),
                  y = Some("1wwJuzZ4e898lWyLjwHi3H83602JI-8ErcWt08czqfI"),
                  kty = "EC"
                )
              ),
              publicKeyHex = Option.empty,
              publicKeyMultibase = Option.empty,
              blockchainAccountId = Option.empty,
              ethereumAddress = Option.empty
            ),
            VerificationMethod(
              id = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a#issue-1",
              `type` = "JsonWebKey2020",
              controller = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
              publicKeyBase58 = Option.empty,
              publicKeyBase64 = Option.empty,
              publicKeyJwk = Some(
                JsonWebKey(
                  crv = Some("secp256k1"),
                  x = Some("CXIFl2R18ameLD-ykSOGKQoCBVbFM5oulkc2vIrJtS4"),
                  y = Some("D2QYNi6-A9z1lxpRjKbocKSTvNAIsNVslBjlzegYyUA"),
                  kty = "EC"
                )
              ),
              publicKeyHex = Option.empty,
              publicKeyMultibase = Option.empty,
              blockchainAccountId = Option.empty,
              ethereumAddress = Option.empty
            )
          ),
          authentication = Vector(
            "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a#auth-1"
          ),
          assertionMethod = Vector(
            "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a#issue-1"
          ),
          service = Vector(
            Service(
              id = "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a#agent-base-url",
              `type` = "LinkedResourceV1",
              serviceEndpoint = Json.fromString("https://agent-url.com")
            )
          )
        ),
        DIDDocumentMetadata(
          created = Some(Instant.parse("2024-06-20T15:16:39Z")),
          updated = Some(Instant.parse("2024-06-20T15:16:39Z")),
          deactivated = Some(false)
        )
      )
    )
  })
  private val httpUrlResolver = ZLayer.succeed(new MockHttpUrlResolver)

  private val schemaHash = Sha256Hash.compute(schema.getBytes()).hexEncoded

  private val testDidUrl = Url
    .parse(
      s"did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a?resourceService=agent-base-url&resourcePath=schema-registry/schemas/ef3e4135-8fcf-3ce7-b5bb-df37defc13f6&resourceHash=$schemaHash"
    )
    .toString

  override def spec = {
    suite("DidUrlResolverSpec")(
      test("Should resolve a DID url correctly") {
        for {
          didResolver <- ZIO.service[DidResolver]
          httpUrlResolver <- ZIO.service[HttpUrlResolver]
          didUrlResolver = new DidUrlResolver(httpUrlResolver, didResolver)
          response <- didUrlResolver.resolve(testDidUrl)
          responseJson = Json.fromString(response)
        } yield {
          val schemaJson = Json.fromString(schema)
          assert(responseJson.noSpaces)(
            equalTo(schemaJson.noSpaces)
          )
        }
      }
    ).provide(didResolverLayer, httpUrlResolver)
  }
}
