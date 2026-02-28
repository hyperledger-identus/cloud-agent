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
    // credentialsCore should not directly depend on walletManagement
    // (transitive dep via walletManagementApi is acceptable)
    (
      "credentialsCore",
      "walletManagement",
      "credentialsCore should not directly depend on walletManagement; use walletManagementApi instead",
      Direct
    ),
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
      "apiServerJobs",
      "didCore should not depend on apiServerJobs",
      Transitive
    ),
    (
      "credentialsCore",
      "apiServerJobs",
      "credentialsCore should not depend on apiServerJobs",
      Transitive
    ),
    (
      "connectionsCore",
      "apiServerJobs",
      "connectionsCore should not depend on apiServerJobs",
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
      "apiServerJobs",
      "notifications should not depend on apiServerJobs",
      Transitive
    ),
    (
      "notifications",
      "apiServerIam",
      "notifications should not depend on apiServerIam",
      Transitive
    ),
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
