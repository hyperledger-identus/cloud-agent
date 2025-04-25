/* global __ENV */

import { Connection, ConnectionInvitation, ConnectionStateEnum } from '@hyperledger/identus-cloud-agent-client'
import { WAITING_LOOP_MAX_ITERATIONS, WAITING_LOOP_PAUSE_INTERVAL } from './Config'
import { HttpService, statusChangeTimeouts } from './HttpService'
import { sleep, fail } from 'k6'

/**
 * A service class for managing connections in the application.
 * Extends the HttpService class.
 */
export class ConnectionService extends HttpService {
  /**
   * Retrieves a specific connection by ID.
   * @param {string} connectionId - The ID of the connection.
   * @returns {Connection} The connection object.
   */
  getConnection (connectionId: string): Connection {
    const res = this.get(`connections/${connectionId}`)
    try {
      return this.toJson(res) as unknown as Connection
    } catch {
      fail('Failed to parse JSON as connection')
    }
  }

  /**
   * Creates a new connection.
   * @returns {Connection} The created connection object.
   */
  createConnection (): Connection {
    const payload = { label: 'test' }
    const res = this.post('connections', payload)
    try {
      return this.toJson(res) as unknown as Connection
    } catch {
      fail('Failed to parse JSON as connection')
    }
  }

  /**
   * Accepts a connection invitation.
   * @param {ConnectionInvitation} invitation - The connection invitation object.
   * @returns {Connection} The accepted connection object.
   */
  acceptConnectionInvitation (invitation: ConnectionInvitation): Connection {
    const payload = { invitation: this.invitationFromUrl(invitation.invitationUrl) }
    const res = this.post('connection-invitations', payload, 200)
    try {
      return this.toJson(res) as unknown as Connection
    } catch {
      fail('Failed to parse JSON as connection')
    }
  }

  /**
   * Waits for a connection to reach the required state.
   * @param {Connection} connection - The connection object.
   * @param {ConnectionStateEnum} requiredState - The required connection state.
   * @throws {Error} If the connection state does not reach the required state within the maximum iterations.
   */
  waitForConnectionState (connection: Connection, requiredState: ConnectionStateEnum) {
    let iterations = 0
    let state: ConnectionStateEnum
    do {
      state = this.getConnection(connection.connectionId).state
      if (__ENV.DEBUG) console.log(`Connection state: ${state}, required: ${requiredState}`)
      sleep(WAITING_LOOP_PAUSE_INTERVAL)
      iterations++
    } while (state !== requiredState && iterations < WAITING_LOOP_MAX_ITERATIONS)
    if (state !== requiredState) {
      statusChangeTimeouts.add(1)
      fail(`Connection state is ${state}, required ${requiredState}`)
    }
  }

  /**
   * Extracts the invitation code from a full invitation URL.
   * @param {string} fullUrl - The full invitation URL.
   * @returns {string} The invitation code.
   */
  invitationFromUrl (fullUrl: string) {
    return fullUrl.split('=')[1]
  }
}
