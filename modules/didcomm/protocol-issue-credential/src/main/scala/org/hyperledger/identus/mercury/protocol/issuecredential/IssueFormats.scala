package org.hyperledger.identus.mercury.protocol.issuecredential

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/*
Issue Credential Formats:
  Propose  ->  Offer -> Request  -> Issue

 - Issue Propose:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#propose-attachment-registry
     - dif/credential-manifest@v1.0
     - aries/ld-proof-vc-detail@v1.0
     - hlindy/cred-filter@v2.0
     - anoncreds/credential-filter@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#propose-attachment-registry
     - dif/credential-manifest@v1.0
     - aries/ld-proof-vc-detail@v1.0
     - hlindy/cred-filter@v2.0

 - Issue Offer:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#offer-attachment-registry
     - dif/credential-manifest@v1.0
     - hlindy/cred-abstract@v2.0
     - aries/ld-proof-vc-detail@v1.0
     - anoncreds/credential-offer@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#offer-attachment-registry
     - dif/credential-manifest@v1.0
     - hlindy/cred-req@v2.0
     - aries/ld-proof-vc-detail@v1.0

 - Issue Request:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#request-attachment-registry
     - dif/credential-manifest@v1.0
     - hlindy/cred-req@v2.0
     - aries/ld-proof-vc-detail@v1.0
     - anoncreds/credential-request@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#request-attachment-registry
     - dif/credential-manifest@v1.0
     - hlindy/cred-req@v2.0
     - aries/ld-proof-vc-detail@v1.0

 - Issue Credential:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#credentials-attachment-registry
     - aries/ld-proof-vc@v1.0
     - hlindy/cred@v2.0
     - anoncreds/credential@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#credentials-attachment-registry
     - aries/ld-proof-vc@v1.0
     - hlindy/cred@v2.0
 */

/** Issue Propose:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#propose-attachment-registry
  *   (DID Comm v1)
  *   - dif/credential-manifest@v1.0
  *   - aries/ld-proof-vc-detail@v1.0
  *   - hlindy/cred-filter@v2.0
  *   - anoncreds/credential-filter@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#propose-attachment-registry
  *   (DID Comm v2)
  *   - dif/credential-manifest@v1.0
  *   - aries/ld-proof-vc-detail@v1.0
  *   - hlindy/cred-filter@v2.0
  */
enum IssueCredentialProposeFormat(val name: String) {
  case Unsupported(other: String) extends IssueCredentialProposeFormat(other)
  // case JWT extends IssueCredentialProposeFormat("jwt/credential-propose@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends IssueCredentialProposeFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends IssueCredentialProposeFormat("vc+sd-jwt")
  case Anoncred extends IssueCredentialProposeFormat("anoncreds/credential-filter@v1.0")
}

object IssueCredentialProposeFormat {
  given JsonEncoder[IssueCredentialProposeFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[IssueCredentialProposeFormat] = DeriveJsonDecoder.gen
}

/** Issue Offer:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#offer-attachment-registry
  *   (DID Comm v1)
  *   - dif/credential-manifest@v1.0
  *   - hlindy/cred-abstract@v2.0
  *   - aries/ld-proof-vc-detail@v1.0
  *   - anoncreds/credential-offer@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#offer-attachment-registry
  *   (DID Comm v2)
  *   - dif/credential-manifest@v1.0
  *   - hlindy/cred-req@v2.0
  *   - aries/ld-proof-vc-detail@v1.0
  */
enum IssueCredentialOfferFormat(val name: String) {
  case Unsupported(other: String) extends IssueCredentialOfferFormat(other)
  // case JWT extends IssueCredentialOfferFormat("jwt/credential-offer@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends IssueCredentialOfferFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends IssueCredentialOfferFormat("vc+sd-jwt")
  case Anoncred extends IssueCredentialOfferFormat("anoncreds/credential-offer@v1.0")
}

object IssueCredentialOfferFormat {
  given JsonEncoder[IssueCredentialOfferFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[IssueCredentialOfferFormat] = DeriveJsonDecoder.gen
}

/** Issue Request:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#request-attachment-registry
  *   (DID Comm v1)
  *   - dif/credential-manifest@v1.0
  *   - hlindy/cred-req@v2.0
  *   - aries/ld-proof-vc-detail@v1.0
  *   - anoncreds/credential-request@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#request-attachment-registry
  *   (DID Comm v2)
  *   - dif/credential-manifest@v1.0
  *   - hlindy/cred-req@v2.0
  *   - aries/ld-proof-vc-detail@v1.0
  */
enum IssueCredentialRequestFormat(val name: String) {
  case Unsupported(other: String) extends IssueCredentialRequestFormat(other)
  // case JWT extends IssueCredentialRequestFormat("jwt/credential-request@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends IssueCredentialRequestFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends IssueCredentialRequestFormat("vc+sd-jwt")
  case Anoncred extends IssueCredentialRequestFormat("anoncreds/credential-request@v1.0")
}

object IssueCredentialRequestFormat {
  given JsonEncoder[IssueCredentialRequestFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[IssueCredentialRequestFormat] = DeriveJsonDecoder.gen
}

/** Issue Credential:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/tree/main/features/0453-issue-credential-v2#credentials-attachment-registry
  *   (DID Comm v1)
  *   - aries/ld-proof-vc@v1.0
  *   - hlindy/cred@v2.0
  *   - anoncreds/credential@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/issue_credential/README.md#credentials-attachment-registry
  *   (DID Comm v2)
  *   - aries/ld-proof-vc@v1.0
  *   - hlindy/cred@v2.0
  */
enum IssueCredentialIssuedFormat(val name: String) {
  case Unsupported(other: String) extends IssueCredentialIssuedFormat(other)
  // case JWT extends IssueCredentialIssuedFormat("jwt/credential@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends IssueCredentialIssuedFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends IssueCredentialIssuedFormat("vc+sd-jwt")
  case Anoncred extends IssueCredentialIssuedFormat("anoncreds/credential@v1.0")
}

object IssueCredentialIssuedFormat {
  given JsonEncoder[IssueCredentialIssuedFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[IssueCredentialIssuedFormat] = DeriveJsonDecoder.gen
}
