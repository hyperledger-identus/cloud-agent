package org.hyperledger.identus.shared.credentials

import zio.*

/** A single step in a credential build pipeline */
trait BuildStep:
  def name: String
  def execute(state: BuildState): IO[Throwable, BuildState]

/** Accumulated state flowing through the build pipeline */
case class BuildState(
    claims: String, // JSON string
    metadata: Map[String, String] = Map.empty,
    payload: Option[Array[Byte]] = None,
    signature: Option[Array[Byte]] = None,
    artifacts: Map[String, Array[Byte]] = Map.empty,
)

/** Descriptor for introspection */
case class BuildStepDescriptor(name: String, description: String)

/** Assembles a credential through a pipeline of steps */
trait CredentialBuilder:
  def format: CredentialFormat
  def supportedDataModels: Set[DataModelType]
  def buildCredential(ctx: BuildContext): IO[Throwable, BuiltCredential]
  def steps: Seq[BuildStepDescriptor]
