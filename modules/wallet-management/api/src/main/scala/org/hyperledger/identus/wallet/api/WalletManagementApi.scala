package org.hyperledger.identus.wallet.api

/** Re-exports from agent.walletapi for the wallet-management bounded context API.
  *
  * These type aliases establish the public API surface for the Wallet Management bounded context. Consumers should
  * depend on wallet-management-api rather than cloudAgentWalletAPI directly.
  *
  * Critical: This module enables polluxCore to depend on wallet-management-api instead of the full cloudAgentWalletAPI,
  * breaking the reverse dependency cycle identified in the architectural constraints.
  */

// Service traits
type ManagedDIDService = org.hyperledger.identus.wallet.service.ManagedDIDService
val ManagedDIDService = org.hyperledger.identus.wallet.service.ManagedDIDService

type WalletManagementService = org.hyperledger.identus.wallet.service.WalletManagementService

type EntityService = org.hyperledger.identus.wallet.service.EntityService

// Storage port traits
type GenericSecretStorage = org.hyperledger.identus.wallet.storage.GenericSecretStorage
type GenericSecret[K, V] = org.hyperledger.identus.wallet.storage.GenericSecret[K, V]
type DIDNonSecretStorage = org.hyperledger.identus.wallet.storage.DIDNonSecretStorage
type DIDSecretStorage = org.hyperledger.identus.wallet.storage.DIDSecretStorage
type WalletNonSecretStorage = org.hyperledger.identus.wallet.storage.WalletNonSecretStorage
type WalletSecretStorage = org.hyperledger.identus.wallet.storage.WalletSecretStorage

// Core model types
type ManagedDIDState = org.hyperledger.identus.wallet.model.ManagedDIDState
type ManagedDIDDetail = org.hyperledger.identus.wallet.model.ManagedDIDDetail
type PublicationState = org.hyperledger.identus.wallet.model.PublicationState
val PublicationState = org.hyperledger.identus.wallet.model.PublicationState

type Wallet = org.hyperledger.identus.wallet.model.Wallet
val Wallet = org.hyperledger.identus.wallet.model.Wallet

type Entity = org.hyperledger.identus.wallet.model.Entity
val Entity = org.hyperledger.identus.wallet.model.Entity

type WalletSeed = org.hyperledger.identus.wallet.model.WalletSeed
val WalletSeed = org.hyperledger.identus.wallet.model.WalletSeed

// Error types
type WalletManagementServiceError = org.hyperledger.identus.wallet.service.WalletManagementServiceError
val WalletManagementServiceError = org.hyperledger.identus.wallet.service.WalletManagementServiceError
