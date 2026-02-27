package org.hyperledger.identus.castor.core.model.did

import com.google.protobuf.ByteString
import io.iohk.atala.prism.protos.node_models
import org.hyperledger.identus.shared.crypto.Sha256Hash
import org.hyperledger.identus.shared.models.Base64UrlString
import zio.*
import zio.test.*
import zio.test.Assertion.*

object PrismDIDSpec extends ZIOSpecDefault {

  private val canonicalSuffixHex = "9b5118411248d9663b6ab15128fba8106511230ff654e7514cdcc4ce919bde9b"
  private val canonicalSuffix = Sha256Hash.fromHex(canonicalSuffixHex)
  private val encodedStateUsedBase64 =
    "Cj8KPRI7CgdtYXN0ZXIwEAFKLgoJc2VjcDI1NmsxEiEDHpf-yhIns-LP3tLvA8icC5FJ1ZlBwbllPtIdNZ3q0jU"

  private val short = PrismDID.buildCanonical(canonicalSuffix.bytes.toArray).toOption.get
  private val long = PrismDID
    .buildLongFormFromAtalaOperation(
      node_models.AtalaOperation.parseFrom(Base64UrlString.fromStringUnsafe(encodedStateUsedBase64).toByteArray)
    )
    .toOption
    .get
  override def spec = suite("PrismDID")(didParserSpec)

  private val didParserSpec = suite("PrismDID.fromString")(
    test("success for valid DID") {
      val stateHash = Sha256Hash.compute(Array()).bytes.toArray
      val validDID = PrismDID.buildCanonical(stateHash).toOption.get
      val unsafeDID = PrismDID.fromString(validDID.toString)
      assert(unsafeDID)(isRight(equalTo(validDID)))
    },
    test("success for long form string") {
      val longAsString = long.toString
      val unsafeDID = PrismDID.fromString(longAsString)
      assert(unsafeDID)(isRight(equalTo(long)))
    },
    test("success for canonical form string") {
      val canonicalAsString = short.toString
      val unsafeDID = PrismDID.fromString(canonicalAsString)
      assert(unsafeDID)(isRight(equalTo(short)))
    },
    test("fail for invalid DID") {
      val unsafeDID = PrismDID.fromString("invalid-did")
      assert(unsafeDID)(isLeft)
    },
    test("fail for long form initial state is not CreateDID") {
      val mockAtalaOperation = node_models.AtalaOperation(
        node_models.AtalaOperation.Operation.UpdateDid(
          node_models.UpdateDIDOperation(
            previousOperationHash = ByteString.EMPTY,
            id = "update0",
            actions = Nil
          )
        )
      )
      val encodedState = mockAtalaOperation.toByteArray
      val encodedStateBase64 = Base64UrlString.fromByteArray(encodedState).toStringNoPadding
      val stateHash = Sha256Hash.compute(encodedState).hexEncoded
      val didString = s"did:prism:$stateHash:$encodedStateBase64"
      val unsafeDID = PrismDID.fromString(didString)
      assert(unsafeDID)(isLeft(containsString("CreateDid Atala operation expected")))
    },
    test("parsing long form examples and convert it back should be the same") {
      val longFormDIDs = Seq(
        // from TS SDK
        "did:prism:2c6c7c7490a4196f0b3877c83ef18255f327ecb26e0de2df5fa72618f931a3d4:CtMBCtABEmIKDW1hc3RlcihpbmRleCkQAUJPCglzZWNwMjU2azESIAfAhyZhkEJmSB_jJTzIW0u6jTui-_-Ac8qDhcbaAb-AGiALZ0fogk8QyDqud03bRAYxtcZJPElxHd3pnNCSbM05NRJqChVhdXRoZW50aWNhdGlvbihpbmRleCkQBEJPCglzZWNwMjU2azESIAfAhyZhkEJmSB_jJTzIW0u6jTui-_-Ac8qDhcbaAb-AGiALZ0fogk8QyDqud03bRAYxtcZJPElxHd3pnNCSbM05NQ",
        // from Switft SDK
        "did:prism:f2c267b1e7426c9b6bc9853c0521e08514cba95164c6c2ca1fef5f719df0bfa4:CtMBCtABEmIKDW1hc3RlcihpbmRleCkQAUJPCglzZWNwMjU2azESIEOoZGnyVFlIkzVHcdF57Bg1dWpX_EbaxDm8D4mEgLK7GiCiwmj120GSFX0Mo1BjtGMDi0sCsIoKyS0rwC8qAznFkxJqChVhdXRoZW50aWNhdGlvbihpbmRleCkQBEJPCglzZWNwMjU2azESIEOoZGnyVFlIkzVHcdF57Bg1dWpX_EbaxDm8D4mEgLK7GiCiwmj120GSFX0Mo1BjtGMDi0sCsIoKyS0rwC8qAznFkw",
        // from 1.4 SDK
        s"did:prism:$canonicalSuffixHex:$encodedStateUsedBase64"
      )
      val parsedDIDs = longFormDIDs.flatMap(PrismDID.fromString(_).toOption.map(_.toString))
      assert(parsedDIDs)(hasSameElements(longFormDIDs))
    }
  )

}
