package org.hyperledger.identus.api.http.codec

import sttp.model.MediaType

object CustomMediaTypes {

  val `application/did+ld+json`: MediaType = MediaType(
    mainType = "application",
    subType = "did+ld+json",
  )

  val `application/ld+json;did-resolution`: MediaType = MediaType(
    mainType = "application",
    subType = "ld+json",
    otherParameters = Map("profile" -> "https://w3id.org/did-resolution")
  )

}
