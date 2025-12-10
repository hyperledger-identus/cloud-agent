package org.hyperledger.identus.castor.controller.http

import org.hyperledger.identus.agent.walletapi.model.{DIDPublicKeyTemplate, ManagedDIDDetail, PublicationState}
import org.hyperledger.identus.agent.walletapi.model as walletDomain
import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.castor.core.model.did.{
  EllipticCurve,
  InternalKeyPurpose,
  PrismDID,
  VerificationRelationship
}
import org.hyperledger.identus.castor.core.model.did as castorDomain
import org.hyperledger.identus.shared.utils.Traverse.*
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.lang.IllegalArgumentException
import scala.language.implicitConversions

final case class ManagedDID(
    @description(ManagedDID.annotations.did.description)
    @encodedExample(ManagedDID.annotations.did.example)
    did: String,
    @description(ManagedDID.annotations.longFormDid.description)
    @encodedExample(ManagedDID.annotations.longFormDid.example)
    longFormDid: Option[String] = None,
    @description(ManagedDID.annotations.status.description)
    @encodedExample(ManagedDID.annotations.status.example)
    status: String // TODO: use enum
)

object ManagedDID {
  object annotations {
    object did
        extends Annotation[String](
          description = "A managed DID",
          example = "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff"
        )

    object longFormDid
        extends Annotation[String](
          description =
            "A long-form DID. Mandatory when status is not `PUBLISHED` and optional when status is `PUBLISHED`",
          example =
            "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ"
        )

    object status
        extends Annotation[String](
          description =
            """A status indicating a publication state of a DID in the wallet (e.g. `PUBLICATION_PENDING`, `PUBLISHED`).
              |Does not represent DID a lifecyle (e.g. `deactivated`, `recovered`, `updated`).""".stripMargin,
          example = "CREATED"
        )
  }

  given encoder: JsonEncoder[ManagedDID] = DeriveJsonEncoder.gen[ManagedDID]
  given decoder: JsonDecoder[ManagedDID] = DeriveJsonDecoder.gen[ManagedDID]
  given schema: Schema[ManagedDID] = Schema.derived

  given Conversion[ManagedDIDDetail, ManagedDID] = { didDetail =>
    val operation = didDetail.state.createOperation
    val (longFormDID, status) = didDetail.state.publicationState match {
      case PublicationState.Created() =>
        Some(PrismDID.buildLongFormFromOperation(operation)) -> "CREATED"
      case PublicationState.PublicationPending(_) =>
        Some(PrismDID.buildLongFormFromOperation(operation)) -> "PUBLICATION_PENDING"
      case PublicationState.Published(_) =>
        None -> "PUBLISHED"
    }
    ManagedDID(
      did = didDetail.did.toString,
      longFormDid = longFormDID.map(_.toString),
      status = status
    )
  }
}

final case class ManagedDIDPage(
    self: String,
    kind: String = "ManagedDIDPage",
    pageOf: String,
    next: Option[String] = None,
    previous: Option[String] = None,
    contents: Seq[ManagedDID]
)

object ManagedDIDPage {
  given encoder: JsonEncoder[ManagedDIDPage] = DeriveJsonEncoder.gen[ManagedDIDPage]
  given decoder: JsonDecoder[ManagedDIDPage] = DeriveJsonDecoder.gen[ManagedDIDPage]
  given schema: Schema[ManagedDIDPage] = Schema.derived
}

final case class CreateManagedDidRequest(
    documentTemplate: CreateManagedDidRequestDocumentTemplate
)

object CreateManagedDidRequest {
  given encoder: JsonEncoder[CreateManagedDidRequest] = DeriveJsonEncoder.gen[CreateManagedDidRequest]
  given decoder: JsonDecoder[CreateManagedDidRequest] = DeriveJsonDecoder.gen[CreateManagedDidRequest]
  given schema: Schema[CreateManagedDidRequest] = Schema.derived
}

final case class CreateManagedDidRequestDocumentTemplate(
    publicKeys: Seq[ManagedDIDKeyTemplate],
    internalKeys: Option[Seq[ManagedDIDInternalKeyTemplate]] = None,
    services: Seq[Service],
    contexts: Option[Seq[Context]]
)

object CreateManagedDidRequestDocumentTemplate {
  given encoder: JsonEncoder[CreateManagedDidRequestDocumentTemplate] =
    DeriveJsonEncoder.gen[CreateManagedDidRequestDocumentTemplate]
  given decoder: JsonDecoder[CreateManagedDidRequestDocumentTemplate] =
    DeriveJsonDecoder.gen[CreateManagedDidRequestDocumentTemplate]
  given schema: Schema[CreateManagedDidRequestDocumentTemplate] = Schema.derived

