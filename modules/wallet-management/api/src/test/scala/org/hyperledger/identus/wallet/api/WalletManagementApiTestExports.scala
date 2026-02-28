package org.hyperledger.identus.wallet.api

/** Test-scope re-exports from agent.walletapi for consumers that need test doubles.
  *
  * These re-exports allow polluxCore tests to depend on walletManagementApi (test->test) instead of
  * cloudAgentWalletAPI directly, completing the reverse dependency break.
  */

// In-memory test implementations
type GenericSecretStorageInMemory = org.hyperledger.identus.wallet.memory.GenericSecretStorageInMemory
val GenericSecretStorageInMemory = org.hyperledger.identus.wallet.memory.GenericSecretStorageInMemory

// Mock implementations (zio-mock)
val MockManagedDIDService = org.hyperledger.identus.wallet.service.MockManagedDIDService
