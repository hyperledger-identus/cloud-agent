package org.hyperledger.identus.pollux.credentialschema.http

import org.hyperledger.identus.castor.core.model.did.{DIDUrl, PrismDID}
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.schema.CredentialSchema
import org.hyperledger.identus.pollux.PrismEnvelopeResponse
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.json.Json as JsonUtils
import org.hyperledger.identus.shared.utils.Base64Utils
import zio.json.*
import zio.json.ast.Json

import java.util.UUID
import scala.collection.immutable.ListMap

object CredentialSchemaDidUrlResponse {

  def asPrismEnvelopeResponse(cs: CredentialSchema, serviceName: String): Either[String, PrismEnvelopeResponse] = {

    for {
      authorDid <- PrismDID.fromString(cs.author)
      canonicalized <- JsonUtils.canonicalizeToJcs(cs.toJson).left.map(_.toString)
      encoded = Base64Utils.encodeURL(canonicalized.getBytes)
      hash = Sha256Hash.compute(encoded.getBytes).hexEncoded
      didUrl = DIDUrl(
        authorDid.did,
        Seq(),
        ListMap(
          "resourceService" -> Seq(serviceName),
          "resourcePath" -> Seq(s"schema-registry/schemas/did-url/${cs.guid}?resourceHash=$hash"),
        ),
        None
      ).toString
    } yield PrismEnvelopeResponse(
      resource = encoded,
      url = didUrl
    )
  }

}

object CredentialSchemaInnerDidUrlResponse {

  def asPrismEnvelopeResponse(
      innerSchema: Json,
      authorDid: PrismDID,
      schemaGuid: UUID,
      serviceName: String
  ): Either[String, PrismEnvelopeResponse] = {
    for {
      canonicalized <- JsonUtils.canonicalizeToJcs(innerSchema.toJson).left.map(_.toString)
      encoded = Base64Utils.encodeURL(canonicalized.getBytes)
      hash = Sha256Hash.compute(encoded.getBytes).hexEncoded
      didUrl = DIDUrl(
        authorDid.did,
        Seq(),
        ListMap(
          "resourceService" -> Seq(serviceName),
          "resourcePath" -> Seq(s"schema-registry/schemas/did-url/$schemaGuid/schema?resourceHash=$hash"),
        ),
        None
      ).toString
    } yield PrismEnvelopeResponse(
      resource = encoded,
      url = didUrl
    )
  }
}
