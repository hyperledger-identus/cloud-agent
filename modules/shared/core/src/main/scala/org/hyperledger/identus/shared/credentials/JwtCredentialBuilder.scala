package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.*
import zio.json.ast.Json

import java.util.Base64

class JwtCredentialBuilder(
    codec: DataModelCodec,
    signer: CredentialSigner,
) extends CredentialBuilder:

  override def format: CredentialFormat = CredentialFormat.JWT

  override def supportedDataModels: Set[DataModelType] = Set(codec.modelType)

  override def steps: Seq[BuildStepDescriptor] = Seq(
    BuildStepDescriptor("encodeClaims", "Encode claims using data model codec"),
    BuildStepDescriptor("serializePayload", "Serialize VC as JWT payload"),
    BuildStepDescriptor("sign", "Sign JWT with credential signer"),
    BuildStepDescriptor("assembleJwt", "Assemble header.payload.signature"),
  )

  override def buildCredential(ctx: BuildContext): IO[Throwable, BuiltCredential] =
    val issuerMeta = Json.Obj(
      "issuer" -> Json.Str(ctx.issuerDid),
    )
    val mergedMeta = mergeObjects(ctx.metadata, issuerMeta)
    for
      vcJson <- codec.encodeClaims(ctx.claims, mergedMeta)
      payloadBytes = vcJson.toJson.getBytes("UTF-8")
      headerJson = Json.Obj(
        "alg" -> Json.Str(algorithmName(ctx.keyRef.algorithm)),
        "typ" -> Json.Str("JWT"),
      )
      headerBytes = headerJson.toJson.getBytes("UTF-8")
      headerB64 = Base64.getUrlEncoder.withoutPadding.encodeToString(headerBytes)
      payloadB64 = Base64.getUrlEncoder.withoutPadding.encodeToString(payloadBytes)
      signingInput = s"$headerB64.$payloadB64"
      signature <- signer.sign(signingInput.getBytes("UTF-8"), ctx.keyRef)
      signatureB64 = Base64.getUrlEncoder.withoutPadding.encodeToString(signature)
      jwt = s"$headerB64.$payloadB64.$signatureB64"
    yield BuiltCredential(
      raw = RawCredential(CredentialFormat.JWT, jwt.getBytes("UTF-8")),
      metadata = vcJson,
    )

  private def algorithmName(algo: SignatureAlgorithm): String = algo match
    case SignatureAlgorithm.EdDSA => "EdDSA"
    case SignatureAlgorithm.ES256 => "ES256"
    case SignatureAlgorithm.ES256K => "ES256K"
    case SignatureAlgorithm.BBS_PLUS => "BBS+"
    case SignatureAlgorithm.CL => "CL"

  private def mergeObjects(a: Json, b: Json): Json =
    (a.asObject, b.asObject) match
      case (Some(aObj), Some(bObj)) => Json.Obj(aObj.fields ++ bObj.fields)
      case (Some(_), None) => a
      case (None, Some(_)) => b
      case _ => Json.Obj()
