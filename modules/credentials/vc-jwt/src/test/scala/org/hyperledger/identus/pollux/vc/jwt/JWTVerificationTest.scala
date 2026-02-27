package org.hyperledger.identus.pollux.vc.jwt

import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.{Curve, ECKey}
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import org.hyperledger.identus.castor.core.model.did.{DID, VerificationRelationship}
import org.hyperledger.identus.pollux.vc.jwt.StatusPurpose.Revocation
import org.hyperledger.identus.shared.http.*
import zio.*
import zio.json.ast.Json
import zio.json.EncoderOps
import zio.test.*
import zio.test.Assertion.*

import java.security.Security
import java.time.{Clock, Instant, ZoneId}

object JWTVerificationTest extends ZIOSpecDefault {

  Security.insertProviderAt(BouncyCastleProviderSingleton.getInstance(), 2)

  case class IssuerWithKey(issuer: Issuer, key: ECKey)

  private def createUser(did: String): IssuerWithKey = {
    val ecKey = ECKeyGenerator(Curve.SECP256K1).generate()
    IssuerWithKey(
      Issuer(
        did = DID.fromString(did).toOption.get,
        signer = ES256KSigner(ecKey.toPrivateKey),
        publicKey = ecKey.toPublicKey
      ),
      ecKey
    )
  }

  private val statusListCredentialString = """
                                              |{
                                              |  "proof" : {
                                              |    "type" : "EcdsaSecp256k1Signature2019",
                                              |    "proofPurpose" : "assertionMethod",
                                              |    "verificationMethod" : "data:application/json;base64,eyJAY29udGV4dCI6WyJodHRwczovL3czaWQub3JnL3NlY3VyaXR5L3YxIl0sInR5cGUiOiJFY2RzYVNlY3AyNTZrMVZlcmlmaWNhdGlvbktleTIwMTkiLCJwdWJsaWNLZXlKd2siOnsiY3J2Ijoic2VjcDI1NmsxIiwia2V5X29wcyI6WyJ2ZXJpZnkiXSwia3R5IjoiRUMiLCJ4IjoiQ1hJRmwyUjE4YW1lTEQteWtTT0dLUW9DQlZiRk01b3Vsa2MydklySnRTND0iLCJ5IjoiRDJRWU5pNi1BOXoxbHhwUmpLYm9jS1NUdk5BSXNOVnNsQmpsemVnWXlVQT0ifX0=",
                                              |    "created" : "2024-07-25T22:49:59.091957Z",
                                              |    "jws" : "eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJFUzI1NksifQ..FJLUBsZhGB1o_G1UwsVaoL-8agvcpoelJtAr2GlNOOqCSOd-WNEj5-FOgv0m0QcdKMokl2TxibJMg3Y-MJq4-A"
                                              |  },
                                              |  "@context" : [
                                              |    "https://www.w3.org/2018/credentials/v1",
                                              |    "https://w3c.github.io/vc-bitstring-status-list/contexts/2021/v1.jsonld"
                                              |  ],
                                              |  "type" : [
                                              |    "VerifiableCredential",
                                              |    "StatusList2021Credential"
                                              |  ],
                                              |  "id" : "http://localhost:8085/credential-status/01def9a2-2bcb-4bb3-8a36-6834066431d0",
                                              |  "issuer" : "did:prism:462c4811bf61d7de25b3baf86c5d2f0609b4debe53792d297bf612269bf8593a",
                                              |  "issuanceDate" : 1721947798,
                                              |  "credentialSubject" : {
                                              |    "type" : "StatusList2021",
                                              |    "statusPurpose" : "Revocation",
                                              |    "encodedList" : "H4sIAAAAAAAA_-3BIQEAAAACIKf6f4UzLEADAAAAAAAAAAAAAAAAAAAAvA3PduITAEAAAA=="
                                              |  }
                                              |}
                                              |""".stripMargin

