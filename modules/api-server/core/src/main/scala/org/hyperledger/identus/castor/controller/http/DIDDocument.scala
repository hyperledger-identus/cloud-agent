package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.controller.http.DIDDocument.annotations
import org.hyperledger.identus.castor.core.model.did.w3c
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import scala.language.implicitConversions

@description("A W3C compliant Prism DID document representation.")
final case class DIDDocument(
    `@context`: Seq[Context],
    @description(annotations.id.description)
    @encodedExample(annotations.id.example)
    id: String,
    @description(annotations.controller.description)
    @encodedExample(annotations.controller.example)
    controller: Option[String] = None,
    verificationMethod: Option[Seq[VerificationMethod]] = None,
    authentication: Option[Seq[String]] = None,
    assertionMethod: Option[Seq[String]] = None,
    keyAgreement: Option[Seq[String]] = None,
    capabilityInvocation: Option[Seq[String]] = None,
    capabilityDelegation: Option[Seq[String]] = None,
    service: Option[Seq[Service]] = None
)

object DIDDocument {

  object annotations {
    object id
        extends Annotation[String](
          description = """[DID subject](https://www.w3.org/TR/did-core/#did-subject).
              |The value must match the DID that was given to the resolver.""".stripMargin,
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )

    object controller
        extends Annotation[String](
          description = "[DID controller](https://www.w3.org/TR/did-core/#did-controller)",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )
  }

  given encoder: JsonEncoder[DIDDocument] = DeriveJsonEncoder.gen[DIDDocument]
  given decoder: JsonDecoder[DIDDocument] = DeriveJsonDecoder.gen[DIDDocument]
  given schema: Schema[DIDDocument] = Schema.derived

  given Conversion[w3c.DIDDocumentRepr, DIDDocument] = (didDocument: w3c.DIDDocumentRepr) => {
    DIDDocument(
      `@context` = didDocument.context.map(Context(_)),
      id = didDocument.id,
      controller = Some(didDocument.controller),
      verificationMethod = Some(didDocument.verificationMethod.map(i => i)),
      authentication = Some(didDocument.authentication.map(toPublicKeyRef)),
      assertionMethod = Some(didDocument.assertionMethod.map(toPublicKeyRef)),
      keyAgreement = Some(didDocument.keyAgreement.map(toPublicKeyRef)),
      capabilityInvocation = Some(didDocument.capabilityInvocation.map(toPublicKeyRef)),
      capabilityDelegation = Some(didDocument.capabilityDelegation.map(toPublicKeyRef)),
      service = Some(didDocument.service.map(i => i))
    )
  }

  // TODO: support embedded public key
  private def toPublicKeyRef(publicKeyReprOrRef: w3c.PublicKeyReprOrRef): String = {
    publicKeyReprOrRef match {
      case s: String => s
      case pk        => throw Exception("Embedded public key is not yet supported by PRISM DID.")
    }
  }
}
