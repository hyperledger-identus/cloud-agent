import sbt._
import sbt.Keys._

object DependencyGraph {
  val dependencyDot = taskKey[Unit]("Print inter-project dependency edges")

  val settings: Seq[Setting[_]] = Seq(
    dependencyDot := {
      val structure = buildStructure.value
      val refs = structure.allProjectRefs
      val logger = streams.value.log

      logger.info("=== Inter-project dependency graph ===")
      for {
        ref <- refs
        project <- structure.allProjects.find(_.id == ref.project)
        dep <- project.dependencies
      } {
        val from = ref.project
        val to = dep.project.project
        logger.info(s"$from -> $to")
      }
      logger.info("=== End dependency graph ===")
    }
  )
}
