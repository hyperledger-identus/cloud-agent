package org.hyperledger.identus.connect.core.repository

import zio.*
import zio.test.*

object ConnectionRepositoryInMemorySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("In Memory Connection Repository test suite")(
      ConnectionRepositorySpecSuite.testSuite,
      ConnectionRepositorySpecSuite.multitenantTestSuite
    ).provide(
      ConnectionRepositoryInMemory.layer
    )

  // TODO: not good enough I want to test the SQL queries!

}
