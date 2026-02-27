package org.hyperledger.identus.pollux.core.repository

import zio.*
import zio.test.*

object PresentationRepositoryInMemorySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Presentation Repository In Memory test suite")(
      PresentationRepositorySpecSuite.testSuite,
      PresentationRepositorySpecSuite.multitenantTestSuite
    ).provide(PresentationRepositoryInMemory.layer)

}
