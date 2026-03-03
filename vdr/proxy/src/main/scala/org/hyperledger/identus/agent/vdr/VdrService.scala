package org.hyperledger.identus.agent.vdr

import drivers.{DatabaseDriver, InMemoryDriver}
import hyperledger.identus.vdr.prism
import interfaces.{Driver, Proof}
import io.iohk.atala.prism.protos.node_api
import javax.sql.DataSource
import org.hyperledger.identus.agent.vdr.blockfrost.BlockfrostPrismDriverProvider
import org.hyperledger.identus.agent.vdr.database.DatabaseDriverProvider
import org.hyperledger.identus.agent.vdr.memory.MemoryDriverProvider
import org.hyperledger.identus.agent.vdr.neoprism.NeoPrismVdrService
import org.hyperledger.identus.castor.core.service.NeoPrismClient
import org.hyperledger.identus.agent.vdr.VdrConfigs.PRISMDriverConfig
import org.hyperledger.identus.agent.vdr.VdrServiceError.{
  DeactivatedDid,
  DriverNotFound,
  MissingVdrKey,
  VdrEntryNotFound
}
import org.hyperledger.identus.shared.models.WalletAccessContext
import proxy.VDRProxyMultiDrivers
import proxy.VDRProxyMultiDrivers.NoDriverWithThisSpecificationsException
import urlManagers.BaseUrlManager
import zio.*

import scala.jdk.CollectionConverters.*

class VdrServiceImpl(
    proxy: Option[VDRProxyMultiDrivers],
    prismNodeService: Option[VdrService],
    neoPrismService: Option[VdrService],
    override val identifier: String,
    override val version: String
) extends VdrService {
  private val PrismNodeDriverId = "prism-node"
  private val NeoPrismDriverId = "neoprism"

  extension [R, A](z: ZIO[R, Throwable, A]) {
    def refineVdrError: ZIO[R, DriverNotFound | VdrEntryNotFound, A] =
      z.refineOrDie[DriverNotFound | VdrEntryNotFound] {
        case e: NoDriverWithThisSpecificationsException     => DriverNotFound(e)
        case e: InMemoryDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        case e: DatabaseDriver.DataCouldNotBeFoundException => VdrEntryNotFound(e)
        // Errors thrown from prism-vdr-driver are wrapped in ZIO FiberFailure context
        case FiberFailure(Cause.Die(e: prism.DataAlreadyDeactivatedException, _)) => VdrEntryNotFound(e)
        case FiberFailure(Cause.Die(e: prism.DataNotInitializedException, _))     => VdrEntryNotFound(e)
        case FiberFailure(Cause.Die(e: prism.DataCouldNotBeFoundException, _))    => VdrEntryNotFound(e)
      }
  }

  private def proxyUrl(url: String): Boolean = url.startsWith("vdr://")
  private def wantPrismNode(options: VdrOptions): Boolean =
    options.get("drid").contains(PrismNodeDriverId) || options.get("drf").contains(PrismNodeDriverId)
  private def wantNeoPrism(options: VdrOptions): Boolean =
    options.get("drid").contains(NeoPrismDriverId)
  private def isPrismNodeUrl(url: String): Boolean =
    url.contains(s"drid=$PrismNodeDriverId") || url.contains("drf=prism")
  private def isNeoPrismUrl(url: String): Boolean =
    url.contains(s"drid=$NeoPrismDriverId")

  private def mapProxyError(th: Throwable): DriverNotFound | VdrEntryNotFound = th match
    case e: NoDriverWithThisSpecificationsException                           => DriverNotFound(e)
    case e: InMemoryDriver.DataCouldNotBeFoundException                       => VdrEntryNotFound(e)
    case e: DatabaseDriver.DataCouldNotBeFoundException                       => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataAlreadyDeactivatedException, _)) => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataNotInitializedException, _))     => VdrEntryNotFound(e)
    case FiberFailure(Cause.Die(e: prism.DataCouldNotBeFoundException, _))    => VdrEntryNotFound(e)
    case e: Throwable                                                         => DriverNotFound(e)

  private def useProxy[A](f: VDRProxyMultiDrivers => A): IO[DriverNotFound | VdrEntryNotFound, A] =
    proxy match
      case Some(p) =>
        ZIO
          .attemptBlocking(f(p))
          .mapError(mapProxyError)
      case None =>
        ZIO.fail(DriverNotFound(new NoDriverWithThisSpecificationsException(null, null, Array.empty)))

  private def orDieProxy[A](zio: IO[DriverNotFound | VdrEntryNotFound, A]): UIO[A] =
    zio
      .mapError(e =>
        new RuntimeException(
          e.userFacingMessage,
          e match
            case DriverNotFound(cause)   => cause
            case VdrEntryNotFound(cause) => cause
        )
      )
      .orDie

  override def create(
      data: Array[Byte],
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | MissingVdrKey | DeactivatedDid, VdrOperationResult] =
    if (wantNeoPrism(options)) {
      neoPrismService match
        case Some(s) => s.create(data, options, didKeyId)
        case None    =>
          ZIO.fail(DriverNotFound(new RuntimeException(s"NeoPrism VDR driver is not enabled")))
    } else if (wantPrismNode(options)) {
      prismNodeService match
        case Some(s) => s.create(data, options, didKeyId)
        case None    =>
          ZIO.fail(DriverNotFound(new NoDriverWithThisSpecificationsException(PrismNodeDriverId, null, Array.empty)))
    } else {
      useProxy(_.create(data, options.asJava)).map(url => VdrOperationResult(url, None)).mapError {
        case d: DriverNotFound   => d
        case _: VdrEntryNotFound => DriverNotFound(new RuntimeException("No driver found for create"))
      }
    }

  override def update(
      data: Array[Byte],
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey | DeactivatedDid, Option[
    VdrOperationResult
  ]] =
    neoPrismService match
      case Some(s) if isNeoPrismUrl(url) =>
        s.update(data, url, options, didKeyId)
      case _ =>
        prismNodeService match
          case Some(s) if !proxyUrl(url) || isPrismNodeUrl(url) =>
            s.update(data, url, options, didKeyId)
          case _ =>
            useProxy(p => Option(p.update(data, url, options.asJava))).map(_.map(VdrOperationResult(_, None)))

  override def read(url: VdrUrl): IO[DriverNotFound | VdrEntryNotFound, Array[Byte]] =
    neoPrismService match
      case Some(s) if isNeoPrismUrl(url) =>
        s.read(url)
      case _ =>
        prismNodeService match
          case Some(s) if !proxyUrl(url) || isPrismNodeUrl(url) =>
            s.read(url)
          case _ =>
            useProxy(_.read(url))

  override def delete(
      url: VdrUrl,
      options: VdrOptions,
      didKeyId: Option[String]
  ): ZIO[WalletAccessContext, DriverNotFound | VdrEntryNotFound | MissingVdrKey | DeactivatedDid, Option[String]] =
    neoPrismService match
      case Some(s) if isNeoPrismUrl(url) =>
        s.delete(url, options, didKeyId)
      case _ =>
        prismNodeService match
          case Some(s) if !proxyUrl(url) || isPrismNodeUrl(url) =>
            s.delete(url, options, didKeyId)
          case _ =>
            useProxy(_.delete(url, options.asJava)).as(None)

  override def verify(url: VdrUrl, returnData: Boolean): UIO[Proof] =
    neoPrismService match
      case Some(s) if isNeoPrismUrl(url) =>
        s.verify(url, returnData)
      case _ =>
        prismNodeService match
          case Some(s) if !proxyUrl(url) =>
            s.verify(url, returnData)
          case _ =>
            orDieProxy(useProxy(_.verify(url, returnData)))

  override def getOperationStatus(operationId: String): IO[DriverNotFound, VdrOperationStatus] =
    // Try neoprism first if available, fall back to prism-node
    neoPrismService.orElse(prismNodeService) match
      case Some(s) => s.getOperationStatus(operationId)
      case None    => ZIO.fail(DriverNotFound(new RuntimeException("No VDR driver available for operation status")))

}

