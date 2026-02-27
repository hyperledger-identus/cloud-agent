package org.hyperledger.identus.castor.core.util

// consider moving this to shared library
object Prelude {

  extension [T](xs: Seq[T]) {
    def isUnique: Boolean = xs.length == xs.distinct.length
  }

}
