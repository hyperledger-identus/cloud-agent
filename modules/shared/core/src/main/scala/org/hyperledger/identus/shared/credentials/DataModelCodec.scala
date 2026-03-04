package org.hyperledger.identus.shared.credentials

import zio.*

trait DataModelCodec:
  def modelType: DataModelType
  def encodeClaims(claims: String, meta: Map[String, String]): IO[Throwable, String]
  def decodeClaims(raw: RawCredential): IO[Throwable, String]
  def validateStructure(raw: RawCredential): IO[Throwable, Unit]
