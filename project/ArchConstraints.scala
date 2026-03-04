import sbt._
import sbt.Keys._

object ArchConstraints {
  val checkArchConstraints = taskKey[Unit]("Check architectural dependency constraints")

  sealed trait DepScope
  case object Direct extends DepScope
  case object Transitive extends DepScope

  /** Forbidden dependency constraints: (from, to, reason, scope)
    *
    * - Direct: only checks immediate (declared) dependencies
    * - Transitive: checks the full transitive closure
    */
  val forbiddenDeps: Seq[(String, String, String, DepScope)] = Seq(
    // Core modules should not depend on the server
    (
      "didCore",
      "apiServer",
      "didCore should not depend on apiServer",
      Transitive
    ),
    (
      "credentialsCore",
      "apiServer",
      "credentialsCore should not depend on apiServer",
      Transitive
    ),
    (
      "connectionsCore",
      "apiServer",
      "connectionsCore should not depend on apiServer",
      Transitive
    ),
    (
      "notifications",
      "apiServer",
      "notifications should not depend on apiServer",
      Transitive
    ),
    (
      "didCore",
      "didcommModels",
      "didCore should not depend on didcommModels",
      Transitive
    ),
    // Background jobs and IAM should not be depended on by domain core modules
    (
      "didCore",
      "apiServerJobsCore",
      "didCore should not depend on apiServerJobsCore",
      Transitive
    ),
    (
      "credentialsCore",
      "apiServerJobsCore",
      "credentialsCore should not depend on apiServerJobsCore",
      Transitive
    ),
    (
      "connectionsCore",
      "apiServerJobsCore",
      "connectionsCore should not depend on apiServerJobsCore",
      Transitive
    ),
    (
      "didCore",
      "apiServerIam",
      "didCore should not depend on apiServerIam",
      Transitive
    ),
    (
      "credentialsCore",
      "apiServerIam",
      "credentialsCore should not depend on apiServerIam",
      Transitive
    ),
    (
      "connectionsCore",
      "apiServerIam",
      "connectionsCore should not depend on apiServerIam",
      Transitive
    ),
    (
      "notifications",
      "apiServerJobsCore",
      "notifications should not depend on apiServerJobsCore",
      Transitive
    ),
    (
      "notifications",
      "apiServerIam",
      "notifications should not depend on apiServerIam",
      Transitive
    ),
    // credentialsCore should not directly depend on didcommVC (unused)
    ("credentialsCore", "didcommVC", "credentialsCore should not directly depend on didcommVC", Direct),
    // credentialsCore should not depend on credentialsPreX (dependency was inverted)
    ("credentialsCore", "credentialsPreX", "credentialsCore should not depend on credentialsPreX", Direct),
    // credentialsCore should not depend on credentialsSDJWT (dependency was inverted)
    ("credentialsCore", "credentialsSDJWT", "credentialsCore should not depend on credentialsSDJWT", Direct),
    // credentialsCore should not depend on credentialsAnoncreds (dependency was inverted)
    ("credentialsCore", "credentialsAnoncreds", "credentialsCore should not depend on credentialsAnoncreds", Direct),
    // credentialsCore should not depend on credentialsVcJWT (dependency was inverted)
    ("credentialsCore", "credentialsVcJWT", "credentialsCore should not depend on credentialsVcJWT", Direct),
    // Adapter direction constraints — core modules should not depend on persistence adapters
    (
      "credentialsCore",
      "credentialsPersistenceDoobie",
      "credentialsCore should not depend on credentialsPersistenceDoobie (persistence adapter)",
      Transitive
    ),
    (
      "connectionsCore",
      "connectionsPersistenceDoobie",
      "connectionsCore should not depend on connectionsPersistenceDoobie (persistence adapter)",
      Transitive
    ),
    (
      "didCore",
      "credentialsPersistenceDoobie",
      "didCore should not depend on credentialsPersistenceDoobie (persistence adapter)",
      Transitive
    ),
    (
      "didCore",
      "connectionsPersistenceDoobie",
      "didCore should not depend on connectionsPersistenceDoobie (persistence adapter)",
      Transitive
    ),
    // HTTP modules should not directly depend on domain core modules (use API modules instead)
    ("didcommHttp", "connectionsCore", "didcommHttp should not directly depend on connectionsCore", Direct),
    ("didcommHttp", "credentialsCore", "didcommHttp should not directly depend on credentialsCore", Direct),
    ("connectionsHttp", "connectionsCore", "connectionsHttp should not directly depend on connectionsCore", Direct),
    // HTTP modules should not depend on each other (prevents cross-domain coupling)
    ("connectionsHttp", "didHttp", "connectionsHttp should not depend on didHttp", Transitive),
    ("connectionsHttp", "issueHttp", "connectionsHttp should not depend on issueHttp", Transitive),
    ("didHttp", "connectionsHttp", "didHttp should not depend on connectionsHttp", Transitive),
    ("didHttp", "issueHttp", "didHttp should not depend on issueHttp", Transitive),
    ("issueHttp", "connectionsHttp", "issueHttp should not depend on connectionsHttp", Transitive),
    (
      "credentialSchemaHttp",
      "credentialDefinitionHttp",
      "credentialSchemaHttp should not depend on credentialDefinitionHttp",
      Transitive
    ),
    // apiServerHttpCore should not depend on domain core modules
    ("apiServerHttpCore", "credentialsCore", "apiServerHttpCore should not depend on credentialsCore", Transitive),
    ("apiServerHttpCore", "connectionsCore", "apiServerHttpCore should not depend on connectionsCore", Transitive),
    ("apiServerHttpCore", "didCore", "apiServerHttpCore should not depend on didCore", Transitive),
    // apiServerConfig should not depend on domain core modules
    ("apiServerConfig", "credentialsCore", "apiServerConfig should not depend on credentialsCore", Transitive),
    ("apiServerConfig", "connectionsCore", "apiServerConfig should not depend on connectionsCore", Transitive),
    ("apiServerConfig", "didCore", "apiServerConfig should not depend on didCore", Transitive),
    // walletManagement should not depend on didCore (only didApi)
    ("walletManagement", "didCore", "walletManagement should depend on didApi, not didCore", Transitive),
    // walletManagement should not depend on didcommAgentDidcommx (uses didcommResolver for PeerDID)
    ("walletManagement", "didcommAgentDidcommx", "walletManagement should not depend on didcommAgentDidcommx", Direct),
    // walletManagementApi should not depend on walletManagement (dependency was inverted)
    ("walletManagementApi", "walletManagement", "walletManagementApi should not depend on walletManagement", Direct),
    // apiServerJobsCore should not depend on HTTP modules
    ("apiServerJobsCore", "issueHttp", "apiServerJobsCore should not depend on issueHttp", Transitive),
    ("apiServerJobsCore", "connectionsHttp", "apiServerJobsCore should not depend on connectionsHttp", Transitive),
    ("apiServerJobsCore", "didHttp", "apiServerJobsCore should not depend on didHttp", Transitive),
    // Persistence modules should not depend on HTTP modules
    (
      "credentialsPersistenceDoobie",
      "issueHttp",
      "credentialsPersistenceDoobie should not depend on issueHttp",
      Transitive
    ),
    (
      "connectionsPersistenceDoobie",
      "connectionsHttp",
      "connectionsPersistenceDoobie should not depend on connectionsHttp",
      Transitive
    ),
    // Domain job modules should not depend on each other
    ("apiServerJobsConnect", "apiServerJobsIssue", "job modules should not depend on each other", Transitive),
    ("apiServerJobsIssue", "apiServerJobsConnect", "job modules should not depend on each other", Transitive),
    ("apiServerJobsPresent", "apiServerJobsConnect", "job modules should not depend on each other", Transitive),
    // apiServerJobsDidSync should not directly depend on credentialsCore
    ("apiServerJobsDidSync", "credentialsCore", "apiServerJobsDidSync should not directly depend on credentialsCore", Direct),
    // credentialDefinitionHttp should not depend on credentialSchemaHttp (uses own annotations)
    ("credentialDefinitionHttp", "credentialSchemaHttp", "credentialDefinitionHttp should not depend on credentialSchemaHttp", Direct),
    // notificationsWebhook should not depend on domain core modules (decoupled via JsonEventConsumer)
    ("notificationsWebhook", "connectionsCore", "notificationsWebhook should not depend on connectionsCore", Direct),
    ("notificationsWebhook", "credentialsCore", "notificationsWebhook should not depend on credentialsCore", Direct),
    // notificationsWebhook should not depend on HTTP controller modules (uses local DTOs instead)
    ("notificationsWebhook", "connectionsHttp", "notificationsWebhook should not depend on connectionsHttp", Direct),
    ("notificationsWebhook", "didHttp", "notificationsWebhook should not depend on didHttp", Direct),
    ("notificationsWebhook", "issueHttp", "notificationsWebhook should not depend on issueHttp", Direct),
    ("notificationsWebhook", "presentProofHttp", "notificationsWebhook should not depend on presentProofHttp", Direct),
    // apiServerJobsCore should not depend on credentialsVcJWT (uses VcJwtService abstraction)
    ("apiServerJobsCore", "credentialsVcJWT", "apiServerJobsCore should not depend on credentialsVcJWT", Direct),
    // oid4vciCore should not depend on credentialsVcJWT (JwtSignerImplicits moved to sharedCrypto)
    ("oid4vciCore", "credentialsVcJWT", "oid4vciCore should not depend on credentialsVcJWT", Direct),
  )

