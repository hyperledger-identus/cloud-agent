import Dependencies._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

inThisBuild(
  Seq(
    organization := "io.iohk.atala",
    scalaVersion := "3.2.0",
    fork := true,
    run / connectInput := true,
    versionScheme := Some("semver-spec"),
    githubOwner := "input-output-hk",
    githubRepository := "atala-prism-building-blocks",
    githubTokenSource := TokenSource.Environment("GITHUB_TOKEN")
  )
)

// Custom keys
val apiBaseDirectory = settingKey[File]("The base directory for Iris API specifications")
ThisBuild / apiBaseDirectory := baseDirectory.value / "../../api"

lazy val root = project
  .in(file("."))
  .settings(
    name := "iris-client",
    libraryDependencies ++= rootDependencies,
    // gRPC settings
    Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"),
    Compile / PB.protoSources := Seq(apiBaseDirectory.value / "grpc")
  )

// ### ReleaseStep ###
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  publishArtifacts,
  setNextVersion
)
