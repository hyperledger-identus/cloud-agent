package org.hyperledger.identus.presentproof.controller.http

import org.hyperledger.identus.api.http.Annotation
import org.hyperledger.identus.presentproof.controller.http.PresentationStatusPage.annotations
import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.{description, encodedExample}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

import java.util.UUID

final case class PresentationStatusPage(
    @description(annotations.contents.description)
    @encodedExample( // This is a hammer - to be improved in the future
      JsonEncoder[Seq[PresentationStatus]].encodeJson(annotations.contents.example)
    )
    contents: Seq[PresentationStatus],
    @description(annotations.self.description)
    @encodedExample(annotations.self.example)
    self: String = "/present-proof/presentations",
    @description(annotations.kind.description)
    @encodedExample(annotations.kind.example)
    kind: String = "Collection",
    @description(annotations.pageOf.description)
    @encodedExample(annotations.pageOf.example)
    pageOf: String = "1",
    @description(annotations.next.description)
    @encodedExample(annotations.next.example)
    next: Option[String] = None,
    @description(annotations.previous.description)
    @encodedExample(annotations.previous.example)
    previous: Option[String] = None
)

object PresentationStatusPage {
  object annotations {
    object self
        extends Annotation[String](
          description = "The reference to the presentation collection itself.",
          example = "/present-proof/presentations"
        )
    object kind
        extends Annotation[String](
          description = "The type of object returned. In this case a `Collection`.",
          example = "Collection"
        )
    object pageOf
        extends Annotation[String](
          description = "Page number within the context of paginated response.",
          example = "1"
        )
    object next
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the next page of results. If the API response does not contain any more pages, this field should be set to None.",
          example = "/present-proof/presentations?offset=20&limit=10"
        )
    object previous
        extends Annotation[String](
          description =
            "An optional string field containing the URL of the previous page of results. If the API response is the first page of results, this field should be set to None.",
          example = "/present-proof/presentations?offset=0&limit=10"
        )
    object contents
        extends Annotation[Seq[PresentationStatus]](
          description = "A sequence of Presentation objects.",
          example = Seq(
            PresentationStatus(
              presentationId = "938bfc23-f78d-4734-9bf3-6dccf300856f",
              thid = "04112f4d-e894-4bff-a706-85b3e7190a2c",
              role = "Verifier",
              status = "RequestSent",
              proofs = Seq.empty,
              data = Seq.empty,
              disclosedClaims = None,
              requestData = Seq.empty,
              connectionId = Some("e0d81be9-47ca-4e0b-b8a7-325e8c3abc2f"),
              invitation = None,
              metaRetries = 5
            ),
            PresentationStatus(
              presentationId = "d22158b0-c650-48ea-be85-2920a845ef26",
              thid = "04112f4d-e894-4bff-a706-85b3e7190a2c",
              role = "Prover",
              status = "RequestReceived",
              proofs = Seq.empty,
              disclosedClaims = None,
              requestData = Seq.empty,
              data = Seq.empty,
              metaRetries = 5
            ),
            PresentationStatus(
              presentationId = "fd3f5e54-fae9-4f72-9413-ec66aab83a57",
              thid = "6b42fd91-4c98-40ae-a371-a1fd1a39e05e",
              role = "Prover",
              status = "PresentationPending",
              proofs = Seq.empty,
              disclosedClaims = None,
              requestData = Seq.empty,
              data = Seq.empty,
              metaRetries = 5
            ),
            PresentationStatus(
              presentationId = "e56dd3e0-79d0-45f4-ba6c-ff857211b07b",
              thid = "6b42fd91-4c98-40ae-a371-a1fd1a39e05e",
              role = "Verifier",
              status = "PresentationVerified",
              proofs = Seq.empty,
              disclosedClaims = None,
              requestData = Seq.empty,
              data = Seq(
                "{\"claimsToDisclose\":{\"emailAddress\":{},\"givenName\":{}},\"presentation\":\"{\\\"protected\\\":\\\"eyJhbGciOiJFZERTQSJ9\\\",\\\"payload\\\":\\\"eyJfc2QiOlsiMGl4d0tIV0dzbzFvZThFR0hQd2tGYW9EZE1TRFQ3SmgyNkZGSm1ZbGRnRSIsIjQ4VlFXZS1tcjBibHMyOWpicHFKeDNxX2dYY0k5N3dHcEpsZnRoNXQwMGciLCI0Wk9xanFNZVNUVHRKQTNJRExsc3ZXN0dTNzRIemNxY3N2NVFoZk1valE4IiwiUjhGRE0ydXB1V09mNmVJMVA5ckNPdG12c3puVWFFYXpncVNuN0JfeTE0MCIsIlU5MmpfUHlpcHN2TERNQTlDaVRWbnl3bUFzYTM4S2lDWm5TeVhyUE5mNG8iLCJldFB1Mmc5ajdRd01rZ3g5VnpEX1RnNTNUV3UydVpadk1KeHRnNEJ1WGJBIiwidGV3RG1LWklNcS10bUNrMkpqZU0wajNYbU1aUUFLN01heENVNlF4dm9OMCJdLCJfc2RfYWxnIjoic2hhLTI1NiIsImlzcyI6ImRpZDpwcmlzbToxMmEzOWI1YWEwZTcxODI3ZmMxYzYwMjg1ZDVlZWJjMTk0Yjg2NzFhYTJmY2QxZDM2NDBkMGYwMTBlMzliZmVlIiwiaWF0IjoxNzE3NDEwMzgzLCJleHAiOjE3MjAwMDIzODN9\\\",\\\"signature\\\":\\\"953FfSRU_0Y2q0ERrFPzbXJ_hkF0YQe5efwESaZwtXDCn8aanD3MUstp3lzqGZkhvcWRdtCCpIxzhy0zgKwLBg\\\",\\\"disclosures\\\":[\\\"WyI0SHF6MDZCeG5fRlJMb2hWX2lWNXp3IiwgImdpdmVuTmFtZSIsICJBbGljZSJd\\\",\\\"WyJLUnNYYU01c3NXZTl4UEhqQnNjT213IiwgImVtYWlsQWRkcmVzcyIsICJhbGljZUB3b25kZXJsYW5kLmNvbSJd\\\"],\\\"kb_jwt\\\":null}\"}"
              ),
              connectionId = Some("e0d81be9-47ca-4e0b-b8a7-325e8c3abc2f"),
              metaRetries = 5
            ),
            PresentationStatus(
              presentationId = "938bfc23-f78d-4734-9bf3-6dccf300856f",
              thid = "04112f4d-e894-4bff-a706-85b3e7190a2c",
              role = "Verifier",
              status = "InvitationGenerated",
              proofs = Seq.empty,
              disclosedClaims = None,
              data = Seq.empty,
              requestData = Seq.empty,
              connectionId = None,
              myDid = Some("did:peer:veriferPeerDID1234567890"),
              invitation = Some(
                OOBPresentationInvitation(
                  id = UUID.fromString("04112f4d-e894-4bff-a706-85b3e7190a2c"),
                  `type` = "didcomm/aip2;rfc0048/invitation",
                  from = "did:peer:veriferPeerDID1234567890",
                  invitationUrl =
                    "http://localhost:8000/present-proof/invitation?_oob=eyJpZCI6ImU2M2JkNzQ1LWZjYzYtNGQ0My05NjgzLTY4MjUyOTNlYTgxNiIsInR5cGUiOiJodHRwczovL2RpZGNvbW0ub3JnL291dC1vZi1iYW5kLzIuMC9pbnZpdGF0aW9uIiwiZnJvbSI6ImRpZDpwZWVyOjIuRXo2TFNoOWFSQmRFQlV6WkFRSzN5VnFBRnRYS0pVMVZ1cUZlMVd1U1ZRcnRvRGROZi5WejZNa3NCWmZkc3U4UmFxWjNmdjlBdkJ0elVGd1VyaW5td0xRODFNVjVoc29td2JZLlNleUowSWpvaVpHMGlMQ0p6SWpwN0luVnlhU0k2SW1oMGRIQTZMeTh4T1RJdU1UWTRMakV1TVRrNU9qZ3dOekF2Wkdsa1kyOXRiU0lzSW5JaU9sdGRMQ0poSWpwYkltUnBaR052YlcwdmRqSWlYWDE5IiwiYm9keSI6eyJnb2FsX2NvZGUiOiJwcmVzZW50LXZwIiwiZ29hbCI6IlJlcXVlc3QgcHJvb2Ygb2YgdmFjY2luYXRpb24gaW5mb3JtYXRpb24iLCJhY2NlcHQiOltdfSwiYXR0YWNobWVudHMiOlt7ImlkIjoiZTE5ZjNkNmMtY2U2Ni00Y2EwLWI1ZWUtZDBiY2ZhOGI1MTc3IiwibWVkaWFfdHlwZSI6ImFwcGxpY2F0aW9uL2pzb24iLCJkYXRhIjp7Impzb24iOnsiaWQiOiIxYjMwYzRjZi05MmVjLTQwOTMtYWFlOC1hZDk3NmIzODljY2MiLCJ0eXBlIjoiaHR0cHM6Ly9kaWRjb21tLmF0YWxhcHJpc20uaW8vcHJlc2VudC1wcm9vZi8zLjAvcmVxdWVzdC1wcmVzZW50YXRpb24iLCJib2R5Ijp7ImdvYWxfY29kZSI6IlJlcXVlc3QgUHJvb2YgUHJlc2VudGF0aW9uIiwid2lsbF9jb25maXJtIjpmYWxzZSwicHJvb2ZfdHlwZXMiOltdfSwiYXR0YWNobWVudHMiOlt7ImlkIjoiNDBiZjcyNzUtMDNkNS00MjI1LWFlYjAtMzhhZDYyODhhMThkIiwibWVkaWFfdHlwZSI6ImFwcGxpY2F0aW9uL2pzb24iLCJkYXRhIjp7Impzb24iOnsib3B0aW9ucyI6eyJjaGFsbGVuZ2UiOiIxMWM5MTQ5My0wMWIzLTRjNGQtYWMzNi1iMzM2YmFiNWJkZGYiLCJkb21haW4iOiJodHRwczovL3ByaXNtLXZlcmlmaWVyLmNvbSJ9LCJwcmVzZW50YXRpb25fZGVmaW5pdGlvbiI6eyJpZCI6IjkyODkyMjJmLWY3ZmItNDk4Yi1iMmE0LTNlODdiNzdiMzk5ZiIsImlucHV0X2Rlc2NyaXB0b3JzIjpbXX19fSwiZm9ybWF0IjoicHJpc20vand0In1dLCJ0aGlkIjoiZTYzYmQ3NDUtZmNjNi00ZDQzLTk2ODMtNjgyNTI5M2VhODE2IiwiZnJvbSI6ImRpZDpwZWVyOjIuRXo2TFNoOWFSQmRFQlV6WkFRSzN5VnFBRnRYS0pVMVZ1cUZlMVd1U1ZRcnRvRGROZi5WejZNa3NCWmZkc3U4UmFxWjNmdjlBdkJ0elVGd1VyaW5td0xRODFNVjVoc29td2JZLlNleUowSWpvaVpHMGlMQ0p6SWpwN0luVnlhU0k2SW1oMGRIQTZMeTh4T1RJdU1UWTRMakV1TVRrNU9qZ3dOekF2Wkdsa1kyOXRiU0lzSW5JaU9sdGRMQ0poSWpwYkltUnBaR052YlcwdmRqSWlYWDE5In19fV19"
                )
              ),
              metaRetries = 5
            ),
          )
        )
  }

  given encoder: JsonEncoder[PresentationStatusPage] =
    DeriveJsonEncoder.gen[PresentationStatusPage]

  given decoder: JsonDecoder[PresentationStatusPage] =
    DeriveJsonDecoder.gen[PresentationStatusPage]

  given schema: Schema[PresentationStatusPage] = Schema.derived
}
