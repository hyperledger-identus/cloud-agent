package org.hyperledger.identus.sharedtest.containers

import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.util.transactor.Transactor
import org.hyperledger.identus.shared.db.{ContextAwareTask, TransactorLayer}
import zio.*

trait PostgresTestContainerSupport {

  protected val pgContainerLayer: TaskLayer[PostgreSQLContainer] = PostgresLayer.postgresLayer()

  protected val contextAwareTransactorLayer: TaskLayer[Transactor[ContextAwareTask]] = {
    import doobie.*
    import doobie.implicits.*
    import zio.interop.catz.*

    val appUser = "test-application-user"
    val appPassword = "password"

    val createAppUser = (xa: Transactor[Task]) =>
      doobie.free.connection.createStatement
        .map { stm =>
          stm.execute(s"""CREATE USER "$appUser" WITH PASSWORD '$appPassword';""")
          stm.execute(
            s"""ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "$appUser";"""
          )
          stm
        }
        .transact(xa)
        .unit

    val superUserTransactor = ZLayer.makeSome[PostgreSQLContainer, Transactor[Task]](
      TransactorLayer.task,
      PostgresLayer.dbConfigLayer,
    )

    val appUserTransactor = ZLayer.makeSome[PostgreSQLContainer, Transactor[ContextAwareTask]](
      TransactorLayer.contextAwareTask,
      PostgresLayer.dbConfigLayer.map(conf =>
        ZEnvironment(
          conf.get.copy(
            username = appUser,
            password = appPassword
          )
        )
      ),
    )

    val initializedTransactor = ZLayer.fromZIO {
      for {
        _ <- ZIO
          .serviceWithZIO[Transactor[Task]](createAppUser)
          .provideSomeLayer(superUserTransactor)
      } yield appUserTransactor
    }.flatten

    pgContainerLayer >>> initializedTransactor
  }

  protected val systemTransactorLayer: TaskLayer[Transactor[Task]] = {
    pgContainerLayer >>> PostgresLayer.dbConfigLayer >>> TransactorLayer.task
  }
}
