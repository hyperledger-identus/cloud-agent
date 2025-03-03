package org.hyperledger.identus.pollux.core.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.hyperledger.identus.agent.walletapi.service.MockManagedDIDService
import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.service.MockDIDService
import org.hyperledger.identus.mercury.model.{Base64 as MyBase64, *}
import org.hyperledger.identus.mercury.protocol.issuecredential.*
import org.hyperledger.identus.pollux.anoncreds.AnoncredCredential
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.error.CredentialServiceError.*
import org.hyperledger.identus.pollux.core.model.primitives.UriString
import org.hyperledger.identus.pollux.core.model.primitives.UriString.toUriString
import org.hyperledger.identus.pollux.core.model.schema.{CredentialDefinition, CredentialSchemaRef}
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchemaRefType
import org.hyperledger.identus.pollux.core.model.IssueCredentialRecord.{ProtocolState, Role}
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.vc.jwt.{CredentialIssuer, JWT, JwtCredential}
import org.hyperledger.identus.shared.models.{KeyId, UnmanagedFailureException, WalletAccessContext, WalletId}
import zio.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json
import zio.mock.MockSpecDefault
import zio.test.*
import zio.test.Assertion.*

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.Security
import java.util.{Base64, UUID}

object CredentialServiceImplSpec extends MockSpecDefault with CredentialServiceSpecHelper {
  Security.addProvider(new BouncyCastleProvider());
  override def spec = suite("CredentialServiceImpl")(
    singleWalletJWTCredentialSpec,
    singleWalletAnonCredsCredentialSpec,
    multiWalletSpec
  ).provideSomeLayer(
    MockDIDService.empty ++
      MockManagedDIDService.empty ++
      ResourceUrlResolver.layer >+>
      credentialServiceLayer ++
      ZLayer.succeed(WalletAccessContext(WalletId.random))
  )

  private val (issuerOp, issuerKp, issuerDidMetadata, issuerDidData) =
    MockDIDService.createDID(VerificationRelationship.AssertionMethod)

  private val (holderOp, holderKp, holderDidMetadata, holderDidData) =
    MockDIDService.createDID(VerificationRelationship.Authentication)

  private val holderDidServiceExpectations =
    MockDIDService.resolveDIDExpectation(holderDidMetadata, holderDidData)

  private val issuerDidServiceExpectations =
    MockDIDService.resolveDIDExpectation(issuerDidMetadata, issuerDidData)
      ++ MockDIDService.resolveDIDExpectation(holderDidMetadata, holderDidData)

  private val holderManagedDIDServiceExpectations =
    MockManagedDIDService.getManagedDIDStateExpectation(holderOp)
      ++ MockManagedDIDService.findDIDKeyPairExpectation(holderKp)

  private val issuerManagedDIDServiceExpectations =
    MockManagedDIDService.getManagedDIDStateExpectation(issuerOp)
      ++ MockManagedDIDService.findDIDKeyPairExpectation(issuerKp)

