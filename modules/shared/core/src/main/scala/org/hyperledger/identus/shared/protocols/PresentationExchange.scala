package org.hyperledger.identus.shared.protocols

import org.hyperledger.identus.shared.credentials.RawCredential
import zio.*

trait PresentationExchange:
  def matchCredentials(definition: String, available: Seq[RawCredential]): IO[Throwable, String]
  def validateSubmission(definition: String, submission: String): IO[Throwable, Boolean]
