package org.hyperledger.identus.oid4vci.http

case class VerifiablePresentationSubmissionForm(
    vp_token: String,
    presentation_submission: String,
    state: Option[String],
    code: Option[String],
    id_token: Option[String],
    iss: Option[String]
)
