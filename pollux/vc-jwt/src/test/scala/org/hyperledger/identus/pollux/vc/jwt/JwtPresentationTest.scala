package org.hyperledger.identus.pollux.vc.jwt

import zio.test.*

object JwtPresentationTest extends ZIOSpecDefault {
  val jwt = JWT(
    "eyJraWQiOiJteS1hdXRoLWtleSIsInR5cCI6IkpXVCIsImFsZyI6IkVTMjU2SyJ9.eyJpc3MiOiJkaWQ6cHJpc206YjgwZjAxMTZhYWY2OTI5MGRkMzJiZDE0OTNmN2IxYWJhMDM5OTYyM2JkNDk5Mzk3NTRjNThhNGNmZTU4M2QwYzpDcGNDQ3BRQ0VqOEtDMjE1TFdGMWRHZ3RhMlY1RUFSS0xnb0pjMlZqY0RJMU5tc3hFaUVDQnViQkpoNDhRNjhqOTJZS3NjMVFqQ0prOHFvRXBMamRoejNRRVFzRWpWRVNTZ29XYlhrdGEyVjVMV0Z6YzJWeWRHbHZiazFsZEdodlpCQUNTaTRLQ1hObFkzQXlOVFpyTVJJaEFoTENSMkhTa3NOWUh2Y0dCUWZzQVZrdDNFa1pMSVpMVEhYcFc4ckJRRHI5RWpzS0IyMWhjM1JsY2pBUUFVb3VDZ2x6WldOd01qVTJhekVTSVFMRC10d3c1SklVbzA2dXQ5MDQwaTZ5dTdhQUhMcWdxajdUcHBZNlUtQzhrQnBJQ2c1aFoyVnVkQzFpWVhObExYVnliQklRVEdsdWEyVmtVbVZ6YjNWeVkyVldNUm9rYUhSMGNEb3ZMekU1TWk0eE5qZ3VNUzQ0TmpvNU1EQXdMMk5zYjNWa0xXRm5aVzUwIiwiYXVkIjoiaHR0cHM6XC9cL3ByaXNtLXZlcmlmaWVyLmNvbSIsInZwIjp7InR5cGUiOlsiVmVyaWZpYWJsZVByZXNlbnRhdGlvbiJdLCJAY29udGV4dCI6WyJodHRwczpcL1wvd3d3LnczLm9yZ1wvMjAxOFwvcHJlc2VudGF0aW9uc1wvdjEiXSwidmVyaWZpYWJsZUNyZWRlbnRpYWwiOlsiZXlKMGVYQWlPaUpLVjFRaUxDSmhiR2NpT2lKRlpFUlRRU0o5LmV5SnBjM01pT2lKa2FXUTZjSEpwYzIwNk1HWmxaVFF4Wm1Sa05qZ3dOemMyTTJOall6VXpPV015T1RnME1XRTBNVGhqTnpaaE9EbGlNMlk0WTJRMlpHRTRPV1JsTW1FelpERmhNRFExTnpGbU5UcERjSE5EUTNCblEwVnJXVXRHVnpFMVRGZDBiR1ZUTVdoa1dGSnZXbGMxTUdGWFRtaGtSMngyWW1oQlJWTnBjMHRDTUZaclRXcFZNVTFVYTFOSlJIVjVSVlF4YUVOQmRVTjJhek5QYm05bmJETTRZMDFwU201NFgycHJURmxUVG5kWFRVbEJiblJzVEVWclkwdEdiVEUxVEZkMGJHVlRNV2hqTTA1c1kyNVNjR0l5TlU1YVdGSnZZakpSVVVGcmIzSkRaMlJHV2tSSk1VNVVSVFZGYVVOYVFWSkpibE5rVWtwRVN5MUZUSEp4VjI5a01YZE9UMGhvZFhSM1dsSkVTMG90YnpWQlpraHlZbkpTU1RkRFoyUjBXVmhPTUZwWVNYZEZRVVpMVEdkdlNtTXlWbXBqUkVreFRtMXplRVZwUlVSSE1XMXpkV056VlhOa1VVOHRibGx3Y2xGWU5HOUJXRnBJVnpKak9GQlNWM3BTWkZOMGF6WXdPRFZKWVZOQmIwOVpWMlJzWW01UmRGbHRSbnBhVXpFeFkyMTNVMFZGZUhCaWJYUnNXa1pLYkdNeU9URmpiVTVzVm1wRllVcEhhREJrU0VFMlRIazRlRTlVU1hWTlZGazBUR3BGZFU5RVdUWlBSRUYzVFVNNWFtSkhPVEZhUXpGb1dqSldkV1JCSWl3aWMzVmlJam9pWkdsa09uQnlhWE50T21JNE1HWXdNVEUyWVdGbU5qa3lPVEJrWkRNeVltUXhORGt6WmpkaU1XRmlZVEF6T1RrMk1qTmlaRFE1T1RNNU56VTBZelU0WVRSalptVTFPRE5rTUdNNlEzQmpRME53VVVORmFqaExRekl4TlV4WFJqRmtSMmQwWVRKV05VVkJVa3RNWjI5S1l6SldhbU5FU1RGT2JYTjRSV2xGUTBKMVlrSkthRFE0VVRZNGFqa3lXVXR6WXpGUmFrTkthemh4YjBWd1RHcGthSG96VVVWUmMwVnFWa1ZUVTJkdlYySllhM1JoTWxZMVRGZEdlbU15Vm5sa1IyeDJZbXN4YkdSSGFIWmFRa0ZEVTJrMFMwTllUbXhaTTBGNVRsUmFjazFTU1doQmFFeERVakpJVTJ0elRsbElkbU5IUWxGbWMwRldhM1F6Uld0YVRFbGFURlJJV0hCWE9ISkNVVVJ5T1VWcWMwdENNakZvWXpOU2JHTnFRVkZCVlc5MVEyZHNlbHBYVG5kTmFsVXlZWHBGVTBsUlRFUXRkSGQzTlVwSlZXOHdOblYwT1RBME1HazJlWFUzWVVGSVRIRm5jV28zVkhCd1dUWlZMVU00YTBKd1NVTm5OV2hhTWxaMVpFTXhhVmxZVG14TVdGWjVZa0pKVVZSSGJIVmhNbFpyVlcxV2VtSXpWbmxaTWxaWFRWSnZhMkZJVWpCalJHOTJUSHBGTlUxcE5IaE9hbWQxVFZNME5FNXFielZOUkVGM1RESk9jMkl6Vm10TVYwWnVXbGMxTUNJc0ltNWlaaUk2TVRjek1qQXhOVEl6TVN3aVpYaHdJam94TnpNeU1ERTRPRE14TENKMll5STZleUpqY21Wa1pXNTBhV0ZzVTJOb1pXMWhJanBiZXlKcFpDSTZJbWgwZEhBNlhDOWNMekU1TWk0eE5qZ3VNUzQ0TmpvNE1EQXdYQzlqYkc5MVpDMWhaMlZ1ZEZ3dmMyTm9aVzFoTFhKbFoybHpkSEo1WEM5elkyaGxiV0Z6WEM4Mk16TmhOamhtTnkwMFpUZGlMVE13TnpNdFlUbGhNQzA0WVdVNU5qUmtZVFU1TmpjaUxDSjBlWEJsSWpvaVEzSmxaR1Z1ZEdsaGJGTmphR1Z0WVRJd01qSWlmVjBzSW1OeVpXUmxiblJwWVd4VGRXSnFaV04wSWpwN0ltVnRZV2xzUVdSa2NtVnpjeUk2SW1Gc2FXTmxRSGR2Ym1SbGNteGhibVF1WTI5dElpd2laSEpwZG1sdVowTnNZWE56SWpvekxDSm1ZVzFwYkhsT1lXMWxJam9pVjI5dVpHVnliR0Z1WkNJc0ltZHBkbVZ1VG1GdFpTSTZJa0ZzYVdObElpd2laSEpwZG1sdVoweHBZMlZ1YzJWSlJDSTZJakV5TXpRMUlpd2lhV1FpT2lKa2FXUTZjSEpwYzIwNllqZ3daakF4TVRaaFlXWTJPVEk1TUdSa016SmlaREUwT1RObU4ySXhZV0poTURNNU9UWXlNMkprTkRrNU16azNOVFJqTlRoaE5HTm1aVFU0TTJRd1l6cERjR05EUTNCUlEwVnFPRXRETWpFMVRGZEdNV1JIWjNSaE1sWTFSVUZTUzB4bmIwcGpNbFpxWTBSSk1VNXRjM2hGYVVWRFFuVmlRa3BvTkRoUk5qaHFPVEpaUzNOak1WRnFRMHByT0hGdlJYQk1hbVJvZWpOUlJWRnpSV3BXUlZOVFoyOVhZbGhyZEdFeVZqVk1WMFo2WXpKV2VXUkhiSFppYXpGc1pFZG9kbHBDUVVOVGFUUkxRMWhPYkZrelFYbE9WRnB5VFZKSmFFRm9URU5TTWtoVGEzTk9XVWgyWTBkQ1VXWnpRVlpyZERORmExcE1TVnBNVkVoWWNGYzRja0pSUkhJNVJXcHpTMEl5TVdoak0xSnNZMnBCVVVGVmIzVkRaMng2V2xkT2QwMXFWVEpoZWtWVFNWRk1SQzEwZDNjMVNrbFZiekEyZFhRNU1EUXdhVFo1ZFRkaFFVaE1jV2R4YWpkVWNIQlpObFV0UXpoclFuQkpRMmMxYUZveVZuVmtRekZwV1ZoT2JFeFlWbmxpUWtsUlZFZHNkV0V5Vm10VmJWWjZZak5XZVZreVZsZE5VbTlyWVVoU01HTkViM1pNZWtVMVRXazBlRTVxWjNWTlV6UTBUbXB2TlUxRVFYZE1NazV6WWpOV2EweFhSbTVhVnpVd0lpd2laR0YwWlU5bVNYTnpkV0Z1WTJVaU9pSXlNREl3TFRFeExURXpWREl3T2pJd09qTTVLekF3T2pBd0luMHNJblI1Y0dVaU9sc2lWbVZ5YVdacFlXSnNaVU55WldSbGJuUnBZV3dpWFN3aVFHTnZiblJsZUhRaU9sc2lhSFIwY0hNNlhDOWNMM2QzZHk1M015NXZjbWRjTHpJd01UaGNMMk55WldSbGJuUnBZV3h6WEM5Mk1TSmRMQ0pwYzNOMVpYSWlPbnNpYVdRaU9pSmthV1E2Y0hKcGMyMDZNR1psWlRReFptUmtOamd3TnpjMk0yTmpZelV6T1dNeU9UZzBNV0UwTVRoak56WmhPRGxpTTJZNFkyUTJaR0U0T1dSbE1tRXpaREZoTURRMU56Rm1OVHBEY0hORFEzQm5RMFZyV1V0R1Z6RTFURmQwYkdWVE1XaGtXRkp2V2xjMU1HRlhUbWhrUjJ4MlltaEJSVk5wYzB0Q01GWnJUV3BWTVUxVWExTkpSSFY1UlZReGFFTkJkVU4yYXpOUGJtOW5iRE00WTAxcFNtNTRYMnByVEZsVFRuZFhUVWxCYm5Sc1RFVnJZMHRHYlRFMVRGZDBiR1ZUTVdoak0wNXNZMjVTY0dJeU5VNWFXRkp2WWpKUlVVRnJiM0pEWjJSR1drUkpNVTVVUlRWRmFVTmFRVkpKYmxOa1VrcEVTeTFGVEhKeFYyOWtNWGRPVDBob2RYUjNXbEpFUzBvdGJ6VkJaa2h5WW5KU1NUZERaMlIwV1ZoT01GcFlTWGRGUVVaTFRHZHZTbU15Vm1walJFa3hUbTF6ZUVWcFJVUkhNVzF6ZFdOelZYTmtVVTh0Ymxsd2NsRllORzlCV0ZwSVZ6SmpPRkJTVjNwU1pGTjBhell3T0RWSllWTkJiMDlaVjJSc1ltNVJkRmx0Um5wYVV6RXhZMjEzVTBWRmVIQmliWFJzV2taS2JHTXlPVEZqYlU1c1ZtcEZZVXBIYURCa1NFRTJUSGs0ZUU5VVNYVk5WRmswVEdwRmRVOUVXVFpQUkVGM1RVTTVhbUpIT1RGYVF6Rm9XakpXZFdSQklpd2lkSGx3WlNJNklsQnliMlpwYkdVaWZTd2lZM0psWkdWdWRHbGhiRk4wWVhSMWN5STZleUp6ZEdGMGRYTlFkWEp3YjNObElqb2lVbVYyYjJOaGRHbHZiaUlzSW5OMFlYUjFjMHhwYzNSSmJtUmxlQ0k2TVN3aWFXUWlPaUpvZEhSd09sd3ZYQzh4T1RJdU1UWTRMakV1T0RZNk9EQXdNRnd2WTJ4dmRXUXRZV2RsYm5SY0wyTnlaV1JsYm5ScFlXd3RjM1JoZEhWelhDODBaRGN4TmpZM01pMDFNekpqTFRRM056Y3RZVEJoTmkxbU56azJNR1F3TlRKbFpHVWpNU0lzSW5SNWNHVWlPaUpUZEdGMGRYTk1hWE4wTWpBeU1VVnVkSEo1SWl3aWMzUmhkSFZ6VEdsemRFTnlaV1JsYm5ScFlXd2lPaUpvZEhSd09sd3ZYQzh4T1RJdU1UWTRMakV1T0RZNk9EQXdNRnd2WTJ4dmRXUXRZV2RsYm5SY0wyTnlaV1JsYm5ScFlXd3RjM1JoZEhWelhDODBaRGN4TmpZM01pMDFNekpqTFRRM056Y3RZVEJoTmkxbU56azJNR1F3TlRKbFpHVWlmWDE5LmpiRE02NHQ1N3JoTXktNEt5ZnlsR3FCZGdzMUtJbXZpa0QzblFuVFRPbF9YcXV6UThTWGpZVEYyTWREbXJGRzAtSUk5NGo4ZmlUS0xYcWRpQ1NXNEN3Il19LCJub25jZSI6IjExYzkxNDkzLTAxYjMtNGM0ZC1hYzM2LWIzMzZiYWI1YmRkZiJ9.d5bzOLV-kQZvCceoFppqlPRG7aK3mo9sZVCj5_sPqIMvOqzAbxTOPyfI459GFKpeF8ApsNwJyx9jED_cRaqqiQ"
  )
  val domain = Some("https://prism-verifier.com")
  val challenge = Some("11c91493-01b3-4c4d-ac36-b336bab5bddf")
  val schemaIdAndTrustedIssuers = Seq(
    CredentialSchemaAndTrustedIssuersConstraint(
      schemaId = "http://192.168.1.86:8000/cloud-agent/schema-registry/schemas/633a68f7-4e7b-3073-a9a0-8ae964da5967",
      trustedIssuers = Some(
        Seq(
          "did:prism:0fee41fdd6807763ccc539c29841a418c76a89b3f8cd6da89de2a3d1a04571f5:CpsCCpgCEkYKFW15LWtleS1hdXRoZW50aWNhdGlvbhAESisKB0VkMjU1MTkSIDuyET1hCAuCvk3Onogl38cMiJnx_jkLYSNwWMIAntlLEkcKFm15LWtleS1hc3NlcnRpb25NZXRob2QQAkorCgdFZDI1NTE5EiCZARInSdRJDK-ELrqWod1wNOHhutwZRDKJ-o5AfHrbrRI7CgdtYXN0ZXIwEAFKLgoJc2VjcDI1NmsxEiEDG1msucsUsdQO-nYprQX4oAXZHW2c8PRWzRdStk6085IaSAoOYWdlbnQtYmFzZS11cmwSEExpbmtlZFJlc291cmNlVjEaJGh0dHA6Ly8xOTIuMTY4LjEuODY6ODAwMC9jbG91ZC1hZ2VudA"
        )
      )
    )
  )
  override def spec = suite("JWTVerificationSpec")(
    test("validate true when issuer is trusted") {
      val validation = JwtPresentation.validatePresentation(
        jwt,
        domain,
        challenge,
        schemaIdAndTrustedIssuers
      )
      assertTrue(validation.fold(_ => false, _ => true))
    },
    test("fail when issuer is not trusted") {
      val trustedIssuer = "did:prism:issuer"
      val schemaIdAndTrustedIssuers = Seq(
        CredentialSchemaAndTrustedIssuersConstraint(
          schemaId =
            "http://192.168.1.86:8000/cloud-agent/schema-registry/schemas/633a68f7-4e7b-3073-a9a0-8ae964da5967",
          trustedIssuers = Some(
            Seq(
              trustedIssuer
            )
          )
        )
      )
      val validation = JwtPresentation.validatePresentation(
        jwt,
        domain,
        challenge,
        schemaIdAndTrustedIssuers
      )
      assertTrue(validation.fold(_ => true, _ => false))
      assertTrue(validation.fold(chunk => chunk.mkString.contains(trustedIssuer), _ => false))
    },
    test("fail when schema ID doesn't match") {
      val expectedSchemaId = "http://192.168.1.86:8000/cloud-agent/schema-registry/schemas/schemaId"
      val schemaIdAndTrustedIssuers = Seq(
        CredentialSchemaAndTrustedIssuersConstraint(
          schemaId = expectedSchemaId,
          trustedIssuers = Some(
            Seq(
              "did:prism:0fee41fdd6807763ccc539c29841a418c76a89b3f8cd6da89de2a3d1a04571f5:CpsCCpgCEkYKFW15LWtleS1hdXRoZW50aWNhdGlvbhAESisKB0VkMjU1MTkSIDuyET1hCAuCvk3Onogl38cMiJnx_jkLYSNwWMIAntlLEkcKFm15LWtleS1hc3NlcnRpb25NZXRob2QQAkorCgdFZDI1NTE5EiCZARInSdRJDK-ELrqWod1wNOHhutwZRDKJ-o5AfHrbrRI7CgdtYXN0ZXIwEAFKLgoJc2VjcDI1NmsxEiEDG1msucsUsdQO-nYprQX4oAXZHW2c8PRWzRdStk6085IaSAoOYWdlbnQtYmFzZS11cmwSEExpbmtlZFJlc291cmNlVjEaJGh0dHA6Ly8xOTIuMTY4LjEuODY6ODAwMC9jbG91ZC1hZ2VudA"
            )
          )
        )
      )
      val validation = JwtPresentation.validatePresentation(
        jwt,
        domain,
        challenge,
        schemaIdAndTrustedIssuers
      )
      assertTrue(validation.fold(_ => true, _ => false))
      assertTrue(validation.fold(chunk => chunk.mkString.contains(expectedSchemaId), _ => false))
    },
    test("fail when domain validation fails") {
      val domain = Some("domain")
      val validation = JwtPresentation.validatePresentation(
        jwt,
        domain,
        challenge,
        schemaIdAndTrustedIssuers
      )
      assertTrue(validation.fold(_ => true, _ => false))
      assertTrue(validation.fold(chunk => chunk.mkString.contains("domain/Audience dont match"), _ => false))
    },
    test("fail when challenge validation fails") {
      val challenge = Some("challenge")
      val validation = JwtPresentation.validatePresentation(
        jwt,
        domain,
        challenge,
        schemaIdAndTrustedIssuers
      )
      assertTrue(validation.fold(_ => true, _ => false))
      assertTrue(validation.fold(chunk => chunk.mkString.contains("Challenge/Nonce dont match"), _ => false))
    }
  )

}
