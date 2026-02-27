package org.hyperledger.identus.castor.controller.http

import sttp.tapir.*

object DIDInput {

  val didRefPathSegment = path[String]("didRef")
    .description(
      "Prism DID according to [the Prism DID method syntax](https://github.com/input-output-hk/prism-did-method-spec/blob/main/w3c-spec/PRISM-method.md#prism-did-method-syntax)"
    )
    .example("did:prism:4a5b5cf0a513e83b598bbea25cd6196746747f361a73ef77068268bc9bd732ff")

}
