package org.hyperledger.identus.shared.protocols

import org.hyperledger.identus.shared.credentials.RawCredential
import zio.*
import zio.json.ast.Json

trait PresentationExchange:
  def matchCredentials(definition: Json, available: Seq[RawCredential]): IO[Throwable, Json]
  def validateSubmission(definition: Json, submission: Json): IO[Throwable, Boolean]
