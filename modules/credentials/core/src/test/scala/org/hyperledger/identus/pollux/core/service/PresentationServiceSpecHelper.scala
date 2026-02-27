package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.castor.core.model.did.DID
import org.hyperledger.identus.mercury.{AgentPeerService, PeerDID}
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId}
import org.hyperledger.identus.mercury.protocol.presentproof.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.error.PresentationError
import org.hyperledger.identus.pollux.core.repository.*
import org.hyperledger.identus.pollux.core.service.serdes.*
import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.crypto.KmpSecp256k1KeyOps
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.messaging.{MessagingService, MessagingServiceConfig, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{WalletAccessContext, WalletId}
import zio.*

import java.time.Instant
import java.util.UUID

trait PresentationServiceSpecHelper {

  protected val defaultWalletLayer = ZLayer.succeed(WalletAccessContext(WalletId.default))

  val peerDidAgentLayer =
    AgentPeerService.makeLayer(PeerDID.makePeerDid(serviceEndpoint = Some("http://localhost:9099")))

  val genericSecretStorageLayer = GenericSecretStorageInMemory.layer
  val uriResolverLayer = ResourceUrlResolver.layer
  val credentialDefLayer =
    CredentialDefinitionRepositoryInMemory.layer ++ uriResolverLayer >>> CredentialDefinitionServiceImpl.layer
  val linkSecretLayer = genericSecretStorageLayer >+> LinkSecretServiceImpl.layer

  val presentationServiceLayer = ZLayer.make[
    PresentationService & CredentialDefinitionService & UriResolver & LinkSecretService & PresentationRepository &
      CredentialRepository
  ](
    PresentationServiceImpl.layer,
    credentialDefLayer,
    uriResolverLayer,
    linkSecretLayer,
    PresentationRepositoryInMemory.layer,
    CredentialRepositoryInMemory.layer,
    (MessagingServiceConfig.inMemoryLayer >>> MessagingService.serviceLayer >>>
      (zio.Scope.default >>> MessagingService.producerLayer[UUID, WalletIdAndRecordId])).orDie,
  ) ++ defaultWalletLayer

  def createIssuer(did: String): Issuer = {

    val keyPair = KmpSecp256k1KeyOps.generateKeyPair
    val javaSKey = keyPair.privateKey.toJavaPrivateKey
    val javaPKey = keyPair.publicKey.toJavaPublicKey

    Issuer(
      did = DID.fromString(did).toOption.get,
      signer = ES256KSigner(javaSKey),
      publicKey = javaPKey
    )
  }

  protected def requestCredential = org.hyperledger.identus.mercury.protocol.issuecredential.RequestCredential(
    from = DidId("did:prism:aaa"),
    to = DidId("did:prism:bbb"),
    thid = Some(UUID.randomUUID.toString),
    body = org.hyperledger.identus.mercury.protocol.issuecredential.RequestCredential
      .Body(goal_code = Some("credential issuance")),
    attachments = Nil
  )

  protected def requestPresentation(credentialFormat: PresentCredentialRequestFormat): RequestPresentation = {
    val body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
    val presentationAttachmentAsJson =
      """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
    val prover = Some(DidId("did:peer:Prover"))
    val verifier = Some(DidId("did:peer:Verifier"))

    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(
      payload = presentationAttachmentAsJson,
      format = Some(credentialFormat.name)
    )
    RequestPresentation(
      body = body,
      attachments = Seq(attachmentDescriptor),
      to = prover,
      from = verifier,
    )
  }

  protected def proposePresentation(thid: String): ProposePresentation = {
    val body = ProposePresentation.Body(goal_code = Some("Propose Presentation"))
    val presentationAttachmentAsJson =
      """{
                "id": "1f44d55f-f161-4938-a659-f8026467f126",
                "subject": "subject",
                "credential_definition": {}
            }"""
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(payload = presentationAttachmentAsJson)
    val prover = DidId("did:peer:Prover")
    val verifier = DidId("did:peer:Verifier")
    ProposePresentation(
      body = body,
      thid = Some(thid),
      attachments = Seq(attachmentDescriptor),
      to = verifier,
      from = prover
    )
  }

  protected def presentation(thid: String): Presentation = {
    val body = Presentation.Body(goal_code = Some("Presentation"))
    val presentationAttachmentAsJson =
      """{
                "id": "1f44d55f-f161-4938-a659-f8026467f126",
                "subject": "subject",
                "credential_definition": {}
            }"""
    val attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(payload = presentationAttachmentAsJson)
    val prover = DidId("did:peer:Prover")
    val verifier = DidId("did:peer:Verifier")
    Presentation(
      body = body,
      thid = Some(thid),
      attachments = Seq(attachmentDescriptor),
      to = verifier,
      from = prover
    )
  }

  protected def issueCredentialRecord(credentialFormat: CredentialFormat) = IssueCredentialRecord(
    id = DidCommID(),
    createdAt = Instant.now,
    updatedAt = None,
    thid = DidCommID(),
    schemaUris = None,
    credentialDefinitionId = None,
    credentialDefinitionUri = None,
    credentialFormat = credentialFormat,
    invitation = None,
    role = IssueCredentialRecord.Role.Issuer,
    subjectId = None,
    keyId = None,
    validityPeriod = None,
    automaticIssuance = None,
    protocolState = IssueCredentialRecord.ProtocolState.OfferPending,
    offerCredentialData = None,
    requestCredentialData = None,
    anonCredsRequestMetadata = None,
    issueCredentialData = None,
    issuedCredentialRaw = None,
    issuingDID = None,
    metaRetries = 5,
    metaNextRetry = Some(Instant.now()),
    metaLastFailure = None,
  )

  extension (svc: PresentationService)
    def createJwtRecord(
        pairwiseVerifierDID: DidId = DidId("did:prism:issuer"),
        pairwiseProverDID: DidId = DidId("did:prism:prover-pairwise"),
        thid: DidCommID = DidCommID(),
        schemaId: _root_.java.lang.String = "schemaId",
        options: Option[org.hyperledger.identus.pollux.core.model.presentation.Options] = None
    ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = {
      val proofType = ProofType(schemaId, None, None)
      svc.createJwtPresentationRecord(
        thid = thid,
        pairwiseVerifierDID = pairwiseVerifierDID,
        pairwiseProverDID = Some(pairwiseProverDID),
        connectionId = Some("connectionId"),
        proofTypes = Seq(proofType),
        options = options,
        presentationFormat = PresentCredentialRequestFormat.JWT,
        goalCode = None,
        goal = None,
        expirationDuration = None
      )
    }

    def createAnoncredRecord(
        credentialDefinitionId: String = "$CRED_DEF_ID",
        pairwiseVerifierDID: DidId = DidId("did:prism:issuer"),
        pairwiseProverDID: DidId = DidId("did:prism:prover-pairwise"),
        thid: DidCommID = DidCommID()
    ): ZIO[WalletAccessContext, PresentationError, PresentationRecord] = {
      val anoncredPresentationRequestV1 = AnoncredPresentationRequestV1(
        requested_attributes = Map(
          "sex" -> AnoncredRequestedAttributeV1(
            name = "sex",
            restrictions = List(
              Map(
                ("attr::sex::value" -> "M"),
                ("cred_def_id" -> credentialDefinitionId)
              )
            ),
            non_revoked = None
          )
        ),
        requested_predicates = Map(
          "age" -> AnoncredRequestedPredicateV1(
            name = "age",
            p_type = ">=",
            p_value = 18,
            restrictions = List.empty,
            non_revoked = None
          )
        ),
        name = "proof_req_1",
        nonce = "1103253414365527824079144",
        version = "0.1",
        non_revoked = None
      )
      svc.createAnoncredPresentationRecord(
        thid = thid,
        pairwiseVerifierDID = pairwiseVerifierDID,
        pairwiseProverDID = Some(pairwiseProverDID),
        connectionId = Some("connectionId"),
        presentationRequest = anoncredPresentationRequestV1,
        presentationFormat = PresentCredentialRequestFormat.Anoncred,
        goalCode = None,
        goal = None,
        expirationDuration = None
      )
    }
}
