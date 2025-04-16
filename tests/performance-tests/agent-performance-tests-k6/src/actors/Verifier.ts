import { Connection, PresentationStatus } from '@hyperledger/identus-cloud-agent-client'
import { Actor } from './Actor'
import { VERIFIER_AGENT_API_KEY, VERIFIER_AGENT_URL } from '../common/Config'

export class Verifier extends Actor {
  /**
   * The connection with the Holder.
   */
  connectionWithHolder: Connection | undefined

  /**
   * Presentation ID.
   */
  presentation: PresentationStatus | undefined

  /**
   * Creates a new instance of Verifier.
   */
  constructor () {
    super(VERIFIER_AGENT_URL, VERIFIER_AGENT_API_KEY)
  }

  /**
   * Creates a connection with the holder.
   */
  createHolderConnection () {
    this.connectionWithHolder = this.connectionService.createConnection()
  }

  /**
   * Waits for the connection with the holder to be finalized.
   */
  finalizeConnectionWithHolder () {
    this.connectionService.waitForConnectionState(
      this.connectionWithHolder!,
      'ConnectionResponseSent'
    )
  }

  /**
   * Requests proof from the holder.
   */
  requestProof () {
    const presentationId = this.proofsService.requestProof(this.connectionWithHolder!)
    this.presentation = this.proofsService.getPresentation(presentationId)
  }

  /**
   * Acknowledges the proof received from the holder.
   */
  acknowledgeProof () {
    this.proofsService.waitForPresentationState(this.presentation!.presentationId, 'PresentationVerified')
  }
}
