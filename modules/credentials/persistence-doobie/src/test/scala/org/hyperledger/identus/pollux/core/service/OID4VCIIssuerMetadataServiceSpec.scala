package org.hyperledger.identus.pollux.core.service

import org.hyperledger.identus.pollux.core.service.uriResolvers.ResourceUrlResolver
import org.hyperledger.identus.pollux.sql.repository.JdbcOID4VCIIssuerMetadataRepository
import org.hyperledger.identus.sharedtest.containers.PostgresTestContainerSupport
import org.hyperledger.identus.test.container.MigrationAspects
import zio.*
import zio.test.*

object OID4VCIIssuerMetadataServiceSpec extends ZIOSpecDefault, PostgresTestContainerSupport {

  private val migration = MigrationAspects.migrateEach(
    schema = "public",
    paths = "classpath:sql/pollux"
  )

  private val testEnvironmentLayer = ZLayer.make[OID4VCIIssuerMetadataService](
    OID4VCIIssuerMetadataServiceImpl.layer,
    JdbcOID4VCIIssuerMetadataRepository.layer,
    ResourceUrlResolver.layer,
    contextAwareTransactorLayer,
    systemTransactorLayer
  )

  override def spec =
    (suite("OID4VCIIssuerMetadataService - Jdbc repository")(
      OID4VCIIssuerMetadataServiceSpecSuite.testSuite
    ) @@ migration).provide(
      Runtime.removeDefaultLoggers,
      testEnvironmentLayer,
      pgContainerLayer,
    )

}
