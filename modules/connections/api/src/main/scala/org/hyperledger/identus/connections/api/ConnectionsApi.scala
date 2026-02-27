package org.hyperledger.identus.connections.api

/** Re-exports from connect.core for the connections bounded context API.
  *
  * These type aliases establish the public API surface for the Connections bounded context. Consumers should depend on
  * connections-api rather than connectCore directly. In a future phase, the actual types will be moved here and the
  * aliases reversed.
  */

// Service trait
type ConnectionService = org.hyperledger.identus.connect.core.service.ConnectionService

// Repository trait
type ConnectionRepository = org.hyperledger.identus.connect.core.repository.ConnectionRepository

// Core model types
type ConnectionRecord = org.hyperledger.identus.connect.core.model.ConnectionRecord
val ConnectionRecord = org.hyperledger.identus.connect.core.model.ConnectionRecord

type ConnectionRecordBeforeStored = org.hyperledger.identus.connect.core.model.ConnectionRecordBeforeStored

// Error types
type ConnectionServiceError = org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
val ConnectionServiceError = org.hyperledger.identus.connect.core.model.error.ConnectionServiceError
