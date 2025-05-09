package org.hyperledger.identus.agent.walletapi.benchmark

import org.hyperledger.identus.agent.walletapi.vault.{KVCodec, VaultKVClient}
import org.hyperledger.identus.shared.crypto.{Apollo, DerivationPath, Secp256k1PrivateKey}
import org.hyperledger.identus.shared.models.{Base64UrlString, HexString}
import org.hyperledger.identus.test.container.VaultTestContainerSupport
import zio.*
import zio.test.*

import scala.util.Try

object KeyDerivation extends ZIOSpecDefault, VaultTestContainerSupport {

  private val seedHex = "00" * 64
  private val seed = HexString.fromStringUnsafe(seedHex).toByteArray

  private def codec(apollo: Apollo): KVCodec[Secp256k1PrivateKey] = new {
    override def encode(value: Secp256k1PrivateKey): Map[String, String] = {
      val encodedKey = Base64UrlString.fromByteArray(value.getEncoded).toString()
      Map("value" -> encodedKey)
    }

    override def decode(kv: Map[String, String]): Try[Secp256k1PrivateKey] = {
      val encodedBytes = Base64UrlString.fromString(kv.get("value").get).toOption.get.toByteArray
      apollo.secp256k1.privateKeyFromEncoded(encodedBytes)
    }
  }

  given KVCodec[Map[String, String]] = new {
    override def encode(value: Map[String, String]): Map[String, String] = value
    override def decode(kv: Map[String, String]): Try[Map[String, String]] = Try(kv)
  }

  override def spec = suite("Key derivation benchmark")(
    deriveKeyBenchmark.provide(Apollo.layer),
    queryKeyBenchmark.provide(vaultKvClientLayer(), Apollo.layer)
  ) @@ TestAspect.sequential @@ TestAspect.timed @@ TestAspect.tag("benchmark") @@ TestAspect.ignore

  private val deriveKeyBenchmark = suite("Key derivation benchmark")(
    benchmarkKeyDerivation(1),
    benchmarkKeyDerivation(8),
    benchmarkKeyDerivation(16),
    benchmarkKeyDerivation(32),
  ) @@ TestAspect.before(deriveKeyWarmUp())

  private val queryKeyBenchmark = suite("Query key benchmark - vault storage")(
    benchmarkVaultQuery(1),
    benchmarkVaultQuery(8),
    benchmarkVaultQuery(16),
    benchmarkVaultQuery(32),
  ) @@ TestAspect.before(vaultWarmUp())

  private def benchmarkKeyDerivation(parallelism: Int) = {
    test(s"derive 50000 keys - $parallelism parallelism") {
      for {
        apollo <- ZIO.service[Apollo]
        durationList <- ZIO
          .foreachPar(1 to 50_000) { i =>
            Live.live {
              apollo.secp256k1
                .deriveKeyPair(seed)(derivationPath(keyIndex = i)*)
                .timed
                .map(_._1)
            }
          }
          .withParallelism(parallelism)
        _ <- logStats(durationList)
      } yield assertCompletes
    }
  }

  private def benchmarkVaultQuery(parallelism: Int) = {
    test(s"query 50000 keys - $parallelism parallelism") {
      for {
        vaultClient <- ZIO.service[VaultKVClient]
        apollo <- ZIO.service[Apollo]
        keyPair = apollo.secp256k1.generateKeyPair
        _ <- ZIO
          .foreach(1 to 50_000) { i =>
            given KVCodec[Secp256k1PrivateKey] = codec(apollo)
            vaultClient.set(s"secret/did/prism/key-$i", keyPair.privateKey)
          }
        durationList <- ZIO
          .foreachPar(1 to 50_000) { i =>
            given KVCodec[Secp256k1PrivateKey] = codec(apollo)
            Live.live { vaultClient.get(s"secret/did/prism/key-$i").timed.map(_._1) }
          }
          .withParallelism(parallelism)
        _ <- logStats(durationList)
      } yield assertCompletes
    }
  }

  private def deriveKeyWarmUp(n: Int = 10000) = {
    for {
      _ <- ZIO.logDebug("running key derivation warm-up")
      apollo <- ZIO.service[Apollo]
      _ <- ZIO
        .foreach(1 to n) { i =>
          apollo.secp256k1.deriveKeyPair(seed)(derivationPath(keyIndex = i)*)
        }
    } yield ()
  }

  private def vaultWarmUp(n: Int = 100) = {
    for {
      vaultClient <- ZIO.service[VaultKVClient]
      _ <- ZIO.logDebug("running vault warm-up")
      _ <- ZIO.foreach(1 to n) { i =>
        vaultClient.set(s"secret/warm-up/key-$i", Map("hello" -> "world"))
      }
      _ <- ZIO.foreach(1 to n) { i =>
        vaultClient.get(s"secret/warm-up/key-$i")
      }
    } yield ()
  }

  private def derivationPath(keyIndex: Int = 0): Seq[DerivationPath] = {
    Seq(
      DerivationPath.Hardened(0x1d),
      DerivationPath.Hardened(0),
      DerivationPath.Hardened(0),
      DerivationPath.Hardened(keyIndex),
    )
  }

  private def logStats(durationList: Seq[Duration]) = {
    val n = durationList.length
    val sortedDurationInMicro = durationList.sorted.map(_.toNanos() / 1000.0)
    val avg = sortedDurationInMicro.sum / n
    val p50 = sortedDurationInMicro.apply((0.50 * n).toInt)
    val p75 = sortedDurationInMicro.apply((0.75 * n).toInt)
    val p90 = sortedDurationInMicro.apply((0.90 * n).toInt)
    val p99 = sortedDurationInMicro.apply((0.99 * n).toInt)
    val max = sortedDurationInMicro.last
    ZIO.logDebug(s"execution time in us. avg: $avg | p50: $p50 | p75: $p75 | p90: $p90 | p99: $p99 | max: $max")
  }
}
