import { ConnectionService } from '../common/ConnectionService'
import { CredentialsService } from '../common/CredentialsService'
import { ProofsService } from '../common/ProofsService'
import { DidService } from '../common/DidService'

/**
 * Base class for all SSI actors
 */
export class Actor {
  connectionService: ConnectionService
  credentialsService: CredentialsService
  proofsService: ProofsService
  didService: DidService
  longFormDid: string | undefined
  did: string | undefined

  /**
   * Constructs a new instance of the Actor class.
   * @param {string} url - The URL for the services.
   * @param {string} apiKey - The API key for authentication.
   */
  constructor (url: string, apiKey: string) {
    this.connectionService = new ConnectionService(url, apiKey)
    this.credentialsService = new CredentialsService(url, apiKey)
    this.didService = new DidService(url, apiKey)
    this.proofsService = new ProofsService(url, apiKey)
  }
}
