package org.hyperledger.identus.sharedtest.containers

import jakarta.ws.rs.NotFoundException
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.{
  ClientRepresentation,
  CredentialRepresentation,
  RealmRepresentation,
  RoleRepresentation,
  UserRepresentation
}
import zio.*

import java.util.UUID
import scala.jdk.CollectionConverters.*

type KeycloakAdminClient = Keycloak

trait KeycloakTestContainerSupport {

  protected val keycloakContainerLayer: TaskLayer[KeycloakContainerCustom] =
    KeycloakContainerCustom.layer

  protected val keycloakAdminClientLayer: URLayer[KeycloakContainerCustom, KeycloakAdminClient] =
    ZLayer.fromZIO(ZIO.service[KeycloakContainerCustom].map(_.container.getKeycloakAdminClient))

  protected val adminClientZIO = ZIO.service[KeycloakAdminClient]

  protected val realmName = "atala-test"

  protected val realmRepresentation = {
    val rr = new RealmRepresentation()
    rr.setRealm(realmName)
    rr.setEnabled(true)
    rr
  }

  protected val agentClientSecret = "prism-agent-demo-secret"

  protected val agentClientRepresentation: ClientRepresentation = {
    val acr = new ClientRepresentation()
    acr.setClientId("prism-agent")
    acr.setAuthorizationServicesEnabled(true)
    acr.setDirectAccessGrantsEnabled(true)
    acr.setServiceAccountsEnabled(true)
    acr.setSecret(agentClientSecret)
    acr
  }

  protected def initializeClient =
    for {
      adminClient <- adminClientZIO
      _ <- ZIO
        .attemptBlocking(
          adminClient
            .realm(realmName)
            .remove()
        )
        .catchSome { case _: NotFoundException => ZIO.unit }
      _ <- ZIO
        .attemptBlocking(
          adminClient
            .realms()
            .create(realmRepresentation)
        )
      _ <- ZIO
        .attemptBlocking(
          adminClient
            .realm(realmRepresentation.getRealm)
            .clients()
            .create(agentClientRepresentation)
        )
    } yield ()

  def createUser(username: String, password: String): RIO[KeycloakAdminClient, UserRepresentation] =
    val userRepresentation = {
      val creds = new CredentialRepresentation()
      creds.setTemporary(false)
      creds.setValue(password)

      val ur = new UserRepresentation()
      ur.setId(UUID.nameUUIDFromBytes(username.getBytes).toString)
      ur.setUsername(username)
      ur.setEnabled(true)
      ur.setCredentials(List(creds).asJava)
      ur
    }

    for {
      adminClient <- adminClientZIO
      users = adminClient.realm(realmName).users()
      _ <- ZIO.log(s"Creating user ${userRepresentation.getId}")
      _ <- ZIO.attemptBlocking(users.create(userRepresentation))
      createdUser <- ZIO.attemptBlocking(users.search(username).asScala.head)
      _ <- ZIO.log(s"Created user ${createdUser.getId}")
    } yield createdUser

  def createClientRole(roleName: String): RIO[KeycloakAdminClient, Unit] =
    for {
      adminClient <- adminClientZIO
      realmResource <- ZIO.attemptBlocking(adminClient.realm(realmName))
      clientRepr <- ZIO.attemptBlocking(
        realmResource.clients().findByClientId(agentClientRepresentation.getClientId()).get(0)
      )
      clientResource <- ZIO.attemptBlocking(realmResource.clients().get(clientRepr.getId()))
      _ <- ZIO.attemptBlocking(
        clientResource
          .roles()
          .create(RoleRepresentation(roleName, "", false))
      )
    } yield ()

  def grantClientRole(username: String, roleName: String): RIO[KeycloakAdminClient, Unit] =
    for {
      adminClient <- adminClientZIO
      realmResource <- ZIO.attemptBlocking(adminClient.realm(realmName))
      clientRepr <- ZIO.attemptBlocking(
        realmResource.clients().findByClientId(agentClientRepresentation.getClientId()).get(0)
      )
      clientResource <- ZIO.attemptBlocking(realmResource.clients().get(clientRepr.getId()))
      userRepr <- ZIO.attemptBlocking(realmResource.users().searchByUsername(username, true).get(0))
      roleRepr <- ZIO.attemptBlocking(
        clientResource
          .roles()
          .list()
          .asScala
          .find(_.getName() == roleName)
          .get
      )
      _ <- ZIO.attemptBlocking(
        realmResource
          .users()
          .get(userRepr.getId())
          .roles()
          .clientLevel(clientRepr.getId())
          .add(List(roleRepr).asJava)
      )
    } yield ()
}
