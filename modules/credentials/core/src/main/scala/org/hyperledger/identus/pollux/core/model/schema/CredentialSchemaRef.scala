package org.hyperledger.identus.pollux.core.model.schema

import org.hyperledger.identus.pollux.core.model.primitives.UriString

enum CredentialSchemaRefType:
  case JsonSchema // according to W3C VCDM 2.0
  case JsonSchemaValidator2018 // according to W3C VCDM 1.1

// Represents the credentialSchema properly of the VC according to W3C VCDM 1.1
case class CredentialSchemaRef(`type`: CredentialSchemaRefType, id: UriString)
