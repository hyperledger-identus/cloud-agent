/* global __ENV */

import { HttpService, statusChangeTimeouts } from './HttpService'
import { WAITING_LOOP_MAX_ITERATIONS, WAITING_LOOP_PAUSE_INTERVAL } from './Config'
import {
  CreateManagedDIDResponse,
  DIDDocument,
  DidOperationSubmission,
  ManagedDID
} from '@hyperledger/identus-cloud-agent-client'
import { fail, sleep } from 'k6'

/**
 * A service class for managing decentralized identifiers (DIDs) in the application.
 * Extends the HttpService class.
 */
export class DidService extends HttpService {
  /**
   * Retrieves information about a specific DID.
   * @param {string} did - The DID.
   * @returns {ManagedDID} The managed DID object.
   */
  getDid (did: string): ManagedDID {
    const res = this.get(`did-registrar/dids/${did}`)
    try {
      return this.toJson(res) as unknown as ManagedDID
    } catch {
      fail('Failed to parse JSON as ManagedDID')
    }
  }

  /**
   * Resolves a specific DID to obtain its DID document.
   * @param {string} did - The DID.
   * @returns {DIDDocument} The resolved DID document.
   */
  resolveDid (did: string): DIDDocument {
    const res = this.get(`dids/${did}`)
    try {
      return this.toJson(res) as unknown as DIDDocument
    } catch {
      fail('Failed to parse JSON as DIDDocument')
    }
  }

  /**
   * Publishes a specific DID.
   * @param {string} did - The DID to be published.
   * @returns {DidOperationSubmission} The submission for the DID publication.
   */
  publishDid (did: string): DidOperationSubmission {
    const res = this.post(`did-registrar/dids/${did}/publications`, null, 202)
    try {
      return this.toJson(res).scheduledOperation as unknown as DidOperationSubmission
    } catch {
      fail('Failed to parse JSON as DidOperationSubmission')
    }
  }

  /**
   * Creates an unpublished DID using a document template.
   * @param {string} documentTemplate - The document template for the unpublished DID.
   * @returns {CreateManagedDIDResponse} The response containing the created managed DID.
   */
  createUnpublishedDid (documentTemplate: string): CreateManagedDIDResponse {
    const res = this.post('did-registrar/dids', documentTemplate)
    try {
      return this.toJson(res) as unknown as CreateManagedDIDResponse
    } catch {
      fail('Failed to parse JSON as CreateManagedDIDResponse')
    }
  }

  /**
   * Waits for a DID to reach a specific state.
   * @param {string} credentialRecord - The credential record.
   * @param {string} state - The required state.
   */
  waitForDidState (credentialRecord: string, state: string) {
    let iterations = 0
    let didState
    do {
      const did = this.getDid(credentialRecord)
      didState = did.status
      if (__ENV.DEBUG) console.log(`DID state: ${didState}`)
      sleep(WAITING_LOOP_PAUSE_INTERVAL)
      iterations++
    } while (didState !== state && iterations < WAITING_LOOP_MAX_ITERATIONS)
    if (didState !== state) {
      statusChangeTimeouts.add(1)
      fail(`DID is not ${state} after the waiting loop`)
    }
  }
}
