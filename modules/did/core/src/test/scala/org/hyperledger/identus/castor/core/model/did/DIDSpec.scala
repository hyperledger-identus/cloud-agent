package org.hyperledger.identus.castor.core.model.did

import org.hyperledger.identus.castor.core.util.GenUtils
import zio.*
import zio.test.*
import zio.test.Assertion.*

object DIDSpec extends ZIOSpecDefault {

  override def spec = suite("DID")(fromStringSpec, fromStringSpecFrom14Sdk)

  private val fromStringSpec = suite("DID.fromString")(
    test("parse valid PRISM DID that breaks sdk 1.4 parser") {
      val poisonDid =
        "did:prism:c191d4dfe2806d59df4632f78d38f80dfbfbd88187804783717e29116ffb33d5:Cu0CCuoCEjoKBmF1dGgtMRAESi4KCXNlY3AyNTZrMRIhAz3656yBEIzdpZuG3yYn8Npoty3_qQhIbpOC8QmdVnpeEj8KC2Fzc2VydGlvbi0xEAJKLgoJc2VjcDI1NmsxEiECbZrA1SxPTpIEl9VBY4a6hGqaPJbJE7v8U5b236bCbWUSOwoHbWFzdGVyMBABSi4KCXNlY3AyNTZrMRIhA0BNFWJYhLqaVji9EYfIvdid8lQ6lJciigcytFPb7TiiGjoKE2h0dHBzOi8vZm9vLmJhci5jb20SDUxpbmtlZERvbWFpbnMaFGh0dHBzOi8vZm9vLmJhci5jb20vGjgKEmh0dHBzOi8vdXBkYXRlLmNvbRINTGlua2VkRG9tYWlucxoTaHR0cHM6Ly91cGRhdGUuY29tLxo4ChJodHRwczovL3JlbW92ZS5jb20SDUxpbmtlZERvbWFpbnMaE2h0dHBzOi8vcmVtb3ZlLmNvbS8"
      val parsed = DID.fromString(poisonDid)
      assert(parsed.map(_.toString))(isRight(equalTo(poisonDid))) &&
      assert(parsed.map(_.method.toString))(isRight(equalTo("prism"))) &&
      assert(parsed.map(_.methodSpecificId.toString))(
        isRight(
          equalTo(
            "c191d4dfe2806d59df4632f78d38f80dfbfbd88187804783717e29116ffb33d5:Cu0CCuoCEjoKBmF1dGgtMRAESi4KCXNlY3AyNTZrMRIhAz3656yBEIzdpZuG3yYn8Npoty3_qQhIbpOC8QmdVnpeEj8KC2Fzc2VydGlvbi0xEAJKLgoJc2VjcDI1NmsxEiECbZrA1SxPTpIEl9VBY4a6hGqaPJbJE7v8U5b236bCbWUSOwoHbWFzdGVyMBABSi4KCXNlY3AyNTZrMRIhA0BNFWJYhLqaVji9EYfIvdid8lQ6lJciigcytFPb7TiiGjoKE2h0dHBzOi8vZm9vLmJhci5jb20SDUxpbmtlZERvbWFpbnMaFGh0dHBzOi8vZm9vLmJhci5jb20vGjgKEmh0dHBzOi8vdXBkYXRlLmNvbRINTGlua2VkRG9tYWlucxoTaHR0cHM6Ly91cGRhdGUuY29tLxo4ChJodHRwczovL3JlbW92ZS5jb20SDUxpbmtlZERvbWFpbnMaE2h0dHBzOi8vcmVtb3ZlLmNvbS8"
          )
        )
      )
    },
    test("parse any valid long-form PRISM DID") {
      check(GenUtils.createOperation) { operation =>
        val prismDID = PrismDID.buildLongFormFromOperation(operation)
        val did = prismDID.toString
        val parsed = DID.fromString(did)
        assert(parsed.map(_.toString))(isRight(equalTo(did)))
      }
    },
    test("parse any valid short-form PRISM DID") {
      check(GenUtils.createOperation) { operation =>
        val prismDID = PrismDID.buildLongFormFromOperation(operation)
        val did = prismDID.asCanonical.toString
        val parsed = DID.fromString(did)
        assert(parsed.map(_.toString))(isRight(equalTo(did)))
      }
    },
    test("parse all DIDs from https://github.com/w3c/did-test-suite.git") {
      /*
       * Getting test inputs
       *
       * git clone https://github.com/w3c/did-test-suite.git
       * cd did-test-suite
       * cd packages/did-core-test-server/suites/implementations
       * find . -name 'did-*.json' -exec bash -c "cat {} | jq '.dids' | grep 'did:'" \;
       */
      val dids = Seq(
        "did:3:kjzl6cwe1jw145m7jxh4jpa6iw1ps3jcjordpo81e0w04krcpz8knxvg5ygiabd",
        "did:algo:56da1708-eead-4e2d-9558-f53d684003fd",
        "did:art:enq:f045c5c7d50145b65ca2702c38b4e2d46658293c",
        "did:cheqd:mainnet:zF7rhDBfUt9d1gJPjx7s1JXfUY7oVWkY",
        "did:ebsi:znHeZWvhAK2FK2Dk1jXNe7m",
        "did:elem:ropsten:EiBVk9F3eLf2u9xwLJ91-vTIXD-B7Q4m3iGhCbB2OyRiwQ",
        "did:ethr:0x26bf14321004e770e7a8b080b7a526d8eed8b388",
        "did:example:123",
        "did:ion:EiCUAQbYJzzCY1zL8KYmTu8MxCkFwG_cjRcZI2bRpwDQkQ:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiJzaWdfMDY0YmViY2MiLCJwdWJsaWNLZXlKd2siOnsiY3J2Ijoic2VjcDI1NmsxIiwia3R5IjoiRUMiLCJ4IjoibHV2TG1tbEc0NFZmcWx1aFh3SWpTdVQxVEwxc0VfMTFnQ0RlazJQbXVpbyIsInkiOiJ0WURKWmFpWDdBN2tydmNhcEFnS0pGNjd6ejVKUzF3bjRGdWNqa09kUUVvIn0sInB1cnBvc2VzIjpbImF1dGhlbnRpY2F0aW9uIiwiYXNzZXJ0aW9uTWV0aG9kIl0sInR5cGUiOiJFY2RzYVNlY3AyNTZrMVZlcmlmaWNhdGlvbktleTIwMTkifV0sInNlcnZpY2VzIjpbeyJpZCI6ImxpbmtlZGRvbWFpbnMiLCJzZXJ2aWNlRW5kcG9pbnQiOnsib3JpZ2lucyI6WyJodHRwczovL2FkbWluLXRlc3RzLWRvbWFpbi5jb20vIl19LCJ0eXBlIjoiTGlua2VkRG9tYWlucyJ9XX19XSwidXBkYXRlQ29tbWl0bWVudCI6IkVpQi13MlVfbW5aRUVRWmRoNUNsVWszMERBdjZrTXNMT05TSWdxaGp5WEluLUEifSwic3VmZml4RGF0YSI6eyJkZWx0YUhhc2giOiJFaURyNk1nVWtSSm9maGVqTW96VXVoSXBSX0tFa3J1WlZGaDlheHVzS2I5cnFRIiwicmVjb3ZlcnlDb21taXRtZW50IjoiRWlBS1VxUmJTTkJKSHFUQTljbXJ1MnBtQkVvd2tIVVFVLW9CS3Q3NWQ3QU8wQSJ9fQ",
        "did:is:PMW1Ks7h4brpN8FdDVLwhPDKJ7LdA7mVdd",
        "did:jnctn:187c4af8932a444a9e9503fb96cb672f",
        "did:key:z2J9gaYxrKVpdoG9A4gRnmpnRCcxU6agDtFVVBVdn1JedouoZN7SzcyREXXzWgt3gGiwpoHq7K68X4m32D8HgzG8wv3sY5j7",
        "did:key:z5TcCQtximJCYYLLmpUhydMUfyppwqQFveNQcrmLxYqbCvDrrcu9rVrHwNZEN37CWMUBRd8xgEyPighrGMMmX8NWTnSPUuWPPeFyUhLmkgA1Vqgm3eQYHF4ye7WrkB7jYcWoa68oHQNuSzw6ezgebFtt27uvJG4yjdat8Wj1e2qPMjsR63xQbmNdDTQ4zi8GDz8EwVAgu",
        "did:key:z6LSn9Ah7d33uokFv2pg66BMN5UY72WtPE6eFjGXrA4mPcCp",
        "did:key:z6MkjPrEBMHGuJubLZ5HWf2jBreAuh7onKCA6BknWXYHLxjS",
        "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH",
        "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH",
        "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH",
        "did:key:z6MktZw8HgaRUoG8S9asnmDKQL458uEhuuNT9U2UK5cT6Tmh",
        "did:key:z82Lm1MpAkeJcix9K8TMiLd5NMAhnwkjjCBeWHXyu3U4oT2MVJJKXkcVBgjGhnLBn2Kaau9",
        "did:key:zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169",
        "did:key:zDnaerx9CtbPJ1q36T5Ln5wYt3MQYeGRG5ehnPAmxcf5mDZpv",
        "did:key:zQ3shokFTS3brHcDQrn82RUDfCZESWL1ZdCEJwekUDPQiYBme",
        "did:key:zQ3shwNhfEjorJrrKpqvBvNRV35NfGmVWdx2rNmQCRR58Sfpf",
        "did:key:zUC7LbYAQUjoTVSJyieL3cxpbdA1QjWdqqtFMDoMRg4qkZtQWRrrd4LLVCboCd5xbxET3gNM6ALinG57wBZo5VoQ3AokhE9qpJehX4SHdsDJUGa9u3z22PEGLd1fBwzzLhTkJmV",
        "did:kilt:04siJtc4dYq2gPre8Xj6KJcSjVAdi1gmjctUzjf3AwrtNnhvy",
        "did:kilt:14siJtc4dYq2gPre8Xj6KJcSjVAdi1gmjctUzjf3AwrtNnhvy",
        "did:lit:AEZ87t1bi5bRxmVh3ksMUi",
        "did:monid:1fb352353ff51248c5104b407f9c04c3666627fcf5a167d693c9fc84b75964e2",
        "did:nft:eip155.1_erc721.0xb300a43751601bd54ffee7de35929537b28e1488_2",
        "did:onion:fscst5exmlmr262byztwz4kzhggjlzumvc2ndvgytzoucr2tkgxf7mid",
        "did:orb:bafkreiazah4qrybzyapmrmk2dhldz24vfmavethcrgcoq7qhic63zz55ru:EiAag4cmgxAE2isL5HG3mxjS7WRq4l-xyyTgULCAcEHQQQ",
        "did:orb:bafkreibcsubh3ifub7gletz27hcdyhwvrhlh5mwfth2m5fbasqua6yalay:EiA2ZtZqXjKZt-yf19ersmaCYm-gJEnlixrfk0Mi61ETTg",
        "did:orb:bafkreihp4inweep4py7gw4j7hej5mqlbwa7br4u7mtrfxr5khfwpu3qu3m:EiB2tmdM_oWwjXj6AmVLm0RFa_8XKZHipOpNGpEODIVN8Q",
        "did:orb:interim:EiAQ1HmY03Cx4OMhiuYHl8q-B1JYlkT1Wns-dhhccUIl5g:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJhZGQtcHVibGljLWtleXMiLCJwdWJsaWNLZXlzIjpbeyJpZCI6ImNyZWF0ZUtleSIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJQLTI1NiIsImt0eSI6IkVDIiwieCI6InJILURtZmNkRVZERi1vNm80ellxdjl2YlhPcFFFcDd3RC1XUHFDbl9ELXciLCJ5IjoidnBiRGNQX2YwS1JoRW02Sm93Y0oxbWlNTldJRXo2YWVnRHFDek80WXNxSSJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiSnNvbldlYktleTIwMjAifSx7ImlkIjoiYXV0aCIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJFZDI1NTE5Iiwia3R5IjoiT0tQIiwieCI6IjEzNkZDRjJTSEZNMUZ6aWlJYXJwNEI1RzkxUVNnNHB1dGFhSWg1VEdXREEiLCJ5IjoiIn0sInB1cnBvc2VzIjpbImFzc2VydGlvbk1ldGhvZCJdLCJ0eXBlIjoiRWQyNTUxOVZlcmlmaWNhdGlvbktleTIwMTgifV19LHsiYWN0aW9uIjoiYWRkLXNlcnZpY2VzIiwic2VydmljZXMiOlt7ImlkIjoiZGlkY29tbSIsInByaW9yaXR5IjowLCJyZWNpcGllbnRLZXlzIjpbIjZLWjZLQkZLZDQ3d3FEMlhnelJwTDZ0RGlxQkNnQzg1eEttbzhVRHExZjVSIl0sInJvdXRpbmdLZXlzIjpbIjhrNUY0bXVQN0s3NmVtZHdQNWlDOHJlRVlWb1NiWmdMM2FudmFXOTdUTm1yIl0sInNlcnZpY2VFbmRwb2ludCI6Imh0dHBzOi8vaHViLmV4YW1wbGUuY29tLy5pZGVudGl0eS9kaWQ6ZXhhbXBsZTowMTIzNDU2Nzg5YWJjZGVmLyIsInR5cGUiOiJkaWQtY29tbXVuaWNhdGlvbiJ9XX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlEVXVaSFEwOENXRGVBTmJyc3VSeHh3M2V5bXNucFdNbzJ0TXQ3QUNlUUNIUSJ9LCJzdWZmaXhEYXRhIjp7ImFuY2hvck9yaWdpbiI6Imh0dHBzOi8vb3JiLmRvbWFpbjEuY29tL3NlcnZpY2VzL29yYiIsImRlbHRhSGFzaCI6IkVpQUluS05tb0d1WDJVajI1aGFCNDdGQlF4aGpmb0lJYzc3Y2h6N0p0enJXdVEiLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUNIOWF3WHZQUFZZdVBneEw2WUFQX3FaeUktMzdxclcwQkdFT2o5cnJWbHd3In19",
        "did:orb:interim:EiCYgffdSsqLTXT6PRYLPr6vvgn9PVecJ5nFUGh9hXgOxQ",
        "did:orb:interim:EiDHdXhNm7LuCqxo4JvAwKYKiFmpf85YFswAovxTxI_y4Q",
        "did:orb:ipfs:QmS4ZME5uEPtQ2DFDwhSZYtLxzFxCYjJ6kC7o3ypwanzFm:EiACG5GI9dK1fjnCMYMA6ZFhtP75HVhunEuqW-XDCAU7Ew",
        "did:orb:ipfs:QmfJFePqcopDUYttpvWgec9LKeJhnwh4UjhwUJz5ZcRUqM:EiDwFxa7ooPvKDTqpemH-R-H0pNX9VzUEUzk8AZsMCf9pg",
        "did:orb:ipfs:QmfX6CHk7AC43Xq9iFK9XzgH3a7kJeAn3ewWZxEcqur2wE:EiCnmXoUEEP-04kELpPiF7Ss5GesCCedfTgRPA30SJO5KQ",
        "did:orb:ipfs:bafkreiacr3ga6zilvzatpcixq5mz4uvgld7yedutgcssvnmql44o6rc7yy:EiB2k0ytmo-qi_M7jGocxvj4P9D6VQJGl6gRy4f6-UUpTw",
        "did:orb:webcas:testnet.orb.local:bafkreihdnftiso5b7bzmhhi65nzsutbcuv6mtrmuquzoqlrk7joyer45uq:EiARiEOCLK3GnRVHA_yF92tX3aoSJAVqW1bh7Enre1iDXw",
        "did:photon:EiDS68FUZqv0da57WLI_t9Gl5TYGNxvWR3PGgRk9oXx85Q",
        "did:pkh:btc:128Lkh3S7CkDTBZ8W7BbpsN3YYizJMp8p6",
        "did:pkh:celo:0xa0ae58da58dfa46fa55c3b86545e7065f90ff011",
        "did:pkh:doge:DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L",
        "did:pkh:eth:0xb9c5714089478a327f09197987f16f9e5d936e8a",
        "did:pkh:sol:CKg5d12Jhpej1JqtmxLJgaFqqeYjxgPqToJ4LBdvG9Ev",
        "did:pkh:tz:tz1YwA1FwpgLtc1G8DKbbZ6e6PTb1dQMRn5x",
        "did:polygon:0xBCFdE12C425E4CbDb45226Fe51F89F2d99667d3E",
        "did:schema:public-ipfs:xsd:QmUQAxKQ5sbWWrcBZzwkThktfUGZvuPQyTrqMzb3mZnLE5",
        "did:sov:mattr-dev:3WhAjtBidfhGbiAyNQBxPP",
        "did:ssb:ed25519:f_6sQ6d2CMxRUhLpspgGIulDxDCwYD7DzFzPNr7u5AU",
        "did:trust:tc:dev:id:GvMM3dpmWH6mRhGK88Ykdh",
        "did:tz:delphinet:tz1WvvbEGpBXGeTVbLiR6DYBe1izmgiYuZbq",
        "did:tz:tz1YwA1FwpgLtc1G8DKbbZ6e6PTb1dQMRn5x",
        "did:tz:tz2BFTyPeYRzxd5aiBchbXN3WCZhx7BqbMBq",
        "did:tz:tz3agP9LGe2cXmKQyYn6T68BHKjjktDbbSWX",
        "did:unisot:test:mtF5XVLJvXEeffY8fo2eUfpXqs9CqQzpj7",
        "did:v1:nym:z6Mkh18zyRvTikTTYwi3p4S8kQNqLkExDamQHERxeB34AMvL",
        "did:vaa:2H9XwzRXZ1o5ZwSoYDEZn24eHXcQ",
        "did:web:demo.spruceid.com:2021:07:08",
        "did:web:did.actor:healthcare:doctor:robert",
        "did:web:did.actor:mike",
        "did:web:did.actor:mike",
        "did:web:evernym.com",
        "did:web:kyledenhartog.com",
        "did:web:or13.github.io:deno-did-pm",
        "did:webkey:ssh:demo.spruceid.com:2021:07:14:keys",
      )
      val parsed = dids.map(DID.fromString)
      assert(parsed)(forall(isRight(anything))) &&
      assert(parsed.flatMap(_.toOption).map(_.toString))(equalTo(dids))
    },
    test("reject empty string") {
      val did = ""
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    },
    test("reject did without method specific id section") {
      val did = "did:testmethod"
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    },
    test("reject empty method specific id") {
      val did = "did:testmethod:"
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    },
    test("reject method specific id ':'") {
      val did = "did:testmethod::"
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    }
  )

