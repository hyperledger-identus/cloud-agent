package org.hyperledger.identus.mercury.model

import munit.*
import zio.json.ast.Json

import scala.language.implicitConversions

/** agentDidcommx/testOnly org.hyperledger.identus.mercury.UnpackMessageImpSpec */
class UnpackMessageImpSpec extends ZSuite {
  test("Message conversions - check pleaseAck None") {
    val m1 = Message(
      `type` = "type_test", // PIURI,
      from = None, // Option[DidId],
      to = Seq(), // Seq[DidId],
      body = Json.Obj(),
      id = "id123",
      createdTime = None,
      expiresTimePlus = None,
      attachments = None,
      thid = None,
      pthid = None,
      ack = None,
      pleaseAck = None,
    )

    val m1x = m1: org.didcommx.didcomm.message.Message
    val test: Option[Long] = Option(null.asInstanceOf[Long])
    println(test)
    val m1test = UnpackMessageImp.message(m1x)
    assertEquals(m1test, m1)
  }

  test("Message conversions - check pleaseAck just this message") {
    val m1 = Message(
      `type` = "type_test", // PIURI,
      from = None, // Option[DidId],
      to = Seq(), // Seq[DidId],
      body = Json.Obj(),
      id = "id123",
      createdTime = None,
      expiresTimePlus = None,
      attachments = None,
      pthid = None,
      ack = None,
      pleaseAck = Some(Seq.empty),
    )
    val m1x = m1: org.didcommx.didcomm.message.Message
    val test: Option[Long] = Option(null.asInstanceOf[Long])
    println(test)
    val m1test = UnpackMessageImp.message(m1x)
    assertEquals(m1test, m1)
  }

  test("Message conversions - check pleaseAck this and some other message".fail) { // FIXME fail
    val m1 = Message(
      `type` = "type_test", // PIURI,
      from = None, // Option[DidId],
      to = Seq(), // Seq[DidId],
      body = Json.Obj(),
      id = "id123",
      createdTime = None,
      expiresTimePlus = None,
      attachments = None,
      thid = None,
      pthid = None,
      ack = None,
      pleaseAck = Some(Seq("not empty")),
    )
    val m1x = m1: org.didcommx.didcomm.message.Message
    val test: Option[Long] = Option(null.asInstanceOf[Long])
    println(test)
    val m1test = UnpackMessageImp.message(m1x)
    assertEquals(m1test, m1)
  }
}
