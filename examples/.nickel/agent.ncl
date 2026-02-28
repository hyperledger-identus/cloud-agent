let DbConfig = {
  host
    | String,
  port
    | Number
    | default
    = 5432,
  dbName
    | String,
  user
    | String,
  password
    | String,
}
in
let NodeConfig = {
  host
    | String,
  port
    | Number
    | default
    = 50053
}
in
let KeycloakConfig = {
  url
    | String,
  realmName
    | String,
  clientId
    | String,
  clientSecret
    | String
}
in
let VaultConfig = {
  url
    | String,
  token
    | String,
}
in
let AgentServiceArgs = {
  version
    | String,
  polluxDb
    | DbConfig,
  connectionsDb
    | DbConfig,
  agentDb
    | DbConfig,
  node
    | NodeConfig,
  didcommServiceUrl
    | String,
  restServiceUrl
    | String,
  apikeyEnabled
    | Bool,
  keycloak
    | KeycloakConfig
    | optional,
  vault
    | VaultConfig
    | optional,
  bootstrapContainer
    | String
    | doc "The container that agent must wait to complete before starting"
    | optional
}
in
{
  makeAgentService | AgentServiceArgs -> _ = fun args =>
    {
      image = "hyperledgeridentus/identus-cloud-agent:%{args.version}",
      restart = "always",
      environment =
        {
          CREDENTIALS_DB_HOST = args.polluxDb.host,
          CREDENTIALS_DB_PORT = std.to_string args.polluxDb.port,
          CREDENTIALS_DB_NAME = args.polluxDb.dbName,
          CREDENTIALS_DB_USER = args.polluxDb.user,
          CREDENTIALS_DB_PASSWORD = args.polluxDb.password,
          CONNECTIONS_DB_HOST = args.connectionsDb.host,
          CONNECTIONS_DB_PORT = std.to_string args.connectionsDb.port,
          CONNECTIONS_DB_NAME = args.connectionsDb.dbName,
          CONNECTIONS_DB_USER = args.connectionsDb.user,
          CONNECTIONS_DB_PASSWORD = args.connectionsDb.password,
          AGENT_DB_HOST = args.agentDb.host,
          AGENT_DB_PORT = std.to_string args.agentDb.port,
          AGENT_DB_NAME = args.agentDb.dbName,
          AGENT_DB_USER = args.agentDb.user,
          AGENT_DB_PASSWORD = args.agentDb.password,
          DIDCOMM_SERVICE_URL = args.didcommServiceUrl,
          REST_SERVICE_URL = args.restServiceUrl,
          POLLUX_STATUS_LIST_REGISTRY_PUBLIC_URL = args.restServiceUrl,
          PRISM_NODE_HOST = args.node.host,
          PRISM_NODE_PORT = std.to_string args.node.port,
          ADMIN_TOKEN = "admin",
          API_KEY_ENABLED = std.to_string args.apikeyEnabled,
        }
        & (
          if args |> std.record.has_field "keycloak" then
            {
              KEYCLOAK_ENABLED = "true",
              KEYCLOAK_URL = args.keycloak.url,
              KEYCLOAK_REALM = args.keycloak.realmName,
              KEYCLOAK_CLIENT_ID = args.keycloak.clientId,
              KEYCLOAK_CLIENT_SECRET = args.keycloak.clientSecret
            }
          else
            {}
        )
        & (
          if args |> std.record.has_field "vault" then
            {
              SECRET_STORAGE_BACKEND = "vault",
              VAULT_ADDR = args.vault.url,
              VAULT_TOKEN = args.vault.token
            }
          else
            { SECRET_STORAGE_BACKEND = "postgres" }
        ),
      depends_on =
        { "%{args.node.host}" = { condition = "service_started" } }
        & (
          if args |> std.record.has_field "bootstrapContainer" then
            { "%{args.bootstrapContainer}" = { condition = "service_completed_successfully" } }
          else
            {}
        ),
      extra_hosts = ["host.docker.internal:host-gateway"]
    }
}
