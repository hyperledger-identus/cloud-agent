package org.hyperledger.identus.didcomm.model

import java.util.Base64

trait SignedMesage {
  def string: String
  def base64: String = Base64.getUrlEncoder.encodeToString(string.getBytes)
}
