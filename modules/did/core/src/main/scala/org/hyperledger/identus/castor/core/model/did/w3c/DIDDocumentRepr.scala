package org.hyperledger.identus.castor.core.model.did.w3c

import zio.json.ast.Json

/** A projection of DIDDocument data model to W3C compliant DID representation */
final case class DIDDocumentRepr(
    id: String,
    controller: String,
    verificationMethod: Seq[PublicKeyRepr],
    authentication: Seq[PublicKeyReprOrRef],
    assertionMethod: Seq[PublicKeyReprOrRef],
    keyAgreement: Seq[PublicKeyReprOrRef],
    capabilityInvocation: Seq[PublicKeyReprOrRef],
    capabilityDelegation: Seq[PublicKeyReprOrRef],
    service: Seq[ServiceRepr],
    context: Seq[String]
)

type PublicKeyReprOrRef = PublicKeyRepr | String

final case class PublicKeyRepr(
    id: String,
    `type`: "JsonWebKey2020",
    controller: String,
    publicKeyJwk: PublicKeyJwk
)

final case class ServiceRepr(
    id: String,
    `type`: String | Seq[String],
    serviceEndpoint: Json
)

final case class PublicKeyJwk(kty: String, crv: String, x: Option[String], y: Option[String])
