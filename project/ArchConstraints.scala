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
    // Phase 5: Direct-only — polluxCore should not directly depend on cloudAgentWalletAPI
    // (transitive dep via walletManagementApi is acceptable during migration)
    (
      "polluxCore",
      "cloudAgentWalletAPI",
      "polluxCore (credentials-core) should not directly depend on cloudAgentWalletAPI (wallet-management); use walletManagementApi instead",
      Direct
    ),
    // Core modules should not depend on the server
    (
      "castorCore",
      "cloudAgentServer",
      "castorCore (did-core) should not depend on cloudAgentServer (api-server)",
      Transitive
    ),
    (
      "polluxCore",
      "cloudAgentServer",
      "polluxCore (credentials-core) should not depend on cloudAgentServer (api-server)",
      Transitive
    ),
    (
      "connectCore",
      "cloudAgentServer",
      "connectCore (connections-core) should not depend on cloudAgentServer (api-server)",
      Transitive
    ),
    (
      "eventNotification",
      "cloudAgentServer",
      "eventNotification (notifications) should not depend on cloudAgentServer (api-server)",
      Transitive
    ),
    (
      "castorCore",
      "models",
      "castorCore (did-core) should not depend on mercury models (didcomm-models)",
      Transitive
    ),
    // Phase 4: Background jobs and IAM should not be depended on by domain core modules
    (
      "castorCore",
      "apiServerJobs",
      "castorCore (did-core) should not depend on apiServerJobs (background jobs)",
      Transitive
    ),
    (
      "polluxCore",
      "apiServerJobs",
      "polluxCore (credentials-core) should not depend on apiServerJobs (background jobs)",
      Transitive
    ),
    (
      "connectCore",
      "apiServerJobs",
      "connectCore (connections-core) should not depend on apiServerJobs (background jobs)",
      Transitive
    ),
    (
      "castorCore",
      "apiServerIam",
      "castorCore (did-core) should not depend on apiServerIam (IAM)",
      Transitive
    ),
    (
      "polluxCore",
      "apiServerIam",
      "polluxCore (credentials-core) should not depend on apiServerIam (IAM)",
      Transitive
    ),
    (
      "connectCore",
      "apiServerIam",
      "connectCore (connections-core) should not depend on apiServerIam (IAM)",
      Transitive
    ),
    (
      "eventNotification",
      "apiServerJobs",
      "eventNotification (notifications) should not depend on apiServerJobs (background jobs)",
      Transitive
    ),
    (
      "eventNotification",
      "apiServerIam",
      "eventNotification (notifications) should not depend on apiServerIam (IAM)",
      Transitive
    ),
    // Phase 3: Adapter direction constraints — core modules should not depend on persistence adapters
    (
      "polluxCore",
      "polluxDoobie",
      "polluxCore (credentials-core) should not depend on polluxDoobie (persistence adapter)",
      Transitive
    ),
    (
      "connectCore",
      "connectDoobie",
      "connectCore (connections-core) should not depend on connectDoobie (persistence adapter)",
      Transitive
    ),
    (
      "castorCore",
      "polluxDoobie",
      "castorCore (did-core) should not depend on polluxDoobie (persistence adapter)",
      Transitive
    ),
    (
      "castorCore",
      "connectDoobie",
      "castorCore (did-core) should not depend on connectDoobie (persistence adapter)",
      Transitive
    ),
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