  private val fromStringSpecFrom14Sdk = suite("DID.fromString - 1.4 sdk cases")(
    test("simple") {
      val did = "did:testmethod:testmethodspecificid"
      val parsed = DID.fromString(did)
      assert(parsed.map(_.method.toString()))(isRight(equalTo("testmethod"))) &&
      assert(parsed.map(_.methodSpecificId.toString()))(isRight(equalTo("testmethodspecificid")))
    },
    test("multi-section") {
      val did = "did:testmethod:section1:section2"
      val parsed = DID.fromString(did)
      assert(parsed.map(_.method.toString()))(isRight(equalTo("testmethod"))) &&
      assert(parsed.map(_.methodSpecificId.toString()))(isRight(equalTo("section1:section2")))
    },
    test("special characters") {
      val did = "did:testmethod123:foo.sec_1:foo.sec-2"
      val parsed = DID.fromString(did)
      assert(parsed.map(_.method.toString()))(isRight(equalTo("testmethod123"))) &&
      assert(parsed.map(_.methodSpecificId.toString()))(isRight(equalTo("foo.sec_1:foo.sec-2")))
    },
    test("empty section") {
      val did = "did:testmethod::section2"
      val parsed = DID.fromString(did)
      assert(parsed.map(_.method.toString()))(isRight(equalTo("testmethod"))) &&
      assert(parsed.map(_.methodSpecificId.toString()))(isRight(equalTo(":section2")))
    },
    test("fail on non-did scheme") {
      val did = "nondid:testmethod:section1:section2"
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    },
    test("fail on invalid character") {
      val did1 = "did:a^bc:test"
      val did2 = "did:abc:te^st"
      val parsed1 = DID.fromString(did1)
      val parsed2 = DID.fromString(did2)
      assert(parsed1)(isLeft) &&
      assert(parsed2)(isLeft)
    },
    test("fail on empty method name") {
      val did = "did::section1:section2"
      val parsed = DID.fromString(did)
      assert(parsed)(isLeft)
    }
  )
}
