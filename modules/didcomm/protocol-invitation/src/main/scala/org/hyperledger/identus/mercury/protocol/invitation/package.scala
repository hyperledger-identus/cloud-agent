package org.hyperledger.identus.mercury.protocol

package object invitation {

  /** provides new msg id
    * @return
    */
  def getNewMsgId: String = java.util.UUID.randomUUID().toString
}
