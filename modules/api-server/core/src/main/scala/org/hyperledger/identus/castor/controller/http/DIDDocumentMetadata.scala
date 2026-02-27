package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.controller.http.DIDDocumentMetadata.annotations
import org.hyperledger.identus.castor.core.model.did.w3c
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

@description("[DID document metadata](https://www.w3.org/TR/did-core/#did-document-metadata)")
final case class DIDDocumentMetadata(
    @description(annotations.deactivated.description)
    @encodedExample(annotations.deactivated.example)
    deactivated: Option[Boolean] = None,
    @description(annotations.canonicalId.description)
    @encodedExample(annotations.canonicalId.example)
    canonicalId: Option[String] = None,
    @description(annotations.versionId.description)
    @encodedExample(annotations.versionId.example)
    versionId: Option[String] = None,
    @description(annotations.created.description)
    @encodedExample(annotations.created.example)
    created: Option[String] = None,
    @description(annotations.updated.description)
    @encodedExample(annotations.updated.example)
    updated: Option[String] = None,
)

object DIDDocumentMetadata {

  object annotations {
    object deactivated
        extends Annotation[Boolean](
          description =
            "If a DID has been deactivated, DID document metadata MUST include this property with the boolean value true. If a DID has not been deactivated, this property is OPTIONAL, but if included, MUST have the boolean value false.",
          example = false
        )

    object canonicalId
        extends Annotation[String](
          description = """
            |A DID in canonical form.
            |If a DID is in long form and has been published, DID document metadata MUST contain a `canonicalId`` property with the short form DID as its value.
            |If a DID in short form or has not been published, DID document metadata MUST NOT contain a `canonicalId` property.
            |""".stripMargin,
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )

    object versionId
        extends Annotation[String](
          description = """
            |DID document metadata MUST contain a versionId property with the hash of the AtalaOperation contained in the latest valid SignedAtalaOperation that created the DID or changed the DID's internal state.
            |""".stripMargin,
          example = "4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )

    object created
        extends Annotation[String](
          description =
            "The timestamp of the Cardano block that contained the first valid SignedAtalaOperation with a CreateDIDOperation that created the DID.",
          example = "2023-02-04T13:52:10Z"
        )

    object updated
        extends Annotation[String](
          description =
            "The timestamp of the Cardano block that contained the latest valid SignedAtalaOperation that changed the DID's internal state.",
          example = "2023-02-04T13:52:10Z"
        )
  }

  given encoder: JsonEncoder[DIDDocumentMetadata] = DeriveJsonEncoder.gen[DIDDocumentMetadata]
  given decoder: JsonDecoder[DIDDocumentMetadata] = DeriveJsonDecoder.gen[DIDDocumentMetadata]
  given schema: Schema[DIDDocumentMetadata] = Schema.derived

  given Conversion[w3c.DIDDocumentMetadataRepr, DIDDocumentMetadata] =
    (didDocumentMetadata: w3c.DIDDocumentMetadataRepr) =>
      DIDDocumentMetadata(
        deactivated = Some(didDocumentMetadata.deactivated),
        canonicalId = didDocumentMetadata.canonicalId,
        versionId = Some(didDocumentMetadata.versionId),
        created = didDocumentMetadata.created,
        updated = didDocumentMetadata.updated
      )
}
