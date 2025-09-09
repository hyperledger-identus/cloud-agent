@jwt @proof
Feature: Present Proof Protocol

  Scenario: Holder presents jwt credential proof to verifier
    Given Verifier and Holder have an existing connection
    And Holder has a jwt issued credential from Issuer
    When Verifier sends a request for jwt proof presentation to Holder
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Verifier has the proof verified

  Scenario: Holder presents jwt proof to verifier which is the issuer itself
    Given Issuer and Holder have an existing connection
    And Holder has a jwt issued credential from Issuer
    When Issuer sends a request for jwt proof presentation to Holder
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Issuer has the proof verified

  Scenario: Verifier rejects holder proof
    Given Verifier and Holder have an existing connection
    And Holder has a jwt issued credential from Issuer
    When Verifier sends a request for jwt proof presentation to Holder
    And Holder receives the presentation proof request
    And Holder rejects the proof
    Then Holder sees the proof is rejected

  Scenario Outline: Verifying jwt credential using <assertionMethod> assertion
    Given Issuer and Holder have an existing connection
    And Verifier and Holder have an existing connection
    And Holder creates unpublished DID for 'JWT'
    When Issuer prepares a custom PRISM DID
    And Issuer adds a '<assertionMethod>' key for 'assertionMethod' purpose with '<assertionName>' name to the custom PRISM DID
    And Issuer creates the custom PRISM DID
    And Issuer publishes DID to ledger
    And Issuer has a published 'STUDENT_SCHEMA' schema
    When Issuer offers a jwt credential to Holder with 'short' form DID using issuingKid '<assertionName>' and STUDENT_SCHEMA schema
    And Holder receives the credential offer
    And Holder accepts jwt credential offer using 'auth-1' key id
    And Issuer issues the credential
    Then Holder receives the issued credential
    When Verifier sends a request for jwt proof presentation to Holder
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Verifier has the proof verified
    Examples:
      | assertionMethod | assertionName |
      | secp256k1       | iss-secp |
      | Ed25519         | iss-ed   |

  Scenario: Connectionless Verification Holder presents jwt credential proof to verifier
    Given Holder has a jwt issued credential from Issuer
    When Verifier creates a OOB Invitation request for JWT proof presentation
    And Holder accepts the OOB invitation request for JWT proof presentation from Verifier
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Verifier has the proof verified

  Scenario: Verifier request for jwt proof presentation to Holder from trusted issuer using specified schema
    Given Verifier and Holder have an existing connection
    And Holder has a jwt issued credential with 'STUDENT_SCHEMA' schema from Issuer
    When Verifier sends a request for jwt proof from trustedIssuer Issuer using STUDENT_SCHEMA schema presentation to Holder
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Verifier has the proof verified

  Scenario: Verifier request for jwt proof presentation to Holder from trusted issuer using specified schema
    Given Verifier and Holder have an existing connection
    And Holder has a jwt issued credential with 'STUDENT_SCHEMA' schema from Issuer
    And Holder has a jwt issued credential with 'EMPLOYEE_SCHEMA' schema from Issuer
    When Verifier sends a request for jwt proof from trustedIssuer Issuer using STUDENT_SCHEMA schema presentation to Holder
    And Holder receives the presentation proof request
    And Holder makes the jwt presentation of the proof
    Then Verifier sees the proof returned verification failed
