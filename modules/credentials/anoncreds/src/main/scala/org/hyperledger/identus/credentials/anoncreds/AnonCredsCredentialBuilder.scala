package org.hyperledger.identus.credentials.anoncreds

import org.hyperledger.identus.shared.credentials.*
import zio.*
import zio.json.*
import zio.json.ast.Json

/** Builds AnonCreds credentials by delegating to AnoncredService.
  *
  * AnonCreds issuance requires pre-negotiated state (credential definition,
  * offer, request). The CredentialContext resolver provides these from the
  * protocol layer, keeping the builder focused on credential assembly.
  */
class AnonCredsCredentialBuilder(
    anoncredService: AnoncredService,
    contextResolver: AnonCredsCredentialBuilder.CredentialContext.Resolver,
) extends CredentialBuilder:

  override def format: CredentialFormat = CredentialFormat.AnonCreds

  override def supportedDataModels: Set[DataModelType] = Set(DataModelType.AnonCreds)

  override def steps: Seq[BuildStepDescriptor] = Seq(
    BuildStepDescriptor("resolveContext", "Resolve credential definition, offer, and request"),
    BuildStepDescriptor("extractAttributes", "Extract attribute values from claims"),
    BuildStepDescriptor("createCredential", "Create AnonCreds credential via AnoncredService"),
  )

  override def buildCredential(ctx: BuildContext): IO[Throwable, BuiltCredential] =
    for
      context <- contextResolver.resolve(ctx.keyRef)
      attrValues = extractAttributes(ctx.claims)
      credential = anoncredService.createCredential(
        context.credentialDefinition,
        context.credentialDefinitionPrivate,
        context.offer,
        context.request,
        attrValues,
      )
    yield BuiltCredential(
      raw = RawCredential(CredentialFormat.AnonCreds, credential.data.getBytes("UTF-8")),
      metadata = ctx.claims,
    )

  private def extractAttributes(claims: Json): Seq[(String, String)] =
    claims.asObject match
      case Some(obj) =>
        obj.fields.collect {
          case (key, Json.Str(value)) => (key, value)
          case (key, Json.Num(value)) => (key, value.toString)
          case (key, Json.Bool(value)) => (key, value.toString)
        }.toSeq
      case None => Seq.empty

object AnonCredsCredentialBuilder:

  /** Pre-negotiated state required for AnonCreds credential issuance */
  case class CredentialContext(
      credentialDefinition: AnoncredCredentialDefinition,
      credentialDefinitionPrivate: AnoncredCredentialDefinitionPrivate,
      offer: AnoncredCredentialOffer,
      request: AnoncredCredentialRequest,
  )

  object CredentialContext:
    /** Resolves pre-negotiated AnonCreds issuance state from the protocol layer */
    trait Resolver:
      def resolve(keyRef: KeyRef): IO[Throwable, CredentialContext]
