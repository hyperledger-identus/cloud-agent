package org.hyperledger.identus.pollux.core.service.verification

import org.hyperledger.identus.agent.walletapi.service.MockManagedDIDService
import org.hyperledger.identus.castor.core.service.MockDIDService
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.json.ast.Json
import zio.json.EncoderOps
import zio.test.*
import zio.Config.OffsetDateTime

import java.time.Instant

object VcVerificationServiceImplSpec extends ZIOSpecDefault with VcVerificationServiceSpecHelper {

  override def spec = {
    suite("VcVerificationServiceImpl")(
      test("verify aud given valid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.AudienceCheck(verifier))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.AudienceCheck(verifier), true)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify aud given invalid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.AudienceCheck(issuer.did.toString))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.AudienceCheck(issuer.did.toString), false)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify signature given valid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.SignatureVerification)
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.SignatureVerification, true)
            )
          )
        }
      }.provideSomeLayer(
        issuerDidServiceExpectations.toLayer ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify issuer given valid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(
                  signedJwtCredential.value,
                  VcVerification.IssuerIdentification(issuer.did.toString)
                )
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(
                signedJwtCredential.value,
                VcVerification.IssuerIdentification(issuer.did.toString),
                true
              )
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify issuer given invalid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.IssuerIdentification(verifier))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(
                signedJwtCredential.value,
                VcVerification.IssuerIdentification(verifier),
                false
              )
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify schema given single schema") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "resource:///vc-schema-personal.json",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Alice"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("alice@wonderland.com")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.SchemaCheck)
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(
                signedJwtCredential.value,
                VcVerification.SchemaCheck,
                true
              )
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify schema given multiple schema") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              List(
                CredentialSchema(
                  id = "resource:///vc-schema-personal.json",
                  `type` = "JsonSchemaValidator2018"
                ),
                CredentialSchema(
                  id = "resource:///vc-schema-driver-license.json",
                  `type` = "JsonSchemaValidator2018"
                )
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Alice"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("alice@wonderland.com"),
              "dateOfIssuance" -> Json.Str("2000-01-01T10:00:00Z"),
              "drivingLicenseID" -> Json.Num(12345),
              "drivingClass" -> Json.Str("5")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.SchemaCheck)
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(
                signedJwtCredential.value,
                VcVerification.SchemaCheck,
                true
              )
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify subject given multiple schema") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              List(
                CredentialSchema(
                  id = "resource:///vc-schema-personal.json",
                  `type` = "JsonSchemaValidator2018"
                ),
                CredentialSchema(
                  id = "resource:///vc-schema-driver-license.json",
                  `type` = "JsonSchemaValidator2018"
                )
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Alice"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("alice@wonderland.com"),
              "dateOfIssuance" -> Json.Str("2000-01-01T10:00:00Z"),
              "drivingLicenseID" -> Json.Num(12345),
              "drivingClass" -> Json.Num(5)
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.SubjectVerification)
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(
                signedJwtCredential.value,
                VcVerification.SubjectVerification,
                true
              )
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify nbf given valid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          currentTime = OffsetDateTime.parse("2010-01-01T00:00:00Z").toOption.get
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.NotBeforeCheck(currentTime))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.NotBeforeCheck(currentTime), true)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify nbf given invalid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          currentTime = OffsetDateTime.parse("2010-01-01T00:00:00Z").toOption.get.minusDays(2)
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.NotBeforeCheck(currentTime))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.NotBeforeCheck(currentTime), false)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify exp given valid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          currentTime = OffsetDateTime.parse("2010-01-01T00:00:00Z").toOption.get
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.ExpirationCheck(currentTime))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.ExpirationCheck(currentTime), true)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      ),
      test("verify exp given invalid") {
        for {
          svc <- ZIO.service[VcVerificationService]
          verifier = "did:prism:verifier"
          currentTime = OffsetDateTime.parse("2010-01-12T00:00:00Z").toOption.get
          jwtCredentialPayload = W3cCredentialPayload(
            `@context` =
              Set("https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1"),
            maybeId = Some("http://example.edu/credentials/3732"),
            `type` = Set("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = issuer.did.toString,
            issuanceDate = Instant.parse("2010-01-01T00:00:00Z"),
            maybeExpirationDate = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidFrom = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeValidUntil = Some(Instant.parse("2010-01-12T00:00:00Z")),
            maybeCredentialSchema = Some(
              CredentialSchema(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "JsonSchemaValidator2018"
              )
            ),
            credentialSubject = Json.Obj(
              "userName" -> Json.Str("Bob"),
              "age" -> Json.Num(42),
              "email" -> Json.Str("email")
            ),
            maybeCredentialStatus = Some(
              CredentialStatus(
                id = "did:work:MDP8AsFhHzhwUvGNuYkX7T;id=06e126d1-fa44-4882-a243-1e326fbe21db;version=1.0",
                `type` = "StatusList2021Entry",
                statusPurpose = StatusPurpose.Revocation,
                statusListIndex = 0,
                statusListCredential = "https://example.com/credentials/status/3"
              )
            ),
            maybeRefreshService = Some(
              RefreshService(
                id = "https://example.edu/refresh/3732",
                `type` = "ManualRefreshService2018"
              )
            ),
            maybeEvidence = Option.empty,
            maybeTermsOfUse = Option.empty,
            aud = Set(verifier)
          ).toJwtCredentialPayload
          signedJwtCredential = issuer.signer.encode(jwtCredentialPayload.toJsonAST.toOption.get)
          result <-
            svc.verify(
              List(
                VcVerificationRequest(signedJwtCredential.value, VcVerification.ExpirationCheck(currentTime))
              )
            )
        } yield {
          assertTrue(
            result.contains(
              VcVerificationResult(signedJwtCredential.value, VcVerification.ExpirationCheck(currentTime), false)
            )
          )
        }
      }.provideSomeLayer(
        MockDIDService.empty ++
          MockManagedDIDService.empty ++
          ResourceUrlResolver.layer >+>
          someVcVerificationServiceLayer ++
          ZLayer.succeed(WalletAccessContext(WalletId.random))
      )
    )
  }
}
