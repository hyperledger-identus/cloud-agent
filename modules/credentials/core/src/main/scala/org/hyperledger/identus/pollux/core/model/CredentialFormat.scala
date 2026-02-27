package org.hyperledger.identus.pollux.core.model

enum CredentialFormat:
  case JWT extends CredentialFormat
  case SDJWT extends CredentialFormat
  case AnonCreds extends CredentialFormat

object CredentialFormat {
  def fromString(str: String) = str match
    case "JWT"       => Some(CredentialFormat.JWT)
    case "SDJWT"     => Some(CredentialFormat.SDJWT)
    case "AnonCreds" => Some(CredentialFormat.AnonCreds)
    case _           => None
}
