package org.hyperledger.identus.shared.models

import zio.*

case class ModuleId(value: String)

case class SemVer(major: Int, minor: Int, patch: Int):
  override def toString: String = s"$major.$minor.$patch"

trait Module:
  type Config
  type Service

  def id: ModuleId
  def version: SemVer

  def implements: Set[Capability]
  def requires: Set[Capability]

  def defaultConfig: Config
  def enabled(config: Config): Boolean
  def layer: TaskLayer[Service]
