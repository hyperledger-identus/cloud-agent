package org.hyperledger.identus.did.api

/** Re-exports from castor.core for the did bounded context API.
  *
  * These type aliases establish the public API surface for the DID bounded context. Consumers should depend on did-api
  * rather than castorCore directly. In a future phase, the actual types will be moved here and the aliases reversed.
  */

// Service trait
type DIDService = org.hyperledger.identus.castor.core.service.DIDService

// Error types
type DIDOperationError = org.hyperledger.identus.castor.core.model.error.DIDOperationError
val DIDOperationError = org.hyperledger.identus.castor.core.model.error.DIDOperationError

type DIDResolutionError = org.hyperledger.identus.castor.core.model.error.DIDResolutionError
val DIDResolutionError = org.hyperledger.identus.castor.core.model.error.DIDResolutionError

type OperationValidationError = org.hyperledger.identus.castor.core.model.error.OperationValidationError
val OperationValidationError = org.hyperledger.identus.castor.core.model.error.OperationValidationError

// Core model types
type PrismDID = org.hyperledger.identus.castor.core.model.did.PrismDID
type CanonicalPrismDID = org.hyperledger.identus.castor.core.model.did.CanonicalPrismDID
type LongFormPrismDID = org.hyperledger.identus.castor.core.model.did.LongFormPrismDID
type DIDData = org.hyperledger.identus.castor.core.model.did.DIDData
type DIDMetadata = org.hyperledger.identus.castor.core.model.did.DIDMetadata
type ScheduleDIDOperationOutcome = org.hyperledger.identus.castor.core.model.did.ScheduleDIDOperationOutcome
type ScheduledDIDOperationDetail = org.hyperledger.identus.castor.core.model.did.ScheduledDIDOperationDetail
type SignedPrismDIDOperation = org.hyperledger.identus.castor.core.model.did.SignedPrismDIDOperation
