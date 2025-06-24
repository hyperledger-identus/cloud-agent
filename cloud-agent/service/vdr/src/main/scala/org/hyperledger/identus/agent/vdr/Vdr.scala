package org.hyperledger.identus.agent.vdr

import drivers.InMemoryDriver
import interfaces.Driver
import interfaces.Proof
import proxy.VDRProxyMultiDrivers
import urlManagers.BaseUrlManager
import zio.*

import scala.jdk.CollectionConverters.*

type VdrUrl = String
type VdrOptions = Map[String, String]

trait Vdr {
  def identifier: UIO[String]
  def version: UIO[String]

  def create(data: Array[Byte], options: VdrOptions): Task[VdrUrl]
  def update(data: Array[Byte], url: VdrUrl, options: VdrOptions): Task[Option[VdrUrl]]
  def read(url: VdrUrl, options: VdrOptions): Task[Array[Byte]]
  def delete(url: VdrUrl, options: VdrOptions): Task[Unit]
  def verify(url: VdrUrl, returnData: Boolean = false): Task[Proof]
}

object Vdr {
  def layer: TaskLayer[Vdr] =
    ZLayer.fromZIO {
      for
        urlManager <- ZIO.attempt(BaseUrlManager.apply("localhost", "BaseURL"))
        drivers <- ZIO.attempt(
          Array[Driver](
            InMemoryDriver(
              "memory",
              "memory",
              "0.1.0",
              Array.empty
            )
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
      yield VdrImpl(proxyRef)
    }
}

private class VdrImpl(proxyRef: Ref[VDRProxyMultiDrivers]) extends Vdr {

  override def identifier: UIO[String] = proxyRef.get.map(_.getIdentifier())

  override def version: UIO[String] = proxyRef.get.map(_.getVersion())

  override def create(data: Array[Byte], options: VdrOptions): Task[VdrUrl] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(proxy.create(data, options.asJava))
    }

  override def update(data: Array[Byte], url: VdrUrl, options: VdrOptions): Task[Option[VdrUrl]] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(Option(proxy.update(data, url, options.asJava)))
    }

  override def read(url: VdrUrl, options: VdrOptions): Task[Array[Byte]] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(proxy.read(url))
    }

  override def delete(url: VdrUrl, options: VdrOptions): Task[Unit] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(proxy.delete(url, options.asJava))
    }

  override def verify(url: VdrUrl, returnData: Boolean): Task[Proof] =
    proxyRef.get.flatMap { proxy =>
      ZIO.attemptBlocking(proxy.verify(url, returnData))
    }

}
