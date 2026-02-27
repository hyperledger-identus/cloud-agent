package org.hyperledger.identus.pollux.sql.repository

import org.hyperledger.identus.pollux.core.repository.{
  OID4VCIIssuerMetadataRepository,
  OID4VCIIssuerMetadataRepositorySpecSuite
}
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.MigrationAspects
import zio.*
import zio.test.*

object JdbcOID4VCIIssuerMetadataRepositorySpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val migration = MigrationAspects.migrateEach(
    schema = "public",
    paths = "classpath:sql/pollux"
  )

  private val testEnvironmentLayer = ZLayer.make[OID4VCIIssuerMetadataRepository](
    JdbcOID4VCIIssuerMetadataRepository.layer,
    contextAwareTransactorLayer,
    systemTransactorLayer
  )

  override def spec =
    (suite("JdbcOID4VCIIssuerMetadataRepository")(
      OID4VCIIssuerMetadataRepositorySpecSuite.testSuite,
      OID4VCIIssuerMetadataRepositorySpecSuite.multitenantTestSuite,
    ) @@ migration).provide(
      Runtime.removeDefaultLoggers,
      testEnvironmentLayer,
      pgContainerLayer,
    )
}
