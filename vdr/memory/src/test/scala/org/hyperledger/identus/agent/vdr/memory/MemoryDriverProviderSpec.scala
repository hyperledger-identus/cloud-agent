package org.hyperledger.identus.agent.vdr.memory

import zio.*
import zio.test.*

object MemoryDriverProviderSpec extends ZIOSpecDefault:

  override def spec: Spec[Any, Any] = suite("MemoryDriverProvider")(
    test("returns None when disabled") {
      assertTrue(MemoryDriverProvider.load(false).isEmpty)
    },
    test("loads in-memory driver when enabled") {
      val driverOpt = MemoryDriverProvider.load(true)
      assertTrue(driverOpt.isDefined) &&
      assertTrue(driverOpt.get.getIdentifier() == "memory")
    }
  )
