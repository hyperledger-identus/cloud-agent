package org.hyperledger.identus.wallet.secrets

/** Re-exports from agent.walletapi.vault for the wallet-management secrets adapter.
  *
  * These type aliases establish the public adapter surface for Vault-backed secret storage. In a future phase, the
  * actual implementations will be moved here.
  */
package object vault {
  type VaultKVClient = org.hyperledger.identus.wallet.vault.VaultKVClient
  type VaultDIDSecretStorage = org.hyperledger.identus.wallet.vault.VaultDIDSecretStorage
  type VaultGenericSecretStorage = org.hyperledger.identus.wallet.vault.VaultGenericSecretStorage
  type VaultWalletSecretStorage = org.hyperledger.identus.wallet.vault.VaultWalletSecretStorage
}