  private val singleWalletJWTCredentialSpec =
    suite("Single Wallet JWT Credential")(
      test("createIssuerCredentialRecord without schema creates a valid issuer credential record") {
        check(
          Gen.option(Gen.double),
          Gen.option(Gen.boolean)
        ) { (validityPeriod, automaticIssuance) =>
          for {
            svc <- ZIO.service[CredentialService]
            pairwiseIssuerDid = DidId("did:peer:INVITER")
            pairwiseHolderDid = Some(DidId("did:peer:HOLDER"))
            thid = DidCommID(UUID.randomUUID().toString)
            record <- svc.createJWTIssueCredentialRecord(
              thid = thid,
              pairwiseIssuerDID = pairwiseIssuerDid,
              pairwiseHolderDID = pairwiseHolderDid,
              credentialSchemaRef = None,
              validityPeriod = validityPeriod,
              automaticIssuance = automaticIssuance
            )
          } yield {
            assertTrue(record.thid == thid) &&
            assertTrue(record.updatedAt.isEmpty) &&
            assertTrue(record.schemaUris.getOrElse(List.empty).isEmpty) &&
            assertTrue(record.validityPeriod == validityPeriod) &&
            assertTrue(record.automaticIssuance == automaticIssuance) &&
            assertTrue(record.role == Role.Issuer) &&
            assertTrue(record.protocolState == ProtocolState.OfferPending) &&
            assertTrue(record.offerCredentialData.isDefined) &&
            assertTrue(record.offerCredentialData.get.from == pairwiseIssuerDid) &&
            assertTrue(record.offerCredentialData.get.to == pairwiseHolderDid) &&
            // FIXME: update the assertion when when CredentialOffer attachment is realized
            // assertTrue(record.offerCredentialData.get.attachments.isEmpty) &&
            assertTrue(record.offerCredentialData.get.thid.contains(thid.toString)) &&
            assertTrue(record.offerCredentialData.get.body.comment.isEmpty) &&
            assertTrue(record.offerCredentialData.get.body.goal_code.contains("Offer Credential")) &&
            assertTrue(record.offerCredentialData.get.body.multiple_available.isEmpty) &&
            assertTrue(record.offerCredentialData.get.body.replacement_id.isEmpty) &&
            assertTrue(record.offerCredentialData.get.attachments.headOption.flatMap(_.format).isDefined) &&
            assertTrue(
              record.offerCredentialData.get.body.credential_preview.body.attributes == Seq(
                Attribute("name", "Alice", None),
                Attribute(
                  "address",
                  Base64.getUrlEncoder.encodeToString(
                    """{"street": "Street Name", "number": "12"}"""
                      .fromJson[Json]
                      .toOption
                      .getOrElse(Json.Null)
                      .toJson
                      .getBytes(StandardCharsets.UTF_8)
                  ),
                  Some("application/json")
                )
              )
            ) &&
            assertTrue(record.requestCredentialData.isEmpty) &&
            assertTrue(record.issueCredentialData.isEmpty) &&
            assertTrue(record.issuedCredentialRaw.isEmpty)
          }
        }
      },
      test("createIssuerCredentialRecord with a schema and valid claims creates a valid issuer credential record") {
        check(
          Gen.option(Gen.double),
          Gen.option(Gen.boolean)
        ) { (validityPeriod, automaticIssuance) =>
          for {
            svc <- ZIO.service[CredentialService]
            pairwiseIssuerDid = DidId("did:peer:INVITER")
            pairwiseHolderDid = Some(DidId("did:peer:HOLDER"))
            claims = """
                |{
                |   "credentialSubject": {
                |     "emailAddress": "alice@wonderland.com",
                |     "givenName": "Alice",
                |     "familyName": "Wonderland",
                |     "dateOfIssuance": "2000-01-01T10:00:00Z",
                |     "drivingLicenseID": "12345",
                |     "drivingClass": 5
                |   }
                |}
                |""".stripMargin.fromJson[Json].toOption.getOrElse(Json.Null)
            thid = DidCommID(UUID.randomUUID().toString())
            record <- svc.createJWTIssueCredentialRecord(
              thid = thid,
              pairwiseIssuerDID = pairwiseIssuerDid,
              pairwiseHolderDID = pairwiseHolderDid,
              credentialSchemaRef = Some(
                CredentialSchemaRef(
                  `type` = CredentialSchemaRefType.JsonSchemaValidator2018,
                  id = new URI("resource:///vc-schema-example.json").toUriString
                )
              ),
              claims = claims,
              validityPeriod = validityPeriod,
              automaticIssuance = automaticIssuance
            )
            attributes <- CredentialService.convertJsonClaimsToAttributes(claims)
          } yield {
            assertTrue(record.thid == thid) &&
            assertTrue(record.updatedAt.isEmpty) &&
            assertTrue(
              record.schemaUris.getOrElse(List.empty).contains("resource:///vc-schema-example.json")
            ) &&
            assertTrue(record.validityPeriod == validityPeriod) &&
            assertTrue(record.automaticIssuance == automaticIssuance) &&
            assertTrue(record.role == Role.Issuer) &&
            assertTrue(record.protocolState == ProtocolState.OfferPending) &&
            assertTrue(record.offerCredentialData.isDefined) &&
            assertTrue(record.offerCredentialData.get.from == pairwiseIssuerDid) &&
            assertTrue(record.offerCredentialData.get.to == pairwiseHolderDid) &&
            // FIXME: update the assertion when when CredentialOffer attachment is realized
            // assertTrue(record.offerCredentialData.get.attachments.isEmpty) &&
            assertTrue(record.offerCredentialData.get.thid.contains(thid.toString)) &&
            assertTrue(record.offerCredentialData.get.body.comment.isEmpty) &&
            assertTrue(record.offerCredentialData.get.body.goal_code.contains("Offer Credential")) &&
            assertTrue(record.offerCredentialData.get.body.multiple_available.isEmpty) &&
            assertTrue(record.offerCredentialData.get.body.replacement_id.isEmpty) &&
            assertTrue(record.offerCredentialData.get.attachments.headOption.flatMap(_.format).isDefined) &&
            assertTrue(record.offerCredentialData.get.body.credential_preview.body.attributes == attributes) &&
            assertTrue(record.requestCredentialData.isEmpty) &&
            assertTrue(record.issueCredentialData.isEmpty) &&
            assertTrue(record.issuedCredentialRaw.isEmpty)
          }
        }
      },
      test("createIssuerCredentialRecord with a schema and invalid claims should fail") {
        check(
          Gen.option(Gen.double),
          Gen.option(Gen.boolean)
        ) { (validityPeriod, automaticIssuance) =>
          for {
            svc <- ZIO.service[CredentialService]
            pairwiseIssuerDid = DidId("did:peer:INVITER")
            pairwiseHolderDid = Some(DidId("did:peer:HOLDER"))
            claims = """
                |{
                |   "emailAddress": "alice@wonderland.com",
                |   "givenName": "Alice",
                |   "familyName": "Wonderland"
                |}
                |""".stripMargin.fromJson[Json].toOption.getOrElse(Json.Null)
            thid = DidCommID(UUID.randomUUID().toString)
            record <- svc
              .createJWTIssueCredentialRecord(
                thid = thid,
                pairwiseIssuerDID = pairwiseIssuerDid,
                pairwiseHolderDID = pairwiseHolderDid,
                credentialSchemaRef = Some(
                  CredentialSchemaRef(
                    `type` = CredentialSchemaRefType.JsonSchemaValidator2018,
                    id = new URI("resource:///vc-schema-example.json").toUriString
                  )
                ),
                claims = claims,
                validityPeriod = validityPeriod,
                automaticIssuance = automaticIssuance
              )
              .exit
          } yield {
            assertTrue(record match
              case Exit.Failure(Cause.Die(_: UnmanagedFailureException, _)) => true
              case _                                                        => false)
          }
        }
      },
      test("createIssuerCredentialRecord should reject creation with a duplicate 'thid'") {
        for {
          svc <- ZIO.service[CredentialService]
          thid = DidCommID()
          aRecord <- svc.createJWTIssueCredentialRecord(thid = thid)
          bRecord <- svc.createJWTIssueCredentialRecord(thid = thid).exit
        } yield {
          assert(bRecord)(dies(anything))
        }
      },
      test("getCredentialRecords returns the created records") {
        for {
          svc <- ZIO.service[CredentialService]
          aRecord <- svc.createJWTIssueCredentialRecord()
          bRecord <- svc.createJWTIssueCredentialRecord()
          records <- svc.getIssueCredentialRecords(false).map(_._1)
        } yield {
          assertTrue(records.size == 2) &&
          assertTrue(records.contains(aRecord)) &&
          assertTrue(records.contains(bRecord))
        }
      },
      test("getCredentialRecordsByState returns the correct records") {
        for {
          svc <- ZIO.service[CredentialService]
          aRecord <- svc.createJWTIssueCredentialRecord()
          records <- svc.getIssueCredentialRecordsByStates(
            ignoreWithZeroRetries = true,
            limit = 10,
            ProtocolState.OfferPending
          )
          onePending = assertTrue(records.size == 1) && assertTrue(records.contains(aRecord))
          records <- svc.getIssueCredentialRecordsByStates(
            ignoreWithZeroRetries = true,
            limit = 10,
            ProtocolState.OfferSent
          )
          zeroSent = assertTrue(records.isEmpty)
        } yield onePending && zeroSent
      },
      test("getCredentialRecord returns the correct record") {
        for {
          svc <- ZIO.service[CredentialService]
          aRecord <- svc.createJWTIssueCredentialRecord()
          bRecord <- svc.createJWTIssueCredentialRecord()
          record <- svc.findById(bRecord.id)
        } yield assertTrue(record.contains(bRecord))
      },
      test("getCredentialRecord returns nothing for an unknown 'recordId'") {
        for {
          svc <- ZIO.service[CredentialService]
          aRecord <- svc.createJWTIssueCredentialRecord()
          bRecord <- svc.createJWTIssueCredentialRecord()
          record <- svc.findById(DidCommID())
        } yield assertTrue(record.isEmpty)
      },
      test("receiveCredentialOffer successfully creates a record") {
        for {
          holderSvc <- ZIO.service[CredentialService]
          subjectId = "did:prism:subject"
          offer = offerCredential()
          holderRecord <- holderSvc.receiveCredentialOffer(offer)
        } yield {
          assertTrue(holderRecord.thid.toString == offer.thid.get) &&
          assertTrue(holderRecord.updatedAt.isEmpty) &&
          assertTrue(holderRecord.schemaUris.getOrElse(List.empty).isEmpty) &&
          assertTrue(holderRecord.validityPeriod.isEmpty) &&
          assertTrue(holderRecord.automaticIssuance.isEmpty) &&
          assertTrue(holderRecord.role == Role.Holder) &&
          assertTrue(holderRecord.protocolState == ProtocolState.OfferReceived) &&
          assertTrue(holderRecord.offerCredentialData.contains(offer)) &&
          assertTrue(holderRecord.requestCredentialData.isEmpty) &&
          assertTrue(holderRecord.issueCredentialData.isEmpty) &&
          assertTrue(holderRecord.issuedCredentialRaw.isEmpty)
        }
      },
      test("receiveCredentialOffer can't be called twice with the same offer") {
        for {
          holderSvc <- ZIO.service[CredentialService]
          offer = offerCredential()
          _ <- holderSvc.receiveCredentialOffer(offer)
          exit <- holderSvc.receiveCredentialOffer(offer).exit
        } yield {
          assert(exit)(dies(anything))
        }
      },
      test("acceptCredentialOffer updates the record's protocol state") {
        for {
          holderSvc <- ZIO.service[CredentialService]
          offer = offerCredential()
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          offerAcceptedRecord <- holderSvc
            .acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("my-key-id")))
        } yield {
          assertTrue(offerAcceptedRecord.protocolState == ProtocolState.RequestPending) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.isDefined) &&
          assertTrue(offerAcceptedRecord.subjectId.contains(subjectId)) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.from == offer.from) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.to == offer.to) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.attachments == offer.attachments) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.thid == offer.thid) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.body.comment.isEmpty) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.body.goal_code.contains("Offer Credential")) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.body.multiple_available.isEmpty) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.body.replacement_id.isEmpty) &&
          assertTrue(offerAcceptedRecord.offerCredentialData.get.attachments.headOption.flatMap(_.format).isDefined) &&
          assertTrue(
            offerAcceptedRecord.offerCredentialData.get.body.credential_preview.body.attributes == Seq(
              Attribute("name", "Alice", None)
            )
          )
        }
      },
      test("acceptCredentialOffer cannot be called twice for the same record") {
        for {
          holderSvc <- ZIO.service[CredentialService]
          offer = offerCredential()
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          _ <- holderSvc.acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("my-key-id")))
          exit <- holderSvc
            .acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("my-key-id")))
            .exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFound, _)) => true
            case _                                              => false)
        }
      },
      test("acceptCredentialOffer should reject unsupported `subjectId` format") {
        for {
          holderSvc <- ZIO.service[CredentialService]
          offer = offerCredential()
          subjectId = "did:unknown:subject"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          record <- holderSvc
            .acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("my-key-id")))
            .exit
        } yield {
          assertTrue(record match
            case Exit.Failure(Cause.Fail(_: UnsupportedDidFormat, _)) => true
            case _                                                    => false)
        }
      },
      test("receiveCredentialRequest successfully updates the record") {
        for {
          issuerSvc <- ZIO.service[CredentialService]
          issuerRecord <- issuerSvc.createJWTIssueCredentialRecord()
          _ <- issuerSvc.markOfferSent(issuerRecord.id)
          request = requestCredential(Some(issuerRecord.thid))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(request)
        } yield {
          assertTrue(requestReceivedRecord.protocolState == ProtocolState.RequestReceived) &&
          assertTrue(requestReceivedRecord.requestCredentialData.contains(request))
        }
      },
      test("receiveCredentialRequest cannot be called twice for the same record") {
        for {
          issuerSvc <- ZIO.service[CredentialService]
          issuerRecord <- issuerSvc.createJWTIssueCredentialRecord()
          _ <- issuerSvc.markOfferSent(issuerRecord.id)
          request = requestCredential(Some(issuerRecord.thid))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(request)
          exit <- issuerSvc.receiveCredentialRequest(request).exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFoundForThreadIdAndStates, _)) => true
            case _                                                                  => false)
        }
      },
      test("receiveCredentialRequest is rejected for an unknown 'thid'") {
        for {
          issuerSvc <- ZIO.service[CredentialService]
          issuerRecord <- issuerSvc.createJWTIssueCredentialRecord()
          _ <- issuerSvc.markOfferSent(issuerRecord.id)
          request = requestCredential(Some(DidCommID()))
          exit <- issuerSvc.receiveCredentialRequest(request).exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFoundForThreadIdAndStates, _)) => true
            case _                                                                  => false)
        }
      },
      test("acceptCredentialRequest successfully updates the record") {
        for {
          issuerSvc <- ZIO.service[CredentialService]
          issuerRecord <- issuerSvc.createJWTIssueCredentialRecord()
          _ <- issuerSvc.markOfferSent(issuerRecord.id)
          request = requestCredential(Some(issuerRecord.thid))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(request)
          requestAcceptedRecord <- issuerSvc.acceptCredentialRequest(requestReceivedRecord.id)
        } yield {
          assertTrue(requestAcceptedRecord.protocolState == ProtocolState.CredentialPending) &&
          assertTrue(requestAcceptedRecord.issueCredentialData.isDefined)
        }
      },
      test("acceptCredentialRequest cannot be called twice for the same record") {
        for {
          issuerSvc <- ZIO.service[CredentialService]
          issuerRecord <- issuerSvc.createJWTIssueCredentialRecord()
          _ <- issuerSvc.markOfferSent(issuerRecord.id)
          request = requestCredential(Some(issuerRecord.thid))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(request)
          requestAcceptedRecord <- issuerSvc.acceptCredentialRequest(requestReceivedRecord.id)
          exit <- issuerSvc.acceptCredentialRequest(requestReceivedRecord.id).exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFound, _)) => true
            case _                                              => false)
        }
      },
      test("receiveCredentialIssue successfully updates the record") {
        for {
          holderSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          offer = offerCredential()
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          _ <- holderSvc.acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("key-0")))
          _ <- holderSvc.generateJWTCredentialRequest(offerReceivedRecord.id)
          _ <- holderSvc.markRequestSent(offerReceivedRecord.id)
          issue = issueCredential(thid = Some(offerReceivedRecord.thid))
          credentialReceivedRecord <- holderSvc.receiveCredentialIssue(issue)
        } yield {
          assertTrue(credentialReceivedRecord.protocolState == ProtocolState.CredentialReceived) &&
          assertTrue(credentialReceivedRecord.issueCredentialData.contains(issue))
        }
      }.provideSomeLayer(holderDidServiceExpectations.toLayer ++ holderManagedDIDServiceExpectations.toLayer),
      test("receiveCredentialIssue cannot be called twice for the same record") {
        for {
          holderSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          offer = offerCredential()
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          _ <- holderSvc.acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("key-0")))
          _ <- holderSvc.generateJWTCredentialRequest(offerReceivedRecord.id)
          _ <- holderSvc.markRequestSent(offerReceivedRecord.id)
          issue = issueCredential(thid = Some(offerReceivedRecord.thid))
          _ <- holderSvc.receiveCredentialIssue(issue)
          exit <- holderSvc.receiveCredentialIssue(issue).exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFoundForThreadIdAndStates, _)) => true
            case _                                                                  => false)
        }
      }.provideSomeLayer(holderDidServiceExpectations.toLayer ++ holderManagedDIDServiceExpectations.toLayer),
      test("receiveCredentialIssue is rejected for an unknown 'thid'") {
        for {
          holderSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          offer = offerCredential()
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offer)
          _ <- holderSvc.acceptCredentialOffer(offerReceivedRecord.id, Some(subjectId), Some(KeyId("key-0")))
          _ <- holderSvc.generateJWTCredentialRequest(offerReceivedRecord.id)
          _ <- holderSvc.markRequestSent(offerReceivedRecord.id)
          issue = issueCredential(thid = Some(DidCommID()))
          exit <- holderSvc.receiveCredentialIssue(issue).exit
        } yield {
          assertTrue(exit match
            case Exit.Failure(Cause.Fail(_: RecordNotFoundForThreadIdAndStates, _)) => true
            case _                                                                  => false)
        }
      }.provideSomeLayer(holderDidServiceExpectations.toLayer ++ holderManagedDIDServiceExpectations.toLayer),
      test("Happy flow is successfully executed") {
        for {
          issuerSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          holderSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          // Issuer creates offer
          offerCreatedRecord <- issuerSvc.createJWTIssueCredentialRecord(kidIssuer = Some(KeyId("key-0")))
          issuerRecordId = offerCreatedRecord.id
          // Issuer sends offer
          _ <- issuerSvc.markOfferSent(issuerRecordId)
          msg <- ZIO.fromEither(offerCreatedRecord.offerCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives offer
          offerCredential <- ZIO.fromEither(OfferCredential.readFromMessage(msg))
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offerCredential)
          holderRecordId = offerReceivedRecord.id
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          // Holder accepts offer
          _ <- holderSvc.acceptCredentialOffer(holderRecordId, Some(subjectId), Some(KeyId("key-0")))
          // Holder generates proof
          requestGeneratedRecord <- holderSvc.generateJWTCredentialRequest(offerReceivedRecord.id)
          // Holder sends offer
          _ <- holderSvc.markRequestSent(holderRecordId)
          msg <- ZIO.fromEither(requestGeneratedRecord.requestCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Issuer receives request
          requestCredential <- ZIO.fromEither(RequestCredential.readFromMessage(msg))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(requestCredential)
          // Issuer accepts request
          requestAcceptedRecord <- issuerSvc.acceptCredentialRequest(issuerRecordId)
          // Issuer generates credential
          credentialGenerateRecord <- issuerSvc.generateJWTCredential(
            issuerRecordId,
            "status-list-registry"
          )
          decodedJWT <- credentialGenerateRecord.issueCredentialData.get.attachments.head.data match {
            case MyBase64(value) =>
              val ba = new String(Base64.getUrlDecoder.decode(value))
              JwtCredential.decodeJwt(JWT(ba))
            case _ => ZIO.fail("Error")
          }
          // Issuer sends credential
          _ <- issuerSvc.markCredentialSent(issuerRecordId)
          msg <- ZIO.fromEither(credentialGenerateRecord.issueCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives credential
          issueCredential <- ZIO.fromEither(IssueCredential.readFromMessage(msg))
          _ <- holderSvc.receiveCredentialIssue(issueCredential)
        } yield assertTrue(
          decodedJWT.issuer ==
            CredentialIssuer(
              id = decodedJWT.iss,
              `type` = "Profile"
            )
        )
      }.provideSomeLayer(
        (holderDidServiceExpectations ++ issuerDidServiceExpectations).toLayer
          ++ (holderManagedDIDServiceExpectations ++ issuerManagedDIDServiceExpectations).toLayer
      ),
      test("Happy flow is successfully executed with ED25519") {
        for {
          issuerSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          holderSvc <- ZIO.service[CredentialService].provideSomeLayer(credentialServiceLayer)
          // Issuer creates offer
          offerCreatedRecord <- issuerSvc.createJWTIssueCredentialRecord(kidIssuer = Some(KeyId("key-1")))
          issuerRecordId = offerCreatedRecord.id
          // Issuer sends offer
          _ <- issuerSvc.markOfferSent(issuerRecordId)
          msg <- ZIO.fromEither(offerCreatedRecord.offerCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives offer
          offerCredential <- ZIO.fromEither(OfferCredential.readFromMessage(msg))
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offerCredential)
          holderRecordId = offerReceivedRecord.id
          subjectId = "did:prism:60821d6833158c93fde5bb6a40d69996a683bf1fa5cdf32c458395b2887597c3"
          // Holder accepts offer
          _ <- holderSvc.acceptCredentialOffer(holderRecordId, Some(subjectId), Some(KeyId("key-1")))
          // Holder generates proof
          requestGeneratedRecord <- holderSvc.generateJWTCredentialRequest(offerReceivedRecord.id)
          // Holder sends offer
          _ <- holderSvc.markRequestSent(holderRecordId)
          msg <- ZIO.fromEither(requestGeneratedRecord.requestCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Issuer receives request
          requestCredential <- ZIO.fromEither(RequestCredential.readFromMessage(msg))
          requestReceivedRecord <- issuerSvc.receiveCredentialRequest(requestCredential)
          // Issuer accepts request
          requestAcceptedRecord <- issuerSvc.acceptCredentialRequest(issuerRecordId)
          // Issuer generates credential
          credentialGenerateRecord <- issuerSvc.generateJWTCredential(
            issuerRecordId,
            "status-list-registry"
          )
          decodedJWT <- credentialGenerateRecord.issueCredentialData.get.attachments.head.data match {
            case MyBase64(value) =>
              val ba = new String(Base64.getUrlDecoder.decode(value))
              JwtCredential.decodeJwt(JWT(ba))
            case _ => ZIO.fail("Error")
          }
          // Issuer sends credential
          _ <- issuerSvc.markCredentialSent(issuerRecordId)
          msg <- ZIO.fromEither(credentialGenerateRecord.issueCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives credential
          issueCredential <- ZIO.fromEither(IssueCredential.readFromMessage(msg))
          _ <- holderSvc.receiveCredentialIssue(issueCredential)
        } yield assertTrue(
          decodedJWT.issuer ==
            CredentialIssuer(
              id = decodedJWT.iss,
              `type` = "Profile"
            )
        )
      }.provideSomeLayer(
        (holderDidServiceExpectations ++ issuerDidServiceExpectations).toLayer
          ++ (holderManagedDIDServiceExpectations ++ issuerManagedDIDServiceExpectations).toLayer
      )
    )

  private val singleWalletAnonCredsCredentialSpec =
    suite("single Wallet AnonCreds Credential Spec")(
      test("Happy flow is successfully executed") {
        for {
          issuerServices <- (for {
            issuerCredDefService <- ZIO.service[CredentialDefinitionService]
            issuerSvc <- ZIO.service[CredentialService]
          } yield issuerCredDefService -> issuerSvc).provideSomeLayer(credentialServiceLayer)
          (issuerCredDefService, issuerSvc) = issuerServices
          // Issuer creates credential definition
          credDef <- issuerCredDefService.create(
            CredentialDefinition.Input(
              "Cred Def",
              "Description",
              "1.0",
              None,
              "Tag",
              "did:...",
              "resource:///anoncred-schema-example.json",
              "CL",
              false
            )
          )
          // Issuer creates offer
          credDefId = s"http://test.com/cred-defs/${credDef.guid.toString}"
          offerCreatedRecord <- issuerSvc.createAnonCredsIssueCredentialRecord(
            credentialDefinitionGUID = credDef.guid,
            credentialDefinitionId = credDefId
          )
          issuerRecordId = offerCreatedRecord.id
          // Issuer sends offer
          _ <- issuerSvc.markOfferSent(issuerRecordId)
          msg <- ZIO.fromEither(offerCreatedRecord.offerCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives offer
          holderCredDefResolverLayer = ZLayer.succeed(
            Map(s"http://test.com/cred-defs/${credDef.guid.toString}" -> credDef.definition.toString)
          )
          holderSvc <- ZIO
            .service[CredentialService]
            .provideSomeLayer(
              holderCredDefResolverLayer >>>
                ResourceUrlResolver.layerWithExtraResources >>>
                credentialServiceLayer
            )
          offerCredential <- ZIO.fromEither(OfferCredential.readFromMessage(msg))
          offerReceivedRecord <- holderSvc.receiveCredentialOffer(offerCredential)
          holderRecordId = offerReceivedRecord.id
          // Holder accepts offer
          _ <- holderSvc.acceptCredentialOffer(holderRecordId, None, None)
          // Holder generates proof
          requestGeneratedRecord <- holderSvc.generateAnonCredsCredentialRequest(offerReceivedRecord.id)
          // Holder sends offer
          _ <- holderSvc.markRequestSent(holderRecordId)
          msg <- ZIO.fromEither(requestGeneratedRecord.requestCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Issuer receives request
          requestCredential <- ZIO.fromEither(RequestCredential.readFromMessage(msg))
          _ <- issuerSvc.receiveCredentialRequest(requestCredential)
          // Issuer accepts request
          _ <- issuerSvc.acceptCredentialRequest(issuerRecordId)
          // Issuer generates credential
          credentialGenerateRecord <- issuerSvc.generateAnonCredsCredential(issuerRecordId)
          // Issuer sends credential
          _ <- issuerSvc.markCredentialSent(issuerRecordId)
          msg <- ZIO.fromEither(credentialGenerateRecord.issueCredentialData.get.makeMessage.toJson.fromJson[Message])
          // Holder receives credential\
          issueCredential <- ZIO.fromEither(IssueCredential.readFromMessage(msg))
          record <- holderSvc.receiveCredentialIssue(issueCredential)
        } yield {
          assertTrue(record.issueCredentialData.isDefined) &&
          assertTrue(record.issueCredentialData.get.attachments.nonEmpty) &&
          assertTrue(record.issueCredentialData.get.attachments.head.data match
            case MyBase64(value) =>
              val ba = new String(Base64.getUrlDecoder.decode(value))
              AnoncredCredential(ba).credDefId == credDefId
            case _ => false)
        }
      }
    )

  private val multiWalletSpec =
    suite("multi-wallet spec")(
      test("createIssueCredentialRecord for different wallet and isolate records") {
        val walletId1 = WalletId.random
        val walletId2 = WalletId.random
        val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
        val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
        for {
          svc <- ZIO.service[CredentialService]
          record1 <- svc.createJWTIssueCredentialRecord().provide(wallet1)
          record2 <- svc.createJWTIssueCredentialRecord().provide(wallet2)
          ownRecord1 <- svc.findById(record1.id).provide(wallet1)
          ownRecord2 <- svc.findById(record2.id).provide(wallet2)
          crossRecord1 <- svc.findById(record1.id).provide(wallet2)
          crossRecord2 <- svc.findById(record2.id).provide(wallet1)
        } yield assert(ownRecord1)(isSome(equalTo(record1))) &&
          assert(ownRecord2)(isSome(equalTo(record2))) &&
          assert(crossRecord1)(isNone) &&
          assert(crossRecord2)(isNone)
      }
    )

}
