package org.hyperledger.identus.mercury.protocol.presentproof

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

/*
Present Credential Formats:
  Propose  ->  Request  ->  Present
 - Present Propose:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#negotiation-and-preview
     - hlindy/proof-req@v2.0
     - dif/presentation-exchange/definitions@v1.0
     - anoncreds/proof-request@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#propose-attachment-registry
     - hlindy/proof-req@v2.0
     - dif/presentation-exchange/definitions@v1.0
 - Present Request:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#presentation-request-attachment-registry
     - hlindy/proof-req@v2.0
     - dif/presentation-exchange/definitions@v1.0
     - anoncreds/proof-request@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#presentation-request-attachment-registry
     - hlindy/proof-req@v2.0
     - dif/presentation-exchange/definitions@v1.0
 - Present Credential:
   - (DID Comm v1) https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#presentations-attachment-registry
     - hlindy/proof@v2.0
     - dif/presentation-exchange/submission@v1.0
     - anoncreds/proof@v1.0
   - (DID Comm v2) https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#presentations-attachment-registry
     - hlindy/proof@v2.0
     - dif/presentation-exchange/submission@v1.0
 */

/** Present Propose:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#negotiation-and-preview
  *   (DID Comm v1)
  *   - hlindy/proof-req@v2.0
  *   - dif/presentation-exchange/definitions@v1.0
  *   - anoncreds/proof-request@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#propose-attachment-registry
  *   (DID Comm v2)
  *   - hlindy/proof-req@v2.0
  *   - dif/presentation-exchange/definitions@v1.0
  */
enum PresentCredentialProposeFormat(val name: String) {
  case Unsupported(other: String) extends PresentCredentialProposeFormat(other)
  // case JWT extends PresentCredentialProposeFormat("jwt/proof-request@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends PresentCredentialProposeFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends PresentCredentialProposeFormat("vc+sd-jwt")
  case Anoncred extends PresentCredentialProposeFormat("anoncreds/proof-request@v1.0")
}

object PresentCredentialProposeFormat {
  given JsonEncoder[PresentCredentialProposeFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[PresentCredentialProposeFormat] = DeriveJsonDecoder.gen
}

/** Present Request:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#presentation-request-attachment-registry
  *   (DID Comm v1)
  *   - hlindy/proof-req@v2.0
  *   - dif/presentation-exchange/definitions@v1.0
  *   - anoncreds/proof-request@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#presentation-request-attachment-registry
  *   (DID Comm v2)
  *   - hlindy/proof-req@v2.0
  *   - dif/presentation-exchange/definitions@v1.0
  */
enum PresentCredentialRequestFormat(val name: String) {
  case JWT extends PresentCredentialRequestFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends PresentCredentialRequestFormat("vc+sd-jwt")
  case Anoncred extends PresentCredentialRequestFormat("anoncreds/proof-request@v1.0")
}

object PresentCredentialRequestFormat {
  given JsonEncoder[PresentCredentialRequestFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[PresentCredentialRequestFormat] = DeriveJsonDecoder.gen
}

/** Present Credential:
  * @see
  *   https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md#presentations-attachment-registry
  *   (DID Comm v1)
  *   - hlindy/proof@v2.0
  *   - dif/presentation-exchange/submission@v1.0
  *   - anoncreds/proof@v1.0
  * @see
  *   https://github.com/decentralized-identity/waci-didcomm/blob/main/present_proof/present-proof-v3.md#presentations-attachment-registry
  *   (DID Comm v2)
  *   - hlindy/proof@v2.0
  *   - dif/presentation-exchange/submission@v1.0
  */
enum PresentCredentialFormat(val name: String) {
  // case JWT extends PresentCredentialFormat("jwt/proof-request@v1.0") // TODO FOLLOW specs for JWT VC
  case JWT extends PresentCredentialFormat("prism/jwt") // TODO REMOVE
  case SDJWT extends PresentCredentialFormat("vc+sd-jwt")
  case Anoncred extends PresentCredentialFormat("anoncreds/proof-request@v1.0")
}

object PresentCredentialFormat {
  given JsonEncoder[PresentCredentialFormat] = DeriveJsonEncoder.gen
  given JsonDecoder[PresentCredentialFormat] = DeriveJsonDecoder.gen
}
