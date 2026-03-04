package org.hyperledger.identus.shared.credentials

import zio.json.ast.Json

/** Wire format of a credential */
enum CredentialFormat:
  case JWT, SDJWT, JsonLD, AnonCreds

/** Data model / envelope standard */
enum DataModelType:
  case VCDM_1_1, VCDM_2_0, AnonCreds, Custom

/** Signature algorithm */
enum SignatureAlgorithm:
  case EdDSA, ES256, ES256K, BBS_PLUS, CL

/** Revocation mechanism */
enum RevocationMechanism:
  case StatusList2021, TokenStatusList, AnonCredsAccumulator, RevocationList2020

/** Type of verification check */
enum VerificationCheckType:
  case Signature, Expiry, ClaimsSchema, Predicate, Revocation, IssuerTrust, Zkp, Disclosure

/** Opaque credential bytes + format tag */
case class RawCredential(format: CredentialFormat, data: Array[Byte])

/** Result of building a credential */
case class BuiltCredential(raw: RawCredential, metadata: Json = Json.Obj())

/** Result of a single verification check */
case class CheckResult(checkType: VerificationCheckType, success: Boolean, detail: Option[String] = None)

/** Aggregated verification result */
case class VerificationResult(checks: Seq[CheckResult]):
  def isValid: Boolean = checks.forall(_.success)

/** Opaque reference to a signing key */
case class KeyRef(id: String, algorithm: SignatureAlgorithm)

/** Context for building a credential */
case class BuildContext(
    claims: Json,
    format: CredentialFormat,
    dataModel: DataModelType,
    issuerDid: String,
    keyRef: KeyRef,
    metadata: Json = Json.Obj(),
)

/** Context for verification */
case class VerifyContext(
    resolverEndpoint: Option[String] = None,
    trustedIssuers: Set[String] = Set.empty,
    currentTime: java.time.Instant = java.time.Instant.now(),
)