  val settings: Seq[Setting[_]] = Seq(
    checkArchConstraints := {
      val structure = buildStructure.value
      val logger = streams.value.log

      // Build a map of project -> direct dependencies
      val depMap: Map[String, Set[String]] = structure.allProjectRefs.flatMap { ref =>
        structure.allProjects.find(_.id == ref.project).map { project =>
          ref.project -> project.dependencies.map(_.project.project).toSet
        }
      }.toMap

      // Compute transitive dependencies
      def transitiveDeps(project: String, visited: Set[String] = Set.empty): Set[String] = {
        if (visited.contains(project)) Set.empty
        else {
          val direct = depMap.getOrElse(project, Set.empty)
          direct ++ direct.flatMap(d => transitiveDeps(d, visited + project))
        }
      }

      var violations = 0
      for ((from, to, reason, scope) <- forbiddenDeps) {
        val depsToCheck = scope match {
          case Direct     => depMap.getOrElse(from, Set.empty)
          case Transitive => transitiveDeps(from)
        }
        if (depsToCheck.contains(to)) {
          val scopeLabel = scope match {
            case Direct     => "directly"
            case Transitive => "transitively"
          }
          logger.error(s"[ARCH CONSTRAINT VIOLATION] $reason")
          logger.error(s"  $from $scopeLabel depends on $to")
          violations += 1
        }
      }

      if (violations > 0) {
        throw new MessageOnlyException(s"Found $violations architectural constraint violation(s)")
      } else {
        logger.info("All architectural constraints satisfied.")
      }
    }
  )
}
