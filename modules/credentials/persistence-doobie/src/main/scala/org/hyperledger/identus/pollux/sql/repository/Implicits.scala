package org.hyperledger.identus.pollux.sql.repository

import doobie.util.{Get, Put}
import org.hyperledger.identus.castor.core.model.did.{CanonicalPrismDID, PrismDID}
import org.hyperledger.identus.pollux.core.model.*
import org.hyperledger.identus.pollux.vc.jwt.StatusPurpose

import java.net.{URI, URL}

given didCommIDGet: Get[DidCommID] = Get[String].map(DidCommID(_))
given didCommIDPut: Put[DidCommID] = Put[String].contramap(_.value)

given prismDIDGet: Get[CanonicalPrismDID] =
  Get[String].map(s => PrismDID.fromString(s).fold(e => throw RuntimeException(e), _.asCanonical))
given prismDIDPut: Put[CanonicalPrismDID] = Put[String].contramap(_.toString)

given statusPurposeGet: Get[StatusPurpose] = Get[String].map {
  case "Revocation" => StatusPurpose.Revocation
  case "Suspension" => StatusPurpose.Suspension
  case purpose      => throw RuntimeException(s"Invalid status purpose - $purpose")
}

given statusPurposePut: Put[StatusPurpose] = Put[String].contramap(_.toString)

given urlGet: Get[URL] = Get[String].map(s => URI.create(s).toURL())
given urlPut: Put[URL] = Put[String].contramap(_.toString())

given uriGet: Get[URI] = Get[String].map(s => URI.create(s))
given uriPut: Put[URI] = Put[String].contramap(_.toString())

given credFormatGet: Get[CredentialFormat] = Get[String].map(CredentialFormat.valueOf)
given credFormatPut: Put[CredentialFormat] = Put[String].contramap(_.toString())
