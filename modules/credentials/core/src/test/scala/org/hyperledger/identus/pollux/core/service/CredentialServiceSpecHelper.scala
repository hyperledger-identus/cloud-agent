package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.agent.walletapi.memory.GenericSecretStorageInMemory
import org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
import org.hyperledger.identus.castor.core.model.did.PrismDID
import org.hyperledger.identus.castor.core.service.DIDService
import org.hyperledger.identus.mercury.model.{AttachmentDescriptor, DidId}
import org.hyperledger.identus.mercury.protocol.issuecredential.*
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.core.model.presentation.Options
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchemaRef
import org.hyperledger.identus.pollux.core.repository.{
  CredentialDefinitionRepositoryInMemory,
  CredentialRepositoryInMemory,
  CredentialStatusListRepositoryInMemory
}
import org.hyperledger.identus.pollux.prex.{ClaimFormat, Ldp, PresentationDefinition}
import org.hyperledger.identus.pollux.vc.jwt.*
import org.hyperledger.identus.shared.http.UriResolver
import org.hyperledger.identus.shared.messaging.{MessagingService, MessagingServiceConfig, WalletIdAndRecordId}
import org.hyperledger.identus.shared.models.{KeyId, WalletAccessContext, WalletId}
import zio.*
import zio.json.ast.Json
import zio.json.DecoderOps

import java.util.UUID

trait CredentialServiceSpecHelper {

  protected val didResolverLayer = ZLayer.fromZIO(ZIO.succeed(makeResolver(Map.empty)))

  protected val defaultWalletLayer = ZLayer.succeed(WalletAccessContext(WalletId.default))

  protected val credentialDefinitionServiceLayer =
    CredentialDefinitionRepositoryInMemory.layer >>> CredentialDefinitionServiceImpl.layer

  protected val credentialServiceLayer
      : URLayer[DIDService & ManagedDIDService & UriResolver, CredentialService & CredentialDefinitionService] =
    ZLayer.makeSome[DIDService & ManagedDIDService & UriResolver, CredentialService & CredentialDefinitionService](
      CredentialRepositoryInMemory.layer,
      CredentialStatusListRepositoryInMemory.layer,
      ZLayer.fromFunction(PrismDidResolver(_)),
      credentialDefinitionServiceLayer,
      GenericSecretStorageInMemory.layer,
      LinkSecretServiceImpl.layer,
      (MessagingServiceConfig.inMemoryLayer >>> MessagingService.serviceLayer >>>
        (zio.Scope.default >>> MessagingService.producerLayer[UUID, WalletIdAndRecordId])).orDie,
      CredentialServiceImpl.layer
    )

  protected def offerCredential(
      thid: Option[UUID] = Some(UUID.randomUUID())
  ) = OfferCredential(
    from = DidId("did:prism:issuer"),
    to = Some(DidId("did:prism:holder")),
    thid = thid.map(_.toString),
    attachments = Seq(
      AttachmentDescriptor.buildJsonAttachment(
        format = Some(IssueCredentialOfferFormat.JWT.name),
        payload = CredentialOfferAttachment(
          Options(UUID.randomUUID().toString(), "my-domain"),
          PresentationDefinition(format = Some(ClaimFormat(ldp = Some(Ldp(Seq("EcdsaSecp256k1Signature2019"))))))
        )
      )
    ),
    body = OfferCredential.Body(
      goal_code = Some("Offer Credential"),
      credential_preview = CredentialPreview(attributes = Seq(Attribute("name", "Alice")))
    )
  )

  protected def requestCredential(thid: Option[DidCommID] = Some(DidCommID())) = RequestCredential(
    from = DidId("did:prism:holder"),
    to = DidId("did:prism:issuer"),
    thid = thid.map(_.toString),
    attachments = Nil,
    body = RequestCredential.Body()
  )

  protected def issueCredential(thid: Option[DidCommID] = Some(DidCommID())) = IssueCredential(
    from = DidId("did:prism:issuer"),
    to = DidId("did:prism:holder"),
    thid = thid.map(_.toString),
    attachments = Seq(
      AttachmentDescriptor.buildBase64Attachment(
        UUID.randomUUID().toString,
        payload = "VC content".getBytes,
        mediaType = Some("application/json"),
        format = Some(IssueCredentialIssuedFormat.JWT.name)
      )
    ),
    body = IssueCredential.Body()
  )

  protected def makeResolver(lookup: Map[String, DIDDocument]): DidResolver = (didUrl: String) => {
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

  extension (svc: CredentialService)
    def createJWTIssueCredentialRecord(
        pairwiseIssuerDID: DidId = DidId("did:prism:issuer"),
        pairwiseHolderDID: Option[DidId] = Some(DidId("did:prism:holder-pairwise")),
        thid: DidCommID = DidCommID(),
        credentialSchemaRef: Option[CredentialSchemaRef] = None,
        claims: Json = """
              |{
              | "name":"Alice",
              | "address": {
              |   "street": "Street Name",
              |   "number": "12"
              | }
              |}
              |""".stripMargin.fromJson[Json].toOption.getOrElse(Json.Null),
        validityPeriod: Option[Double] = None,
        automaticIssuance: Option[Boolean] = None,
        kidIssuer: Option[KeyId] = None
    ) = for {
      issuingDID <- ZIO.fromEither(
        PrismDID.buildCanonicalFromSuffix("5c2576867a5544e5ad05cdc94f02c664b99ff65c28e8b62aada767244c2199fe")
      )
      record <- svc.createJWTIssueCredentialRecord(
        pairwiseIssuerDID = pairwiseIssuerDID,
        pairwiseHolderDID = pairwiseHolderDID,
        kidIssuer = kidIssuer,
        thid = thid,
        credentialSchemaRef = credentialSchemaRef,
        claims = claims,
        validityPeriod = validityPeriod,
        automaticIssuance = automaticIssuance,
        issuingDID = issuingDID,
        goalCode = None,
        goal = None,
        expirationDuration = None,
        connectionId = Some(UUID.randomUUID()),
        domain = "domain"
      )
    } yield record

    def createAnonCredsIssueCredentialRecord(
        credentialDefinitionGUID: UUID,
        pairwiseIssuerDID: DidId = DidId("did:prism:issuer"),
        pairwiseHolderDID: Option[DidId] = Some(DidId("did:prism:holder-pairwise")),
        thid: DidCommID = DidCommID(),
        claims: Json = """
                |{
                |  "emailAddress": "alice@wonderland.com",
                |  "familyName": "Wonderland",
                |  "dateOfIssuance": "2020-11-13T20:20:39+00:00",
                |  "drivingLicenseID": "12345",
                |  "drivingClass": "3"
                |}
                |""".stripMargin.fromJson[Json].toOption.getOrElse(Json.Null),
        validityPeriod: Option[Double] = None,
        automaticIssuance: Option[Boolean] = None,
        credentialDefinitionId: String = "http://test.com/cred-def/1234",
    ) = for {
      record <- svc.createAnonCredsIssueCredentialRecord(
        pairwiseIssuerDID = pairwiseIssuerDID,
        pairwiseHolderDID = pairwiseHolderDID,
        thid = thid,
        claims = claims,
        validityPeriod = validityPeriod,
        automaticIssuance = automaticIssuance,
        credentialDefinitionGUID = credentialDefinitionGUID,
        credentialDefinitionId = credentialDefinitionId,
        goalCode = None,
        goal = None,
        expirationDuration = None,
        connectionId = Some(UUID.randomUUID())
      )
    } yield record

}
