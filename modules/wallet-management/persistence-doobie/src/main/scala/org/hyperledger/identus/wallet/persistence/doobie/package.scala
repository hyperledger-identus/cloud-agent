package org.hyperledger.identus.wallet.persistence

/** Re-exports from agent.walletapi.sql for the wallet-management persistence adapter.
  *
  * These type aliases establish the public adapter surface for wallet persistence. In a future phase, the actual
  * implementations will be moved here.
  */
package object doobie {
  type JdbcDIDNonSecretStorage = org.hyperledger.identus.agent.walletapi.sql.JdbcDIDNonSecretStorage
  type JdbcDIDSecretStorage = org.hyperledger.identus.agent.walletapi.sql.JdbcDIDSecretStorage
  type JdbcWalletNonSecretStorage = org.hyperledger.identus.agent.walletapi.sql.JdbcWalletNonSecretStorage
  type JdbcWalletSecretStorage = org.hyperledger.identus.agent.walletapi.sql.JdbcWalletSecretStorage
  type JdbcGenericSecretStorage = org.hyperledger.identus.agent.walletapi.sql.JdbcGenericSecretStorage
  type JdbcEntityRepository = org.hyperledger.identus.agent.walletapi.sql.JdbcEntityRepository
  type EntityRepository = org.hyperledger.identus.agent.walletapi.sql.EntityRepository
}
