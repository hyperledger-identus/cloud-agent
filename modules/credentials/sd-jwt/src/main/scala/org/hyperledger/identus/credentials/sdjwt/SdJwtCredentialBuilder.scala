package org.hyperledger.identus.credentials.sdjwt

import org.hyperledger.identus.shared.credentials.*
import org.hyperledger.identus.shared.crypto.Ed25519PrivateKey
import zio.*
import zio.json.*
import zio.json.ast.Json

/** Builds SD-JWT credentials by delegating to SDJwtService.
  *
  * Unlike JwtCredentialBuilder which uses generic CredentialSigner,
  * SD-JWT requires Ed25519 private keys directly via SDJwtService.
  * The IssuerKeyResolver bridges the generic KeyRef to an Ed25519PrivateKey.
  */
class SdJwtCredentialBuilder(
    sdJwtService: SDJwtService,
    keyResolver: SdJwtCredentialBuilder.IssuerKeyResolver,
) extends CredentialBuilder:

  override def format: CredentialFormat = CredentialFormat.SDJWT

  override def supportedDataModels: Set[DataModelType] = Set(DataModelType.VCDM_1_1)

  override def steps: Seq[BuildStepDescriptor] = Seq(
    BuildStepDescriptor("prepareClaims", "Prepare claims JSON with issuer metadata"),
    BuildStepDescriptor("resolveKey", "Resolve Ed25519 issuer key from KeyRef"),
    BuildStepDescriptor("issueCredential", "Issue SD-JWT credential via SDJwtService"),
  )

  override def buildCredential(ctx: BuildContext): IO[Throwable, BuiltCredential] =
    val claimsObj = ctx.claims.asObject.getOrElse(Json.Obj().asObject.get)
    val enriched = claimsObj
      .add("iss", Json.Str(ctx.issuerDid))
    val claimsStr = Json.Obj(enriched.fields*).toJson
    for
      issuerKey <- keyResolver.resolve(ctx.keyRef)
      compact = sdJwtService.issueCredential(issuerKey, claimsStr)
    yield BuiltCredential(
      raw = RawCredential(CredentialFormat.SDJWT, compact.compact.getBytes("UTF-8")),
      metadata = Json.Obj(enriched.fields*),
    )

object SdJwtCredentialBuilder:

  /** Resolves a generic KeyRef to an Ed25519PrivateKey for SD-JWT signing */
  trait IssuerKeyResolver:
    def resolve(keyRef: KeyRef): IO[Throwable, Ed25519PrivateKey]
