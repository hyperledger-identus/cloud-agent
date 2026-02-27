package org.hyperledger.identus.pollux.core.repository

import zio.*
import zio.test.*

/** core/testOnly org.hyperledger.identus.pollux.core.repository.CredentialRepositoryInMemorySpec */
object CredentialRepositoryInMemorySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Credential Repository In Memory test suite")(
      CredentialRepositorySpecSuite.testSuite,
      CredentialRepositorySpecSuite.multitenantTestSuite
    ).provide(CredentialRepositoryInMemory.layer)

}
