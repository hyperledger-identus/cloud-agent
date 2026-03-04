package org.hyperledger.identus.shared.models

import zio.*

case class ModuleRegistryError(message: String) extends Exception(message)

class ModuleRegistry(val modules: Seq[Module]):

  private val allProvided: Set[Capability] =
    modules.flatMap(_.implements).toSet

  def validateDependencies: IO[ModuleRegistryError, Unit] =
    val unsatisfied = for
      m <- modules
      req <- m.requires
      if !allProvided.exists(_.satisfies(req))
    yield (m.id, req)

    if unsatisfied.isEmpty then ZIO.unit
    else
      val details = unsatisfied
        .map((mid, cap) =>
          s"  ${mid.value} requires ${cap.contract}${cap.variant.map(v => s"($v)").getOrElse("")}"
        )
        .mkString("\n")
      ZIO.fail(ModuleRegistryError(s"Unsatisfied dependencies:\n$details"))

  def resolve(requirement: Capability): Seq[Module] =
    modules.filter(_.implements.exists(_.satisfies(requirement)))
