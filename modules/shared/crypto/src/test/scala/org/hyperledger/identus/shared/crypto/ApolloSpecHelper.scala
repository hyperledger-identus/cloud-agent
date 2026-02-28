package org.hyperledger.identus.shared.crypto

import zio.*

trait ApolloSpecHelper {
  protected val apollo: Apollo = Apollo.default
  protected val apolloLayer: ULayer[Apollo] = Apollo.layer
}