  private def createJwtCredential(
      issuer: IssuerWithKey,
      issuerAsObject: Boolean = false,
      credentialStatus: Option[CredentialStatus | List[CredentialStatus]] = None
  ): JWT = {
    val validFrom = Instant.parse("2010-01-05T00:00:00Z") // ISSUANCE DATE
    val jwtCredentialNbf = Instant.parse("2010-01-01T00:00:00Z") // ISSUANCE DATE
    val validUntil = Instant.parse("2010-01-09T00:00:00Z") // EXPIRATION DATE
    val jwtCredentialExp = Instant.parse("2010-01-12T00:00:00Z") // EXPIRATION DATE
    val jwtCredentialPayload = JwtCredentialPayload(
      iss = issuer.issuer.did.toString,
      maybeSub = Some("1"),
      vc = JwtVc(
        `@context` = Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
        `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
        maybeCredentialSchema = None,
        credentialSubject = Json.Obj("id" -> Json.Str("1")),
        maybeCredentialStatus = credentialStatus,
        maybeRefreshService = None,
        maybeEvidence = None,
        maybeTermsOfUse = None,
        maybeValidFrom = Some(validFrom),
        maybeValidUntil = Some(validUntil),
        maybeIssuer = Some(
          if (issuerAsObject) CredentialIssuer(issuer.issuer.did.toString, "Profile")
          else issuer.issuer.did.toString
        )
      ),
      nbf = jwtCredentialNbf, // ISSUANCE DATE
      aud = Set.empty,
      maybeExp = Some(jwtCredentialExp), // EXPIRATION DATE
      maybeJti = Some("http://example.edu/credentials/3732") // CREDENTIAL ID
    )
    issuer.issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
  }

  private def generateDidDocument(
      did: String,
      verificationMethod: Vector[VerificationMethod] = Vector.empty,
      authentication: Vector[VerificationMethodOrRef] = Vector.empty,
      assertionMethod: Vector[VerificationMethodOrRef] = Vector.empty,
      keyAgreement: Vector[VerificationMethodOrRef] = Vector.empty,
      capabilityInvocation: Vector[VerificationMethodOrRef] = Vector.empty,
      capabilityDelegation: Vector[VerificationMethodOrRef] = Vector.empty,
      service: Vector[Service] = Vector.empty
  ): DIDDocument =
    DIDDocument(
      id = did,
      alsoKnowAs = Vector.empty,
      controller = Vector.empty,
      verificationMethod = verificationMethod,
      authentication = authentication,
      assertionMethod = assertionMethod,
      keyAgreement = keyAgreement,
      capabilityInvocation = capabilityInvocation,
      capabilityDelegation = capabilityDelegation,
      service = service
    )

  private def makeResolver(lookup: Map[String, DIDDocument]): DidResolver = (didUrl: String) => {
    lookup
      .get(didUrl)
      .fold(
        ZIO.succeed(DIDResolutionFailed(NotFound(s"DIDDocument not found for $didUrl")))
      )((didDocument: DIDDocument) => {
        ZIO.succeed(
          DIDResolutionSucceeded(
            didDocument,
            DIDDocumentMetadata()
          )
        )
      })
  }

  override def spec = suite("JWTVerificationSpec")(
    test("validate status list credential proof and revocation status by index") {
      val statusList: CredentialStatus = CredentialStatus(
        id = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9#1",
        statusPurpose = StatusPurpose.Revocation,
        `type` = "StatusList2021Entry",
        statusListCredential = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9",
        statusListIndex = 3
      )

      val urlResolver = new UriResolver {
        override def resolve(uri: String): IO[GenericUriResolverError, String] = {
          ZIO.succeed(statusListCredentialString)
        }
      }

      val genericUriResolver = GenericUriResolver(
        Map(
          "data" -> DataUrlResolver(),
          "http" -> urlResolver,
          "https" -> urlResolver
        )
      )
      for {
        validation <- CredentialVerification.verifyCredentialStatus(statusList)(genericUriResolver)
      } yield assertTrue(validation.fold(_ => false, _ => true))
    },
    test("fail verification if proof is valid but credential is revoked at the give status list index") {
      val statusList: CredentialStatus = CredentialStatus(
        id = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9#1",
        statusPurpose = StatusPurpose.Revocation,
        `type` = "StatusList2021Entry",
        statusListCredential = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9",
        statusListIndex = 1
      )

      val urlResolver = new UriResolver {
        override def resolve(uri: String): IO[GenericUriResolverError, String] = {
          ZIO.succeed(statusListCredentialString)
        }
      }

      val genericUriResolver = GenericUriResolver(
        Map(
          "data" -> DataUrlResolver(),
          "http" -> urlResolver,
          "https" -> urlResolver
        )
      )
      for {
        validation <- CredentialVerification.verifyCredentialStatus(statusList)(genericUriResolver)
      } yield assertTrue(
        validation.fold(
          chunk => chunk.length == 1 && chunk.head.contentEquals("Credential is revoked"),
          _ => false
        )
      )
    },
    test("fail verification if proof is valid but credential is revoked at the give status list index given list") {
      val revokedStatus: List[CredentialStatus] = List(
        org.hyperledger.identus.pollux.vc.jwt.CredentialStatus(
          id = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9#1",
          statusPurpose = StatusPurpose.Revocation,
          `type` = "StatusList2021Entry",
          statusListCredential = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9",
          statusListIndex = 1
        ),
        org.hyperledger.identus.pollux.vc.jwt.CredentialStatus(
          id = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9#2",
          statusPurpose = StatusPurpose.Suspension,
          `type` = "StatusList2021Entry",
          statusListCredential = "http://localhost:8085/credential-status/664382dc-9e6d-4d0c-99d1-85e2c74eb5e9",
          statusListIndex = 1
        )
      )

      val urlResolver = new UriResolver {
        override def resolve(uri: String): IO[GenericUriResolverError, String] = {
          ZIO.succeed(statusListCredentialString)
        }
      }

      val genericUriResolver = GenericUriResolver(
        Map(
          "data" -> DataUrlResolver(),
          "http" -> urlResolver,
          "https" -> urlResolver
        )
      )
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer, credentialStatus = Some(revokedStatus))

      for {
        validation <- JwtCredential.verifyRevocationStatusJwt(jwtCredential)(genericUriResolver)
      } yield assertTrue(
        validation.fold(
          chunk =>
            chunk.length == 2 && chunk.head.contentEquals("Credential is revoked") && chunk.tail.head
              .contentEquals("Credential is revoked"),
          _ => false
        )
      )
    },
    test("validate dates happy path") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      for {
        validation <- ZIO.succeed(
          JwtCredential
            .verifyDates(jwtCredential, java.time.Duration.ZERO)(
              Clock.fixed(Instant.parse("2010-01-08T00:00:00Z"), ZoneId.systemDefault())
            )
        )
      } yield assertTrue(validation.fold(_ => false, _ => true))
    },
    test("validate issuer happy path") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer, false)
      val jwtCredentialWithObjectIssuer = createJwtCredential(issuer, true)
      for {
        jwt <- JwtCredential
          .decodeJwt(jwtCredential)
        jwtWithObjectIssuer <- JwtCredential
          .decodeJwt(jwtCredentialWithObjectIssuer)
        jwtWithObjectIssuerIssuer = jwtWithObjectIssuer.vc.maybeIssuer.get match {
          case string: String                     => string
          case credentialIssuer: CredentialIssuer => credentialIssuer.id
        }
        jwtIssuer = jwt.vc.maybeIssuer.get match {
          case string: String                     => string
          case credentialIssuer: CredentialIssuer => credentialIssuer.id
        }
      } yield assertTrue(
        jwtWithObjectIssuerIssuer.equals(jwtIssuer)
      )
    },
    test("validate credential status list") {
      val issuer = createUser("did:prism:issuer")
      val status = CredentialStatus(id = "id", `type` = "type", statusPurpose = Revocation, 1, "1")
      val encodedJwtWithStatusList = createJwtCredential(
        issuer,
        false,
        Some(List(status))
      )
      val econdedJwtWithStatusObject = createJwtCredential(issuer, true, Some(status))
      for {
        decodeJwtWithStatusList <- JwtCredential
          .decodeJwt(encodedJwtWithStatusList)
        decodeJwtWithStatusObject <- JwtCredential
          .decodeJwt(econdedJwtWithStatusObject)
        statusFromList = decodeJwtWithStatusList.vc.maybeCredentialStatus.map {
          case list: List[CredentialStatus] => list.head
          case _: CredentialStatus          => throw new IllegalStateException("List expected")
        }.get
        statusFromObjet = decodeJwtWithStatusObject.vc.maybeCredentialStatus.get
      } yield assertTrue(
        statusFromList.equals(statusFromObjet)
      )
    },
    test("validate dates should fail given after valid until") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      for {
        validation <- ZIO.succeed(
          JwtCredential
            .verifyDates(jwtCredential, java.time.Duration.ZERO)(
              Clock.fixed(Instant.parse("2010-01-10T00:00:00Z"), ZoneId.systemDefault())
            )
        )
      } yield assertTrue(validation.fold(_ => true, _ => false))
    },
    test("validate dates should fail given before valid from") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      for {
        validation <- ZIO.succeed(
          JwtCredential
            .verifyDates(jwtCredential, java.time.Duration.ZERO)(
              Clock.fixed(Instant.parse("2010-01-02T00:00:00Z"), ZoneId.systemDefault())
            )
        )
      } yield assertTrue(validation.fold(_ => true, _ => false))
    },
    test("validate PrismDID issued JWT VC using verification publicKeys") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              verificationMethod = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "EcdsaSecp256k1VerificationKey2019",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(issuer.key))
                )
              )
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential)(resolver)
      } yield assertTrue(validation.fold(_ => false, _ => true))
    },
    test("validate PrismDID issued JWT VC using specified proofPurpose resolved as embedded key") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              assertionMethod = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "EcdsaSecp256k1VerificationKey2019",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(issuer.key))
                )
              )
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential, Some(VerificationRelationship.AssertionMethod))(
          resolver
        )
      } yield assertTrue(validation.fold(_ => false, _ => true))
    },
    test("validate PrismDID issued JWT VC using specified proofPurpose resolved as referenced key") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              verificationMethod = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "EcdsaSecp256k1VerificationKey2019",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(issuer.key))
                )
              ),
              assertionMethod = Vector("did:prism:issuer#key0")
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential, Some(VerificationRelationship.AssertionMethod))(
          resolver
        )
      } yield assertTrue(validation.fold(_ => false, _ => true))
    },
    test("validate PrismDID issued JWT VC using incorrect proofPurpose should fail") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              authentication = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "EcdsaSecp256k1VerificationKey2019",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(issuer.key))
                )
              )
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential, Some(VerificationRelationship.AssertionMethod))(
          resolver
        )
      } yield assert(validation.fold(_ => false, _ => true))(equalTo(false))
    },
    test("validate PrismDID issued JWT VC using non-resolvable DID should fail") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(Map.empty)
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential)(resolver)
      } yield assert(validation.fold(_ => false, _ => true))(equalTo(false))
    },
    test("validate PrismDID issued JWT VC using non-existing public-key should fail") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(Map("did:prism:issuer" -> generateDidDocument(did = "did:prism:issuer")))
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential)(resolver)
      } yield assert(validation.fold(_ => false, _ => true))(equalTo(false))
    },
    test("validate PrismDID issued JWT VC using incompatible public-key type should fail") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              verificationMethod = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "ThisIsInvalidPublicKeyType",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(issuer.key))
                )
              )
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential)(resolver)
      } yield assert(validation.fold(_ => false, _ => true))(equalTo(false))
    },
    test("validate PrismDID issued JWT VC using different ECKey should fail") {
      val issuer = createUser("did:prism:issuer")
      val jwtCredential = createJwtCredential(issuer)
      val resolver = makeResolver(
        Map(
          "did:prism:issuer" ->
            generateDidDocument(
              did = "did:prism:issuer",
              verificationMethod = Vector(
                VerificationMethod(
                  id = "did:prism:issuer#key0",
                  `type` = "EcdsaSecp256k1VerificationKey2019",
                  controller = "did:prism:issuer",
                  publicKeyJwk = Some(toJWKFormat(ECKeyGenerator(Curve.SECP256K1).generate()))
                )
              )
            )
        )
      )
      for {
        validation <- JwtCredential.validateEncodedJWT(jwtCredential)(resolver)
      } yield assert(validation.fold(_ => false, _ => true))(equalTo(false))
    }
  )

}
