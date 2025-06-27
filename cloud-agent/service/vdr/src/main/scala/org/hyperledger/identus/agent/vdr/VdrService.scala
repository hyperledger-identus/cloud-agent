package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import interfaces.{Driver, Proof}
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.VdrServiceError.{DriverNotFound, VdrEntryNotFound}
import proxy.VDRProxyMultiDrivers
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException
import urlManagers.BaseUrlManager
import zio.*

import scala.jdk.CollectionConverters.*

type VdrUrl = String
type VdrOptions = Map[String, String]

trait VdrService {
  def identifier: String
  def version: String

  def create(data: Array[Byte], options: VdrOptions): IO[DriverNotFound, VdrUrl]
  def update(data: Array[Byte], url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Option[VdrUrl]]
  def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]]
  def delete(url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Unit]
  def verify(url: VdrUrl, returnData: Boolean = false): UIO[Proof]
}

class VdrServiceImpl(
    proxyRef: Ref[VDRProxyMultiDrivers],
    override val identifier: String,
    override val version: String
) extends VdrService {

  override def create(data: Array[Byte], options: VdrOptions): IO[DriverNotFound, VdrUrl] =
    proxyRef.get.flatMap { proxy =>
      ZIO
        .attemptBlocking(proxy.create(data, options.asJava))
        .refineOrDie { case e: NoDriverWithThisSpecificationsException =>
          DriverNotFound(e)
        }
    }

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions
  ): IO[DriverNotFound | VdrEntryNotFound, Option[VdrUrl]] =
    proxyRef.get.flatMap { proxy =>
      ZIO
        .attemptBlocking(Option(proxy.update(data, url, options.asJava)))
        .refineOrDie {
          case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
          case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
          case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        }
    }

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    proxyRef.get.flatMap { proxy =>
      ZIO
        .attemptBlocking(proxy.read(url))
        .refineOrDie {
          case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
          case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
          case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        }
    }

  override def delete(url: VdrUrl, options: VdrOptions): IO[DriverNotFound | VdrEntryNotFound, Unit] =
    proxyRef.get.flatMap { proxy =>
      ZIO
        .attemptBlocking(proxy.delete(url, options.asJava))
        .refineOrDie {
          case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
          case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
          case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        }
    }

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(proxy.verify(url, returnData)).orDie
    }

}

object VdrServiceImpl {
  def layer: RLayer[DataSource, VdrService] =
    ZLayer.fromZIO {
      for
        urlManager <- ZIO.attempt(BaseUrlManager.apply("http://localhost", "BaseURL"))
        dbDriverDataSource <- ZIO.service[DataSource]
        drivers <- ZIO.attempt(
          Array[Driver](
            InMemoryDriver("memory", "memory", "0.1.0", Array.empty),
            DatabaseDriver("database", "0.1.0", Array.empty, dbDriverDataSource)
          )
        )
        // Wrapped in Ref as InMemoryDriver is not thread-safe
        proxyRef <- Ref.make(
          VDRProxyMultiDrivers(
            urlManager,
            drivers,
            "proxy",
            "0.1.0"
          )
        )
        proxy <- proxyRef.get
      yield VdrServiceImpl(proxyRef, proxy.getIdentifier(), proxy.getVersion())
    }
}
