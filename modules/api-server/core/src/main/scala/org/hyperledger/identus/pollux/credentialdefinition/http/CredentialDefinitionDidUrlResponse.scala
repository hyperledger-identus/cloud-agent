package org.hyperledger.identus.pollux.credentialdefinition.http

import org.hyperledger.identus.castor.core.model.did.{DIDUrl, PrismDID}
import org.hyperledger.identus.pollux.core.model
import org.hyperledger.identus.pollux.core.model.schema.CredentialDefinition
import org.hyperledger.identus.pollux.PrismEnvelopeResponse
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.json.Json as JsonUtils
import org.hyperledger.identus.shared.utils.Base64Utils
import zio.json.*
import zio.json.ast.Json

import java.util.UUID
import scala.collection.immutable.ListMap

object CredentialDefinitionDidUrlResponse {

  def asPrismEnvelopeResponse(cd: CredentialDefinition, serviceName: String): Either[String, PrismEnvelopeResponse] = {
    for {
      authorDid <- PrismDID.fromString(cd.author)
      canonicalized <- JsonUtils.canonicalizeToJcs(cd.toJson).left.map(_.toString)
      encoded = Base64Utils.encodeURL(canonicalized.getBytes)
      hash = Sha256Hash.compute(encoded.getBytes).hexEncoded
      didUrl = DIDUrl(
        authorDid.did,
        Seq(),
        ListMap(
          "resourceService" -> Seq(serviceName),
          "resourcePath" -> Seq(s"credential-definition-registry/definitions/did-url/${cd.guid}?resourceHash=$hash"),
        ),
        None
      ).toString
    } yield PrismEnvelopeResponse(
      resource = encoded,
      url = didUrl
    )
  }
}

object CredentialDefinitionInnerDefinitionDidUrlResponse {

  def asPrismEnvelopeResponse(
      innerDefinition: Json,
      authorDid: PrismDID,
      definitionGuid: UUID,
      serviceName: String
  ): Either[String, PrismEnvelopeResponse] = {
    for {
      canonicalized <- JsonUtils.canonicalizeToJcs(innerDefinition.toJson).left.map(_.toString)
      encoded = Base64Utils.encodeURL(canonicalized.getBytes)
      hash = Sha256Hash.compute(encoded.getBytes).hexEncoded
      didUrl = DIDUrl(
        authorDid.did,
        Seq(),
        ListMap(
          "resourceService" -> Seq(serviceName),
          "resourcePath" -> Seq(
            s"credential-definition-registry/definitions/did-url/$definitionGuid/definition?resourceHash=$hash"
          ),
        ),
        None
      ).toString
    } yield PrismEnvelopeResponse(
      resource = encoded,
      url = didUrl
    )
  }

}
