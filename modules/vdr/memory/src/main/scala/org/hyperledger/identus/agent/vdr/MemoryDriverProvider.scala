package org.hyperledger.identus.agent.vdr.memory

import drivers.InMemoryDriver
import interfaces.Driver

/** Factory for the in-memory VDR driver.
  *
  * Keeping the wiring here avoids duplicating the constructor details in the proxy layer.
  */
object MemoryDriverProvider:
  def load(enabled: Boolean): Option[Driver] =
    if enabled then Some(InMemoryDriver("memory", "memory", "0.1.0", Array.empty)) else None