  extension (template: CreateManagedDidRequestDocumentTemplate) {
    def toDomain: Either[String, walletDomain.ManagedDIDTemplate] = {
      for {
        services <- template.services.traverse(_.toDomain)
        publicKeys = template.publicKeys.map[DIDPublicKeyTemplate](k => k)
        internalKeys <- template.internalKeys
          .getOrElse(Nil)
          .toList
          .traverse(_.toDomain)
      } yield walletDomain.ManagedDIDTemplate(
        publicKeys = publicKeys,
        internalKeys = internalKeys,
        services = services,
        contexts = template.contexts.getOrElse(Nil).map(_.value)
      )
    }
  }
}

class Context(val value: String) extends AnyVal

object Context {
  given Conversion[Context, String] = _.value
  given Conversion[String, Context] = Context(_)

  given encoder: JsonEncoder[Context] = JsonEncoder[String].contramap(_.value)
  given decoder: JsonDecoder[Context] = JsonDecoder[String].map(Context(_))
  given schema: Schema[Context] = Schema
    .string[Context]
    .description("The JSON-LD context describing the JSON document")
    .encodedExample("https://didcomm.org/messaging/contexts/v2")
}

enum Purpose {
  case authentication extends Purpose
  case assertionMethod extends Purpose
  case keyAgreement extends Purpose
  case capabilityInvocation extends Purpose
  case capabilityDelegation extends Purpose
}

object Purpose {
  given Conversion[Purpose, VerificationRelationship] = {
    case Purpose.authentication       => VerificationRelationship.Authentication
    case Purpose.assertionMethod      => VerificationRelationship.AssertionMethod
    case Purpose.keyAgreement         => VerificationRelationship.KeyAgreement
    case Purpose.capabilityInvocation => VerificationRelationship.CapabilityInvocation
    case Purpose.capabilityDelegation => VerificationRelationship.CapabilityDelegation
  }

  given Conversion[VerificationRelationship, Purpose] = {
    case VerificationRelationship.Authentication       => Purpose.authentication
    case VerificationRelationship.AssertionMethod      => Purpose.assertionMethod
    case VerificationRelationship.KeyAgreement         => Purpose.keyAgreement
    case VerificationRelationship.CapabilityInvocation => Purpose.capabilityInvocation
    case VerificationRelationship.CapabilityDelegation => Purpose.capabilityDelegation
  }

  given encoder: JsonEncoder[Purpose] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[Purpose] =
    JsonDecoder[String].mapOrFail(s => Purpose.values.find(_.toString == s).toRight(s"Unknown purpose: $s"))
  given schema: Schema[Purpose] = Schema.derivedEnumeration.defaultStringBased
}

enum Curve {
  case secp256k1 extends Curve
  case Ed25519 extends Curve
  case X25519 extends Curve
}

object Curve {
  given Conversion[Curve, EllipticCurve] = {
    case Curve.secp256k1 => EllipticCurve.SECP256K1
    case Curve.Ed25519   => EllipticCurve.ED25519
    case Curve.X25519    => EllipticCurve.X25519
  }

  given Conversion[EllipticCurve, Curve] = {
    case EllipticCurve.SECP256K1 => Curve.secp256k1
    case EllipticCurve.ED25519   => Curve.Ed25519
    case EllipticCurve.X25519    => Curve.X25519
  }

  given encoder: JsonEncoder[Curve] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[Curve] =
    JsonDecoder[String].mapOrFail(s => Curve.values.find(_.toString == s).toRight(s"Unknown curve: $s"))
  given schema: Schema[Curve] = Schema.derivedEnumeration.defaultStringBased
}

@description("A key-pair template to add to DID document.")
final case class ManagedDIDKeyTemplate(
    @description(ManagedDIDKeyTemplate.annotations.id.description)
    @encodedExample(ManagedDIDKeyTemplate.annotations.id.example)
    id: String,
    @description(ManagedDIDKeyTemplate.annotations.purpose.description)
    @encodedExample(ManagedDIDKeyTemplate.annotations.purpose.example)
    purpose: Purpose,
    @description(ManagedDIDKeyTemplate.annotations.curve.description)
    @encodedExample(ManagedDIDKeyTemplate.annotations.curve.example)
    curve: Option[Curve]
)

object ManagedDIDKeyTemplate {
  object annotations {
    object id
        extends Annotation[String](
          description = "Identifier of a verification material in the DID Document",
          example = "key-1"
        )

    object purpose
        extends Annotation[Purpose](
          description = "Purpose of the verification material in the DID Document",
          example = VerificationRelationship.Authentication
        )

    object curve
        extends Annotation[Option[Curve]](
          description =
            "The curve name of the verification material in the DID Document. Defaults to `secp256k1` if not specified.",
          example = Some(Curve.Ed25519)
        )
  }

