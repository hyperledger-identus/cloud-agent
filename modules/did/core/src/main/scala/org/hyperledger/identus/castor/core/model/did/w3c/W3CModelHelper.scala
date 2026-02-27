package org.hyperledger.identus.castor.core.model.did.w3c

import org.hyperledger.identus.castor.core.model.did.*
import org.hyperledger.identus.castor.core.model.did.ServiceEndpoint.UriOrJsonEndpoint
import org.hyperledger.identus.shared.crypto.Apollo
import org.hyperledger.identus.shared.models.{Base64UrlString, HexString}
import zio.json.ast.Json

import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

object W3CModelHelper extends W3CModelHelper

private[castor] trait W3CModelHelper {

  private val XML_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  private def toXmlDateTime(time: Instant): String = {
    val zonedDateTime = time.atZone(ZoneOffset.UTC)
    XML_DATETIME_FORMATTER.format(zonedDateTime)
  }

  extension (didMetadata: DIDMetadata) {
    def toW3C: DIDDocumentMetadataRepr = DIDDocumentMetadataRepr(
      deactivated = didMetadata.deactivated,
      canonicalId = didMetadata.canonicalId.map(_.toString),
      versionId = HexString.fromByteArray(didMetadata.lastOperationHash.toArray).toString,
      created = didMetadata.created.map(toXmlDateTime),
      updated = didMetadata.updated.map(toXmlDateTime)
    )
  }

  extension (didData: DIDData) {
    def toW3C(did: PrismDID): DIDDocumentRepr = {
      import VerificationRelationship.*
      val embeddedKeys = didData.publicKeys.map(k => k.toW3C(did, did))
      val keyRefWithPurpose = didData.publicKeys.map(k => k.purpose -> s"${did.toString}#${k.id}")
      val services = didData.services.map(_.toW3C(did))
      DIDDocumentRepr(
        id = did.toString,
        controller = did.toString,
        verificationMethod = embeddedKeys,
        authentication = keyRefWithPurpose.collect { case (Authentication, k) => k },
        assertionMethod = keyRefWithPurpose.collect { case (AssertionMethod, k) => k },
        keyAgreement = keyRefWithPurpose.collect { case (KeyAgreement, k) => k },
        capabilityInvocation = keyRefWithPurpose.collect { case (CapabilityInvocation, k) => k },
        capabilityDelegation = keyRefWithPurpose.collect { case (CapabilityDelegation, k) => k },
        service = services,
        context = deriveContext(embeddedKeys, services)
      )
    }

    // Reference: https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#constructing-a-json-ld-did-document
    private def deriveContext(keys: Seq[PublicKeyRepr], services: Seq[ServiceRepr]): Seq[String] = {
      val mandatoryContext = Seq("https://www.w3.org/ns/did/v1")
      val additionalContext = {
        val keyTypes = keys.map(_.`type`).toSet
        val serviceTypes = services
          .map(_.`type`)
          .flatMap {
            case s: String      => Seq(s)
            case s: Seq[String] => s
          }
          .toSet
        Seq(
          Option.when(keyTypes.contains("JsonWebKey2020"))("https://w3id.org/security/suites/jws-2020/v1"),
          Option.when(serviceTypes.contains("DIDCommMessaging"))("https://didcomm.org/messaging/contexts/v2"),
          Option.when(serviceTypes.contains("LinkedDomains"))(
            "https://identity.foundation/.well-known/did-configuration/v1"
          )
        ).flatten
      }
      val userDefinedContext = didData.context
      mandatoryContext ++ additionalContext ++ userDefinedContext
    }
  }

  extension (service: Service) {
    def toW3C(did: PrismDID): ServiceRepr =
      ServiceRepr(
        id = s"${did.toString}#${service.id}",
        `type` = serviceTypeToW3C(service.`type`),
        serviceEndpoint = serviceEndpointToW3C(service.serviceEndpoint)
      )

    private def serviceTypeToW3C(serviceType: ServiceType): String | Seq[String] = {
      import ServiceType.*
      serviceType match {
        case ServiceType.Single(name)    => name.value
        case names: ServiceType.Multiple => names.values.map(_.value)
      }
    }

    private def serviceEndpointToW3C(serviceEndpoint: ServiceEndpoint): Json = {
      serviceEndpoint match {
        case ServiceEndpoint.Single(uri) =>
          uri match {
            case UriOrJsonEndpoint.Uri(uri)   => Json.Str(uri.value)
            case UriOrJsonEndpoint.Json(json) => json
          }
        case ep: ServiceEndpoint.Multiple =>
          val uris = ep.values.map {
            case UriOrJsonEndpoint.Uri(uri)   => Json.Str(uri.value)
            case UriOrJsonEndpoint.Json(json) => json
          }
          Json.Arr(uris*)
      }
    }
  }

  extension (publicKey: PublicKey) {
    def toW3C(did: PrismDID, controller: PrismDID): PublicKeyRepr = {
      val curve = publicKey.publicKeyData match {
        case PublicKeyData.ECCompressedKeyData(crv, _) => crv
        case PublicKeyData.ECKeyData(crv, _, _)        => crv
      }
      val publicKeyJwk = curve match {
        case EllipticCurve.SECP256K1 => secp256k1Repr(publicKey.publicKeyData)
        case EllipticCurve.ED25519   => okpPublicKeyRepr(publicKey.publicKeyData)
        case EllipticCurve.X25519    => okpPublicKeyRepr(publicKey.publicKeyData)
      }
      PublicKeyRepr(
        id = s"${did.toString}#${publicKey.id}",
        `type` = "JsonWebKey2020",
        controller = controller.toString,
        publicKeyJwk = publicKeyJwk
      )
    }

    private def okpPublicKeyRepr(pk: PublicKeyData): PublicKeyJwk = {
      pk match {
        case PublicKeyData.ECCompressedKeyData(crv, data) =>
          PublicKeyJwk(
            kty = "OKP",
            crv = crv.name,
            x = Some(data.toStringNoPadding),
            y = None
          )
        case PublicKeyData.ECKeyData(crv, x, _) =>
          PublicKeyJwk(
            kty = "OKP",
            crv = crv.name,
            x = Some(x.toStringNoPadding),
            y = None
          )
      }
    }

    private def secp256k1Repr(pk: PublicKeyData): PublicKeyJwk = {
      val (x, y) = pk match {
        case PublicKeyData.ECKeyData(_, x, y)           => (x, y)
        case PublicKeyData.ECCompressedKeyData(_, data) =>
          val point = Apollo.default.secp256k1.publicKeyFromEncoded(data.toByteArray).get.getECPoint
          val x = Base64UrlString.fromByteArray(point.x)
          val y = Base64UrlString.fromByteArray(point.y)
          (x, y)
      }
      PublicKeyJwk(
        kty = "EC",
        crv = EllipticCurve.SECP256K1.name,
        x = Some(x.toStringNoPadding),
        y = Some(y.toStringNoPadding)
      )
    }
  }

}
