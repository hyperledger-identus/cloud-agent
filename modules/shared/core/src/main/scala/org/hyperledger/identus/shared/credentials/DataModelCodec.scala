package org.hyperledger.identus.shared.credentials

import zio.*
import zio.json.ast.Json

trait DataModelCodec:
  def modelType: DataModelType
  def encodeClaims(claims: Json, meta: Json): IO[Throwable, Json]
  def decodeClaims(raw: RawCredential): IO[Throwable, Json]
  def validateStructure(raw: RawCredential): IO[Throwable, Unit]
