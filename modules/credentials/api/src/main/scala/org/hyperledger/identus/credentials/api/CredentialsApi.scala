package org.hyperledger.identus.credentials.api

/** Re-exports from pollux.core for the credentials bounded context API.
  *
  * These type aliases establish the public API surface for the Credentials bounded context. Consumers should depend on
  * credentials-api rather than polluxCore directly. In a future phase, the actual types will be moved here and the
  * aliases reversed.
  */

// Service traits
type CredentialService = org.hyperledger.identus.credentials.core.service.CredentialService
val CredentialService = org.hyperledger.identus.credentials.core.service.CredentialService

type PresentationService = org.hyperledger.identus.credentials.core.service.PresentationService

// Core model types
type DidCommID = org.hyperledger.identus.credentials.core.model.DidCommID
val DidCommID = org.hyperledger.identus.credentials.core.model.DidCommID

type IssueCredentialRecord = org.hyperledger.identus.credentials.core.model.IssueCredentialRecord
val IssueCredentialRecord = org.hyperledger.identus.credentials.core.model.IssueCredentialRecord

type PresentationRecord = org.hyperledger.identus.credentials.core.model.PresentationRecord
val PresentationRecord = org.hyperledger.identus.credentials.core.model.PresentationRecord

type CredentialFormat = org.hyperledger.identus.credentials.core.model.CredentialFormat
val CredentialFormat = org.hyperledger.identus.credentials.core.model.CredentialFormat

// Repository traits
type CredentialRepository = org.hyperledger.identus.credentials.core.repository.CredentialRepository
type PresentationRepository = org.hyperledger.identus.credentials.core.repository.PresentationRepository

// Error types
type CredentialServiceError = org.hyperledger.identus.credentials.core.model.error.CredentialServiceError
val CredentialServiceError = org.hyperledger.identus.credentials.core.model.error.CredentialServiceError

type PresentationError = org.hyperledger.identus.credentials.core.model.error.PresentationError
val PresentationError = org.hyperledger.identus.credentials.core.model.error.PresentationError
