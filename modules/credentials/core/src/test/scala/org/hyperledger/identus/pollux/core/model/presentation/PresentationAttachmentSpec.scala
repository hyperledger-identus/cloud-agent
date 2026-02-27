package org.hyperledger.identus.pollux.core.model.presentation

import munit.*
import org.hyperledger.identus.pollux.prex.*
import zio.json.{DecoderOps, EncoderOps}
import zio.json.ast.Json

import scala.language.implicitConversions

class PresentationAttachmentSpec extends ZSuite {

  test("Verifier Request Presentation Attachment") {
    val expectedConstraintJson = s"""
        {
          "fields": [
            {
              "path": [
                "credentialSubject.dateOfBirth",
                "credentialSubject.dob",
                "vc.credentialSubject.dateOfBirth",
                "vc.credentialSubject.dob"
              ]
            }
          ]
        }
    """.stripMargin.fromJson[Json]
    val field = Field(
      None,
      path = Seq(
        "credentialSubject.dateOfBirth",
        "credentialSubject.dob",
        "vc.credentialSubject.dateOfBirth",
        "vc.credentialSubject.dob"
      )
    )
    val constraints = Constraints(fields = Some(Seq(field)))
    val result = constraints.toJsonAST
    assertEquals(result, expectedConstraintJson)

    val expectedInputDescriptorJson = s"""
      {
        "id": "wa_driver_license",
        "name": "Washington State Business License",
        "purpose": "We can only allow licensed Washington State business representatives into the WA Business Conference",
        "constraints": {
          "fields": [
            {
              "path": [
                "credentialSubject.dateOfBirth",
                "credentialSubject.dob",
                "vc.credentialSubject.dateOfBirth",
                "vc.credentialSubject.dob"
              ]
            }
          ]
        }
      }
      """.stripMargin.fromJson[Json]

    val inputDescriptor = InputDescriptor(
      id = "wa_driver_license",
      name = Some("Washington State Business License"),
      purpose =
        Some("We can only allow licensed Washington State business representatives into the WA Business Conference"),
      constraints = constraints
    )
    val resultInputDescriptor = inputDescriptor.toJsonAST
    assertEquals(resultInputDescriptor, expectedInputDescriptorJson)

    val expectedPresentationDefinitionJson = s"""
      {
        "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
        "input_descriptors": [
          {
            "id": "wa_driver_license",
            "name": "Washington State Business License",
            "purpose": "We can only allow licensed Washington State business representatives into the WA Business Conference",
            "constraints": {
              "fields": [
                {
                  "path": [
                    "credentialSubject.dateOfBirth",
                    "credentialSubject.dob",
                    "vc.credentialSubject.dateOfBirth",
                    "vc.credentialSubject.dob"
                  ]
                }
              ]
            }
          }
        ]
      }
      """.stripMargin.fromJson[Json]

    val presentationDefinition =
      PresentationDefinition(id = "32f54163-7166-48f1-93d8-ff217bdb0653", input_descriptors = Seq(inputDescriptor))
    val resultPresentationDefinition = presentationDefinition.toJsonAST
    assertEquals(resultPresentationDefinition, expectedPresentationDefinitionJson)

    val expectedPresentationAttachmentJson = s"""
      {
        "options": {
          "challenge": "23516943-1d79-4ebd-8981-623f036365ef",
          "domain": "us.gov/DriversLicense"
        },
        "presentation_definition": {
          "id": "32f54163-7166-48f1-93d8-ff217bdb0653",
          "input_descriptors": [
            {
              "id": "wa_driver_license",
              "name": "Washington State Business License",
              "purpose": "We can only allow licensed Washington State business representatives into the WA Business Conference",
              "constraints": {
                "fields": [
                  {
                    "path": [
                      "credentialSubject.dateOfBirth",
                      "credentialSubject.dob",
                      "vc.credentialSubject.dateOfBirth",
                      "vc.credentialSubject.dob"
                    ]
                  }
                ]
              }
            }
          ]
        }
      }
      """.stripMargin.fromJson[Json]
    val options = Options(challenge = "23516943-1d79-4ebd-8981-623f036365ef", domain = "us.gov/DriversLicense")
    val presentationAttachment =
      PresentationAttachment(presentation_definition = presentationDefinition, options = Some(options))
    val resultPresentationAttachment = presentationAttachment.toJsonAST
    println(resultPresentationAttachment)
    assertEquals(resultPresentationAttachment, expectedPresentationAttachmentJson)

  }
}
