@jwt @issuance
Feature: Issue JWT credential

  Scenario Outline: Issuing jwt credential using <assertionMethod> assertion
    Given Issuer and Holder have an existing connection
    And Holder creates unpublished DID for 'JWT'
    When Issuer prepares a custom PRISM DID
    And Issuer has a published 'STUDENT_SCHEMA' schema
    And Issuer adds a '<assertionMethod>' key for 'assertionMethod' purpose with '<assertionName>' name to the custom PRISM DID
    And Issuer creates the custom PRISM DID
    And Issuer publishes DID to ledger
    When Issuer offers a jwt credential to Holder with 'short' form DID using issuingKid '<assertionName>' and STUDENT_SCHEMA schema
    And Holder receives the credential offer
    And Holder accepts jwt credential offer using 'auth-1' key id
    And Issuer issues the credential
    Then Holder receives the issued credential
    Examples:
      | assertionMethod | assertionName |
      | secp256k1       | assert-1      |
      | ed25519         | assert-1      |

  Scenario: Issuing jwt credential with published PRISM DID and student schema
    Given Issuer and Holder have an existing connection
    And Issuer has a published DID for 'JWT'
    And Issuer has a published 'STUDENT_SCHEMA' schema
    And Holder has an unpublished DID for 'JWT'
    When Issuer offers a jwt credential to Holder with 'short' form using 'STUDENT_SCHEMA' schema
    And Holder receives the credential offer
    And Holder accepts jwt credential offer using 'auth-1' key id
    And Issuer issues the credential
    Then Holder receives the issued credential

  Scenario: Issuing jwt credential with wrong claim structure for schema
    Given Issuer and Holder have an existing connection
    And Issuer has a published DID for 'JWT'
    And Issuer has a published 'STUDENT_SCHEMA' schema
    And Holder has an unpublished DID for 'JWT'
    When Issuer offers a jwt credential to Holder with 'short' form DID with wrong claims structure using 'STUDENT_SCHEMA' schema
    Then Issuer should see that credential issuance has failed

  Scenario: Issuing jwt credential with unpublished PRISM DID
    Given Issuer and Holder have an existing connection
    And Issuer has an unpublished DID for 'JWT'
    And Issuer has a published 'STUDENT_SCHEMA' schema
    And Holder has an unpublished DID for 'JWT'
    And Issuer offers a jwt credential to Holder with 'long' form DID
    And Holder receives the credential offer
    And Holder accepts jwt credential offer using 'auth-1' key id
    And Issuer issues the credential
    Then Holder receives the issued credential

  Scenario: Connectionless issuance of JWT credential using OOB invitation
    Given Issuer has a published DID for 'JWT'
    And Issuer has a published 'STUDENT_SCHEMA' schema
    And Holder has an unpublished DID for 'JWT'
    When Issuer creates a 'JWT' credential offer invitation with 'short' form DID and STUDENT_SCHEMA schema
    And Holder accepts the credential offer invitation from Issuer
    And Holder accepts jwt credential offer using 'auth-1' key id
    And Issuer issues the credential
    Then Holder receives the issued credential

  Scenario Outline: Issuing a credential with <issue> issuer should return <httpStatus>
    Given Issuer and Holder have an existing connection
    And Issuer has a published DID for 'JWT'
    And Holder has an unpublished DID for 'JWT'
    When Issuer offers a jwt credential to Holder with <issue> issue
    Then Issuer should see the status code was <httpStatus>
    Examples:
      | issue                 | httpStatus |
      | UNKNOWN_CONNECTION_ID | 404        |
