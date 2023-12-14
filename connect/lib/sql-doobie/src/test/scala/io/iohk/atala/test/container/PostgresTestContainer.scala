package io.iohk.atala.test.container

import com.dimafeng.testcontainers.PostgreSQLContainer
import io.iohk.atala.sharedtest.containers.PostgresTestContainer.postgresContainer
import zio.*
import zio.ZIO.*

object PostgresLayer {

  def postgresLayer(
      imageName: Option[String] = Some("postgres"),
      verbose: Boolean = false
  ): ZLayer[Any, Nothing, PostgreSQLContainer] =
    ZLayer.scoped {
      acquireRelease(ZIO.attemptBlockingIO {
        val container = postgresContainer(imageName, verbose)
        container.start()
        container
      }.orDie)(container => attemptBlockingIO(container.stop()).orDie)
    }

}
