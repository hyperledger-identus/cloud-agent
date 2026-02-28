package org.hyperledger.identus.sharedtest.containers

import com.dimafeng.testcontainers.{JdbcDatabaseContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName

class PostgreSQLContainerCustom(
    dockerImageNameOverride: Option[DockerImageName] = None,
    databaseName: Option[String] = None,
    pgUsername: Option[String] = None,
    pgPassword: Option[String] = None,
    mountPostgresDataToTmpfs: Boolean = false,
    urlParams: Map[String, String] = Map.empty,
    commonJdbcParams: JdbcDatabaseContainer.CommonParams = JdbcDatabaseContainer.CommonParams()
) extends PostgreSQLContainer(
      dockerImageNameOverride,
      databaseName,
      pgUsername,
      pgPassword,
      mountPostgresDataToTmpfs,
      urlParams,
      commonJdbcParams
    ) {

  override def jdbcUrl: String = {
    /* This is such a hack!
     *
     * We are running PostgreSQL test containers inside a bridged (user-derfined)
     * network.  Testcontainers expects to be able to connect to the _host_ and
     * map ports on the host.  However we are running _inside_ a docker container.
     * So now the mapping to _localhost:randomport_ -> spawned postgres:5432 is
     * available from _outside_, but not form the docker container actually
     * spawning the others.
     *
     * We also can't refer to them by name, because docker somehow fails to
     * resolve names sometimes once a container has joined a network but didn't
     * get a name assigned when joining :shurg:.
     *
     * We can however refer to containers by their containerId, or more
     * precisely by their _short_ (first 12 char) Id.
     *
     * So we overwrite the jdbcUrl, and change the way it's constructed in test
     * containers.
     *
     * This is a mess :(
     */
    val origUrl = super.jdbcUrl
    val idx = origUrl.indexOf('?')
    val params = if (idx >= 0) origUrl.substring(idx) else ""
    s"jdbc:postgresql://${containerId.take(12)}:5432/${super.databaseName}${params}"
  }
}
