package org.hyperledger.identus.shared.protocols

import org.hyperledger.identus.shared.models.Failure
import zio.*
import zio.json.ast.Json

trait IssuanceProtocol:
  def protocolId: ProtocolId
  def transport: TransportType

  def initiateOffer(params: Json): IO[Throwable, RecordId]
  def processOffer(message: ProtocolMessage): IO[Throwable, RecordId]
  def createRequest(recordId: RecordId): IO[Throwable, RecordId]
  def processRequest(message: ProtocolMessage): IO[Throwable, RecordId]
  def issueCredential(recordId: RecordId): IO[Throwable, RecordId]
  def processCredential(message: ProtocolMessage): IO[Throwable, RecordId]

  def markSent(recordId: RecordId, phase: Phase): IO[Throwable, Unit]
  def reportFailure(recordId: RecordId, reason: Failure): IO[Throwable, Unit]
