package org.hyperledger.identus.shared.protocols

import zio.*
import zio.stream.Stream

trait ProtocolTransport:
  def transportType: TransportType
  def send(message: ProtocolMessage, destination: Endpoint): IO[Throwable, Unit]
  def receive: Stream[Throwable, ProtocolMessage]
