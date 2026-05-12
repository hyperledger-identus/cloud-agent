package org.hyperledger.identus.agent.vdr

/** Configuration models shared across VDR modules. */
object VdrConfigs {
  final case class BlockfrostPrivateNetworkConfig(
      url: String,
      protocolMagic: Int
  )

  /** PRISM (blockfrost) driver configuration. */
  final case class PRISMDriverConfig(
      blockfrostApiKey: Option[String],
      privateNetwork: Option[BlockfrostPrivateNetworkConfig],
      walletMnemonic: Seq[String],
      didPrism: String,
      vdrPrivateKey: Array[Byte],
      prismStateDir: String,
      indexIntervalSecond: Int
  )
}
