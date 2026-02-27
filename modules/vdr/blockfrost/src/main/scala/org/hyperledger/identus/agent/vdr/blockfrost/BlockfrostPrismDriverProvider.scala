package org.hyperledger.identus.agent.vdr.blockfrost

import fmgp.crypto.Secp256k1PrivateKey
import fmgp.did.method.prism.{
  BlockfrostConfig,
  BlockfrostRyoConfig,
  DIDPrism,
  IndexerConfig,
  PrismChainService,
  PrismChainServiceImpl,
  PrismState,
  PrismStateInMemory
}
import fmgp.did.method.prism.cardano.CardanoWalletConfig
import fmgp.did.method.prism.vdr.{Indexer, VDRService, VDRServiceImpl}
import hyperledger.identus.vdr.prism.PRISMDriverInMemory
import interfaces.Driver
import org.hyperledger.identus.agent.vdr.VdrConfigs.PRISMDriverConfig
import zio.*

import java.nio.file.{Files, Paths}

/** Factory for the PRISM driver backed by Blockfrost / Cardano. */
object BlockfrostPrismDriverProvider {

  def load(config: PRISMDriverConfig): UIO[Driver] =
    for {
      _ <- createIndexerDirectories(config.prismStateDir).orDie
      bfConfig <- (config.blockfrostApiKey, config.privateNetwork) match
        case (Some(apiKey), None) =>
          ZIO.succeed(BlockfrostConfig(apiKey, None))
        case (None, Some(networkConfig)) =>
          ZIO.succeed(
            BlockfrostConfig(
              "", // Empty API key for private blockfrost instance
              Some(BlockfrostRyoConfig(url = networkConfig.url, protocolMagic = networkConfig.protocolMagic))
            )
          )
        case _ =>
          ZIO.die(
            new IllegalStateException(
              "Invalid blockfrost configuration: exactly one of blockfrostApiKey or privateNetwork must be provided"
            )
          )
      wallet = CardanoWalletConfig(config.walletMnemonic)
      chain: PrismChainService = PrismChainServiceImpl(bfConfig, wallet)
      prismState <- PrismStateInMemory.empty
      vdrService: VDRService = VDRServiceImpl(chain, prismState)
      driver = PRISMDriverInMemory(
        vdrService = vdrService,
        didPrism = DIDPrism(config.didPrism.replace("did:prism:", "")),
        vdrKey = Secp256k1PrivateKey(config.vdrPrivateKey),
      )
      indexerConfig: IndexerConfig =
        IndexerConfig(mBlockfrostConfig = Some(bfConfig), workdir = config.prismStateDir)
      _ <- Indexer.indexerJobFS
        .tap(_ => PRISMDriverInMemory.loadPrismStateFromChunkFiles)
        .schedule(Schedule.spaced(config.indexIntervalSecond.seconds))
        .provideSome[PrismState](ZLayer.succeed(indexerConfig))
        .provideEnvironment(ZEnvironment(prismState))
        .fork
    } yield driver

  private def createIndexerDirectories(baseDir: String): Task[Unit] = {
    val requiredDirs = Seq("cardano-21325", "diddoc", "events", "ssi", "vdr")

    (for {
      _ <- ZIO.logInfo(s"Initializing PRISM indexer directories at: $baseDir")
      basePath <- ZIO.attemptBlocking(Paths.get(baseDir))
      _ <- ZIO.attemptBlocking {
        if (!Files.exists(basePath)) Files.createDirectories(basePath)
        requiredDirs.foreach { dirName =>
          val dirPath = basePath.resolve(dirName)
          if (!Files.exists(dirPath)) Files.createDirectories(dirPath)
        }
      }
      _ <- ZIO.logInfo(s"Successfully created PRISM indexer directories at: $baseDir")
    } yield ()).catchAll { e =>
      ZIO.logError(s"Failed to create PRISM indexer directories at '$baseDir': ${e.getMessage}") *>
        ZIO.fail(e)
    }
  }
}
