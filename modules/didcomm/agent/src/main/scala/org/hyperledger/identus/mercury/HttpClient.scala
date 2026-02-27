package org.hyperledger.identus.mercury

import zio.*

opaque type Header = (String, String)
object Header:
  def apply(key: String, value: String): Header = (key, value)
  def apply(tuple: (String, String)): Header = tuple
  extension (header: Header)
    def key: String = header._1
    def value: String = header._2

case class HttpResponse(status: Int, headers: Seq[Header], bodyAsString: String)

trait HttpClient {
  def get(url: String): Task[HttpResponse]
  def postDIDComm(url: String, data: String): Task[HttpResponse]
}

object HttpClient {
  def get(url: String): RIO[HttpClient, HttpResponse] =
    ZIO.serviceWithZIO(_.get(url))
  def postDIDComm(url: String, data: String): RIO[HttpClient, HttpResponse] =
    ZIO.serviceWithZIO(_.postDIDComm(url, data))
}
