package org.hyperledger.identus.iam.authentication.apikey

case class ApiKeyConfig(salt: String, enabled: Boolean, authenticateAsDefaultUser: Boolean, autoProvisioning: Boolean)
