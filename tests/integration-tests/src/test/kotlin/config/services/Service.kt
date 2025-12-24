package config.services

import com.sksamuel.hoplite.ConfigAlias

data class Service(
    @ConfigAlias("prism_node") val prismNode: VerifiableDataRegistry?,
    val neoprism: NeoPrism?,
    val keycloak: Keycloak?,
    @ConfigAlias("keycloak_oid4vci") val keycloakOid4vci: Keycloak,
    val vault: Vault?,
)
