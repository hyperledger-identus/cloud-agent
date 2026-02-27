package org.hyperledger.identus.didcomm.api

/** Re-exports from mercury.model for the didcomm bounded context API.
  *
  * These type aliases establish the public API surface for the DIDComm bounded context. Consumers should depend on
  * didcomm-api rather than mercury/models directly. In a future phase, the actual types will be moved here and the
  * aliases reversed.
  */

// Core identity type
type DidId = org.hyperledger.identus.mercury.model.DidId
val DidId = org.hyperledger.identus.mercury.model.DidId

// Protocol URI type
type PIURI = org.hyperledger.identus.mercury.model.PIURI

// Core message types
type Message = org.hyperledger.identus.mercury.model.Message
val Message = org.hyperledger.identus.mercury.model.Message

type AttachmentDescriptor = org.hyperledger.identus.mercury.model.AttachmentDescriptor
val AttachmentDescriptor = org.hyperledger.identus.mercury.model.AttachmentDescriptor

// Attachment data variants
type AttachmentData = org.hyperledger.identus.mercury.model.AttachmentData
type Base64 = org.hyperledger.identus.mercury.model.Base64
val Base64 = org.hyperledger.identus.mercury.model.Base64
type JsonData = org.hyperledger.identus.mercury.model.JsonData
val JsonData = org.hyperledger.identus.mercury.model.JsonData
type LinkData = org.hyperledger.identus.mercury.model.LinkData
val LinkData = org.hyperledger.identus.mercury.model.LinkData
type JwsData = org.hyperledger.identus.mercury.model.JwsData
val JwsData = org.hyperledger.identus.mercury.model.JwsData

// Agent types
type DidAgent = org.hyperledger.identus.mercury.DidAgent
type SignedMesage = org.hyperledger.identus.mercury.model.SignedMesage
type EncryptedMessage = org.hyperledger.identus.mercury.model.EncryptedMessage
type UnpackMessage = org.hyperledger.identus.mercury.model.UnpackMessage

// Operations trait
type DidOps = org.hyperledger.identus.mercury.DidOps

// Media types
val MediaTypes = org.hyperledger.identus.mercury.MediaTypes
