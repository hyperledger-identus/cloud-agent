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
type ManagedDIDService = org.hyperledger.identus.agent.walletapi.service.ManagedDIDService
val ManagedDIDService = org.hyperledger.identus.agent.walletapi.service.ManagedDIDService

type WalletManagementService = org.hyperledger.identus.agent.walletapi.service.WalletManagementService

type EntityService = org.hyperledger.identus.agent.walletapi.service.EntityService

// Storage port traits
type GenericSecretStorage = org.hyperledger.identus.agent.walletapi.storage.GenericSecretStorage
type GenericSecret[K, V] = org.hyperledger.identus.agent.walletapi.storage.GenericSecret[K, V]
type DIDNonSecretStorage = org.hyperledger.identus.agent.walletapi.storage.DIDNonSecretStorage
type DIDSecretStorage = org.hyperledger.identus.agent.walletapi.storage.DIDSecretStorage
type WalletNonSecretStorage = org.hyperledger.identus.agent.walletapi.storage.WalletNonSecretStorage
type WalletSecretStorage = org.hyperledger.identus.agent.walletapi.storage.WalletSecretStorage

// Core model types
type ManagedDIDState = org.hyperledger.identus.agent.walletapi.model.ManagedDIDState
type ManagedDIDDetail = org.hyperledger.identus.agent.walletapi.model.ManagedDIDDetail
type PublicationState = org.hyperledger.identus.agent.walletapi.model.PublicationState
val PublicationState = org.hyperledger.identus.agent.walletapi.model.PublicationState

type Wallet = org.hyperledger.identus.agent.walletapi.model.Wallet
val Wallet = org.hyperledger.identus.agent.walletapi.model.Wallet

type Entity = org.hyperledger.identus.agent.walletapi.model.Entity
val Entity = org.hyperledger.identus.agent.walletapi.model.Entity

type WalletSeed = org.hyperledger.identus.agent.walletapi.model.WalletSeed
val WalletSeed = org.hyperledger.identus.agent.walletapi.model.WalletSeed

// Error types
type WalletManagementServiceError = org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceError
val WalletManagementServiceError = org.hyperledger.identus.agent.walletapi.service.WalletManagementServiceError
