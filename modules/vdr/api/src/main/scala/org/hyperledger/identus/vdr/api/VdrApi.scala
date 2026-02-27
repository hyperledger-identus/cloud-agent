package org.hyperledger.identus.vdr.api

/** Re-exports from agent.vdr for the VDR bounded context API.
  *
  * These type aliases establish the public API surface for the VDR bounded context. Consumers should depend on vdr-api
  * rather than vdrCore directly. In a future phase, the actual types will be moved here and the aliases reversed.
  */

// Service trait
type VdrService = org.hyperledger.identus.agent.vdr.VdrService

// Core types
type VdrOperationResult = org.hyperledger.identus.agent.vdr.VdrOperationResult
val VdrOperationResult = org.hyperledger.identus.agent.vdr.VdrOperationResult

type VdrOperationStatus = org.hyperledger.identus.agent.vdr.VdrOperationStatus
val VdrOperationStatus = org.hyperledger.identus.agent.vdr.VdrOperationStatus

type VdrUrl = org.hyperledger.identus.agent.vdr.VdrUrl
type VdrOptions = org.hyperledger.identus.agent.vdr.VdrOptions

// Error types
type VdrServiceError = org.hyperledger.identus.agent.vdr.VdrServiceError
val VdrServiceError = org.hyperledger.identus.agent.vdr.VdrServiceError
