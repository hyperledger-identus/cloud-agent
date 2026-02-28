package org.hyperledger.identus.vdr.database

import drivers.DatabaseDriver
import interfaces.Driver
import javax.sql.DataSource

/** Factory for the Postgres-backed VDR driver. */
object DatabaseDriverProvider:
  def load(enabled: Boolean, dataSource: DataSource): Option[Driver] =
    if enabled then Some(DatabaseDriver("database", "0.1.0", Array.empty, dataSource)) else None
