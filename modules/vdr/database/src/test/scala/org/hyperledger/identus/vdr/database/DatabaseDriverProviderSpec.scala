package org.hyperledger.identus.vdr.database

import javax.sql.DataSource
import zio.*
import zio.test.*

import java.lang.reflect.{InvocationHandler, Proxy}
import java.sql.{Connection, PreparedStatement}

object DatabaseDriverProviderSpec extends ZIOSpecDefault:

  private def preparedProxy(): PreparedStatement =
    Proxy
      .newProxyInstance(
        classOf[PreparedStatement].getClassLoader,
        Array(classOf[PreparedStatement]),
        new InvocationHandler:
          private var stringArgs: Map[Int, String] = Map.empty
          private var byteArgs: Map[Int, Array[Byte]] = Map.empty
          override def invoke(proxy: Any, method: java.lang.reflect.Method, args: Array[Any | Null] | Null): Any =
            method.getName match
              case "setString" =>
                stringArgs = stringArgs + (args(0).asInstanceOf[Int] -> args(1).asInstanceOf[String])
                ()
              case "setBytes" =>
                byteArgs = byteArgs + (args(0).asInstanceOf[Int] -> args(1).asInstanceOf[Array[Byte]])
                ()
              case "executeUpdate" => 1
              case "close"         => ()
              case "isClosed"      => false
              case "unwrap"        => null
              case "isWrapperFor"  => false
              case _               => ()
      )
      .asInstanceOf[PreparedStatement]

  private def connectionProxy(): Connection =
    Proxy
      .newProxyInstance(
        classOf[Connection].getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler:
          private var autoCommit = true
          override def invoke(proxy: Any, method: java.lang.reflect.Method, args: Array[Any | Null] | Null): Any =
            method.getName match
              case "setAutoCommit"    => autoCommit = args(0).asInstanceOf[Boolean]; ()
              case "getAutoCommit"    => autoCommit
              case "prepareStatement" => preparedProxy()
              case "close"            => ()
              case "isClosed"         => false
              case "unwrap"           => null
              case "isWrapperFor"     => false
              case _                  => ()
      )
      .asInstanceOf[Connection]

  private val stubDataSource: DataSource = new DataSource:
    override def getConnection(): Connection = connectionProxy()
    override def getConnection(username: String, password: String): Connection = getConnection()
    override def getLogWriter: java.io.PrintWriter = null
    override def setLogWriter(out: java.io.PrintWriter): Unit = ()
    override def setLoginTimeout(seconds: Int): Unit = ()
    override def getLoginTimeout: Int = 0
    override def getParentLogger: java.util.logging.Logger = java.util.logging.Logger.getGlobal
    override def unwrap[T](iface: Class[T]): T = throw new UnsupportedOperationException
    override def isWrapperFor(iface: Class[?]): Boolean = false

  override def spec: Spec[Any, Any] = suite("DatabaseDriverProvider")(
    test("returns None when disabled") {
      assertTrue(DatabaseDriverProvider.load(enabled = false, stubDataSource).isEmpty)
    },
    test("loads database driver when enabled") {
      val driverOpt = DatabaseDriverProvider.load(enabled = true, stubDataSource)
      assertTrue(driverOpt.isDefined) &&
      assertTrue(driverOpt.get.getIdentifier() == "database")
    }
  )
