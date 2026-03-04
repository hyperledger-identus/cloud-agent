package org.hyperledger.identus.shared.protocols

import zio.*

trait PresentationProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def requestPresentation(params: String): IO[Throwable, RecordId]
  def processRequest(message: ProtocolMessage): IO[Throwable, RecordId]
  def createPresentation(recordId: RecordId): IO[Throwable, RecordId]
  def processPresentation(message: ProtocolMessage): IO[Throwable, RecordId]
  def verifyPresentation(recordId: RecordId): IO[Throwable, RecordId]
