package org.hyperledger.identus.shared.protocols

import zio.*
import zio.json.ast.Json

trait PresentationProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def requestPresentation(params: Json): IO[Throwable, RecordId]
  def processRequest(message: ProtocolMessage): IO[Throwable, RecordId]
  def createPresentation(recordId: RecordId): IO[Throwable, RecordId]
  def processPresentation(message: ProtocolMessage): IO[Throwable, RecordId]
  def verifyPresentation(recordId: RecordId): IO[Throwable, RecordId]
