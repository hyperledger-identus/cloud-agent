import { Connection, ConnectionInvitation, IssueCredentialRecord } from '@hyperledger/identus-cloud-agent-client'
import { Actor } from './Actor'
import { HOLDER_AGENT_API_KEY, HOLDER_AGENT_URL } from '../common/Config'

export class Holder extends Actor {
  /**
   * The connection with the Issuer.
   */
  connectionWithIssuer: Connection | undefined

  /**
   * The connection with the Verifier.
   */
  connectionWithVerifier: Connection | undefined

  /**
   * The credential issued by the issuer.
   */
  credential: IssueCredentialRecord | undefined

  /**
   * The DID template used to create an unpublished DID for Holder.
   * Authentication is the only purpose for the public key required for Holder.
   */
  holderDidTemplate: string = `{
        "documentTemplate": {
            "publicKeys": [
                {
                    "id": "authKey",
                    "purpose": "authentication"
                }
            ],
            "services": []
        }
    }`

  /**
   * Creates a new instance of Holder.
   */
  constructor () {
    super(HOLDER_AGENT_URL, HOLDER_AGENT_API_KEY)
  }

  /**
   * Creates an unpublished DID.
   */
  createUnpublishedDid () {
    this.did = this.didService.createUnpublishedDid(this.holderDidTemplate).longFormDid
  }

  /**
   * Accepts a connection invitation from an issuer.
   * @param {ConnectionInvitation} invitation - The connection invitation.
   */
  acceptIssuerConnection (invitation: ConnectionInvitation) {
    this.connectionWithIssuer = this.connectionService.acceptConnectionInvitation(invitation)
  }

  /**
   * Accepts a connection invitation from a verifier.
   * @param {ConnectionInvitation} invitation - The connection invitation.
   */
  acceptVerifierConnection (invitation: ConnectionInvitation) {
    this.connectionWithVerifier = this.connectionService.acceptConnectionInvitation(invitation)
  }

  /**
   * Waits for the connection with the issuer to be finalized.
   */
  finalizeConnectionWithIssuer () {
    this.connectionService.waitForConnectionState(
      this.connectionWithIssuer!,
      'ConnectionResponseReceived'
    )
  }

  /**
   * Waits for the connection with the verifier to be finalized.
   */
  finalizeConnectionWithVerifier () {
    this.connectionService.waitForConnectionState(
      this.connectionWithVerifier!,
      'ConnectionResponseReceived'
    )
  }

  /**
   * Waits for a credential offer and accepts it.
   */
  waitAndAcceptCredentialOffer (thid: string) {
    this.credential = this.credentialsService.waitForCredentialOffer(thid)
    this.credentialsService.acceptCredentialOffer(this.credential, this.did!)
    this.credentialsService.waitForCredentialState(this.credential, 'RequestSent')
  }

  /**
   * Waits for the credential to be received.
   */
  receiveCredential () {
    this.credentialsService.waitForCredentialState(this.credential!, 'CredentialReceived')
  }

  /**
   * Waits for a proof request, accepts it, and waits for the presentation to be sent.
   */
  waitAndAcceptProofRequest (thid: string) {
    const presentation = this.proofsService.waitForProof(thid)
    this.proofsService.acceptProofRequest(presentation, this.credential!.recordId)
    this.proofsService.waitForPresentationState(presentation.presentationId, 'PresentationSent')
  }
}
