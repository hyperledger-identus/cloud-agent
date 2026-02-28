package org.hyperledger.identus.didcomm.protocol

package object invitation {

  /** provides new msg id
    * @return
    */
  def getNewMsgId: String = java.util.UUID.randomUUID().toString
}
