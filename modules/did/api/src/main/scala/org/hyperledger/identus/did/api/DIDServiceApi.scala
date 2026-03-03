package org.hyperledger.identus.did.api

/** Re-exports from did.core model types for convenience.
  *
  * The actual model types, service traits, and error types now live in the did-api module (under their original
  * did.core.* packages for backward compatibility). These aliases provide a convenient shorthand for consumers that
  * prefer importing from did.api.
  */

// Service trait
type DIDService = org.hyperledger.identus.did.core.service.DIDService

// Error types
type DIDOperationError = org.hyperledger.identus.did.core.model.error.DIDOperationError
val DIDOperationError = org.hyperledger.identus.did.core.model.error.DIDOperationError

type DIDResolutionError = org.hyperledger.identus.did.core.model.error.DIDResolutionError
val DIDResolutionError = org.hyperledger.identus.did.core.model.error.DIDResolutionError

type OperationValidationError = org.hyperledger.identus.did.core.model.error.OperationValidationError
val OperationValidationError = org.hyperledger.identus.did.core.model.error.OperationValidationError

// Core model types
type PrismDID = org.hyperledger.identus.did.core.model.did.PrismDID
type CanonicalPrismDID = org.hyperledger.identus.did.core.model.did.CanonicalPrismDID
type LongFormPrismDID = org.hyperledger.identus.did.core.model.did.LongFormPrismDID
type DIDData = org.hyperledger.identus.did.core.model.did.DIDData
type DIDMetadata = org.hyperledger.identus.did.core.model.did.DIDMetadata
type ScheduleDIDOperationOutcome = org.hyperledger.identus.did.core.model.did.ScheduleDIDOperationOutcome
type ScheduledDIDOperationDetail = org.hyperledger.identus.did.core.model.did.ScheduledDIDOperationDetail
type SignedPrismDIDOperation = org.hyperledger.identus.did.core.model.did.SignedPrismDIDOperation
