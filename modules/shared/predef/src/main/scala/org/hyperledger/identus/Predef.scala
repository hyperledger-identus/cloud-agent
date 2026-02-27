package org.hyperledger.identus

/** Note this is a Error not a Exception. It's not supposed to be catch. */
final class FeatureNotImplemented(msg: String) extends Error(msg) {
  def this() = this(
    "This feature was not implemented." +
      " This path of execution was never expected to pass here." +
      " Please open a open the issue on https://github.com/hyperledger-identus/cloud-agent/issues"
  )
}

object Predef {
  // @deprecated("Do not use this in the code ", "2.14")
  inline def ??? : Nothing =
    scala.compiletime.error("You are not allowed to have a ??? (NotImplementedError) in the identus main code base")

  // TODO review and try to REMOVE
  def UnexpectedCodeExecutionPath: Nothing = scala.Predef.???

  // FIXME
  def FIXME: Nothing = scala.Predef.???

  // TODO Open tickets and REMOVE
  def FeatureNotImplemented: Nothing = throw new FeatureNotImplemented
}