object VdrServiceImpl {
  final case class Config(
      enableInMemoryDriver: Boolean,
      enableDatabaseDriver: Boolean,
      prismDriver: Option[PRISMDriverConfig],
      prismNodeDriver: Option[PrismNodeDriverConfig],
      enableNeoPrismDriver: Boolean = false,
      neoPrismClient: Option[NeoPrismClient] = None
  )

  final case class PrismNodeDriverConfig(
      host: String,
      port: Int,
      usePlainText: Boolean
  )

  def layer: RLayer[
    DataSource & Config & VdrOperationSigner & node_api.NodeServiceGrpc.NodeServiceBlockingStub,
    VdrService
  ] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[Config]
        signer <- ZIO.service[VdrOperationSigner]
        stub <- ZIO.service[node_api.NodeServiceGrpc.NodeServiceBlockingStub]
        urlManager <- ZIO.attempt(BaseUrlManager.apply("vdr://", "BaseURL"))
        prismNodeSvc: Option[VdrService] <- config.prismNodeDriver match
          case Some(_) => PrismNodeVdrService.init(stub, signer, urlManager).map(Some(_))
          case None    => ZIO.none
        neoPrismSvc: Option[VdrService] <- (config.enableNeoPrismDriver, config.neoPrismClient) match
          case (true, Some(client)) =>
            NeoPrismVdrService.init(client, signer, urlManager).map(Some(_))
          case _ => ZIO.none
        dbDriverDataSource <- ZIO.service[DataSource]
        maybeMemoryDriver = MemoryDriverProvider.load(config.enableInMemoryDriver)
        maybeDatabaseDriver = DatabaseDriverProvider.load(config.enableDatabaseDriver, dbDriverDataSource)
        maybePrismDriver <- config.prismDriver match
          case Some(c) => BlockfrostPrismDriverProvider.load(c).map(Some(_))
          case None    => ZIO.none
        drivers = Array(
          maybeMemoryDriver,
          maybeDatabaseDriver,
          maybePrismDriver
        ).flatten
        _ <- ZIO.logInfo(
          s"VDR driver init | memory=${config.enableInMemoryDriver}, database=${config.enableDatabaseDriver}, prism=${config.prismDriver.isDefined}, prism-node=${config.prismNodeDriver.isDefined && prismNodeSvc.isDefined}, neoprism=${neoPrismSvc.isDefined}; loaded=${drivers.map(_.getIdentifier()).mkString(",")}"
        )
        proxyOpt <-
          if drivers.nonEmpty then ZIO.attempt(Some(VDRProxyMultiDrivers(urlManager, drivers, "proxy", "0.1.0")))
          else ZIO.succeed(None)
        identifier = proxyOpt.map(_.getIdentifier()).orElse(prismNodeSvc.map(_.identifier)).orElse(neoPrismSvc.map(_.identifier)).getOrElse("vdr")
        version = proxyOpt.map(_.getVersion()).orElse(prismNodeSvc.map(_.version)).orElse(neoPrismSvc.map(_.version)).getOrElse("0.1.0")
        service = VdrServiceImpl(
          proxyOpt,
          prismNodeSvc,
          neoPrismSvc,
          identifier,
          version
        )
      } yield service
    }

}