  given encoder: JsonEncoder[ManagedDIDKeyTemplate] = DeriveJsonEncoder.gen[ManagedDIDKeyTemplate]
  given decoder: JsonDecoder[ManagedDIDKeyTemplate] = DeriveJsonDecoder.gen[ManagedDIDKeyTemplate]
  given schema: Schema[ManagedDIDKeyTemplate] = Schema.derived

  given Conversion[ManagedDIDKeyTemplate, walletDomain.DIDPublicKeyTemplate] = publicKeyTemplate =>
    walletDomain.DIDPublicKeyTemplate(
      id = publicKeyTemplate.id,
      purpose = publicKeyTemplate.purpose,
      curve = publicKeyTemplate.curve.getOrElse(Curve.secp256k1)
    )
}

enum InternalPurpose {
  case vdr extends InternalPurpose
}

object InternalPurpose {
  given Conversion[InternalPurpose, InternalKeyPurpose] = { case InternalPurpose.vdr =>
    InternalKeyPurpose.VDR
  }

  given Conversion[InternalKeyPurpose, InternalPurpose] = {
    case InternalKeyPurpose.VDR => InternalPurpose.vdr
    case other                  => throw IllegalArgumentException(s"Unsupported internal purpose: $other")
  }

  given encoder: JsonEncoder[InternalPurpose] = JsonEncoder[String].contramap(_.toString)
  given decoder: JsonDecoder[InternalPurpose] =
    JsonDecoder[String].mapOrFail(s => InternalPurpose.values.find(_.toString == s).toRight(s"Unknown purpose: $s"))
  given schema: Schema[InternalPurpose] = Schema.derivedEnumeration.defaultStringBased
}

@description("An internal key template (not exposed in DID Document).")
final case class ManagedDIDInternalKeyTemplate(
    @description(ManagedDIDInternalKeyTemplate.annotations.id.description)
    @encodedExample(ManagedDIDInternalKeyTemplate.annotations.id.example)
    id: String,
    @description(ManagedDIDInternalKeyTemplate.annotations.purpose.description)
    @encodedExample(ManagedDIDInternalKeyTemplate.annotations.purpose.example)
    purpose: InternalPurpose
)

object ManagedDIDInternalKeyTemplate {
  object annotations {
    object id
        extends Annotation[String](
          description = "Identifier of an internal key stored alongside the DID (not published in DID Document)",
          example = "vdr-1"
        )

    object purpose
        extends Annotation[InternalPurpose](
          description = "Purpose of the internal key. Currently only `vdr` is supported.",
          example = InternalPurpose.vdr
        )
  }

  given encoder: JsonEncoder[ManagedDIDInternalKeyTemplate] = DeriveJsonEncoder.gen[ManagedDIDInternalKeyTemplate]
  given decoder: JsonDecoder[ManagedDIDInternalKeyTemplate] = DeriveJsonDecoder.gen[ManagedDIDInternalKeyTemplate]
  given schema: Schema[ManagedDIDInternalKeyTemplate] = Schema.derived

  extension (template: ManagedDIDInternalKeyTemplate) {
    def toDomain: Either[String, walletDomain.ManagedInternalDIDKeyTemplate] =
      Right(walletDomain.ManagedInternalDIDKeyTemplate(template.id, template.purpose: InternalKeyPurpose))
  }
}

final case class CreateManagedDIDResponse(
    @description(CreateManagedDIDResponse.annotations.longFormDid.description)
    @encodedExample(CreateManagedDIDResponse.annotations.longFormDid.example)
    longFormDid: String
)

object CreateManagedDIDResponse {
  object annotations {
    object longFormDid
        extends Annotation[String](
          description = "A long-form DID for the created DID",
          example =
            "did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff:Cr4BCrsBElsKBmF1dGgtMRAEQk8KCXNlY3AyNTZrMRIg0opTuxu-zt6aRbT1tPniG4eu4CYsQPM3rrLzvzNiNgwaIIFTnyT2N4U7qCQ78qtWC3-p0el6Hvv8qxG5uuEw-WgMElwKB21hc3RlcjAQAUJPCglzZWNwMjU2azESIKhBU0eCOO6Vinz_8vhtFSAhYYqrkEXC8PHGxkuIUev8GiAydFHLXb7c22A1Uj_PR21NZp6BCDQqNq2xd244txRgsQ"
        )
  }

  given encoder: JsonEncoder[CreateManagedDIDResponse] = DeriveJsonEncoder.gen[CreateManagedDIDResponse]
  given decoder: JsonDecoder[CreateManagedDIDResponse] = DeriveJsonDecoder.gen[CreateManagedDIDResponse]
  given schema: Schema[CreateManagedDIDResponse] = Schema.derived
}
