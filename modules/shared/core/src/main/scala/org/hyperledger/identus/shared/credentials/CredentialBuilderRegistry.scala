package org.hyperledger.identus.shared.credentials

case class CredentialBuilderRegistry(
    builders: Map[CredentialFormat, CredentialBuilder]
):
  def get(format: CredentialFormat): Option[CredentialBuilder] =
    builders.get(format)

  def formats: Set[CredentialFormat] = builders.keySet

object CredentialBuilderRegistry:
  val empty: CredentialBuilderRegistry = CredentialBuilderRegistry(Map.empty)
