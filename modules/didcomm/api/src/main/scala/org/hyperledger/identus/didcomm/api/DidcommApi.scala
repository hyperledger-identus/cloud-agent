package org.hyperledger.identus.didcomm.api

/** Re-exports from didcomm.model for the DIDComm bounded context API.
  *
  * These type aliases establish the public API surface for the DIDComm bounded context. Consumers should depend on
  * didcomm-api rather than didcomm/models directly. In a future phase, the actual types will be moved here and the
  * aliases reversed.
  */

// Core identity type
type DidId = org.hyperledger.identus.didcomm.model.DidId
val DidId = org.hyperledger.identus.didcomm.model.DidId

// Protocol URI type
type PIURI = org.hyperledger.identus.didcomm.model.PIURI

// Core message types
type Message = org.hyperledger.identus.didcomm.model.Message
val Message = org.hyperledger.identus.didcomm.model.Message

type AttachmentDescriptor = org.hyperledger.identus.didcomm.model.AttachmentDescriptor
val AttachmentDescriptor = org.hyperledger.identus.didcomm.model.AttachmentDescriptor

// Attachment data variants
type AttachmentData = org.hyperledger.identus.didcomm.model.AttachmentData
type Base64 = org.hyperledger.identus.didcomm.model.Base64
val Base64 = org.hyperledger.identus.didcomm.model.Base64
type JsonData = org.hyperledger.identus.didcomm.model.JsonData
val JsonData = org.hyperledger.identus.didcomm.model.JsonData
type LinkData = org.hyperledger.identus.didcomm.model.LinkData
val LinkData = org.hyperledger.identus.didcomm.model.LinkData
type JwsData = org.hyperledger.identus.didcomm.model.JwsData
val JwsData = org.hyperledger.identus.didcomm.model.JwsData

// Agent types
type DidAgent = org.hyperledger.identus.didcomm.DidAgent
type SignedMesage = org.hyperledger.identus.didcomm.model.SignedMesage
type EncryptedMessage = org.hyperledger.identus.didcomm.model.EncryptedMessage
type UnpackMessage = org.hyperledger.identus.didcomm.model.UnpackMessage

// Operations trait
type DidOps = org.hyperledger.identus.didcomm.DidOps

// Media types
val MediaTypes = org.hyperledger.identus.didcomm.MediaTypes
