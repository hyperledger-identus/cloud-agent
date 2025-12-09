@DLT @did @create
Feature: Create and publish DID

  Scenario Outline: Create PRISM DID with <curve> for <purpose>
    When Issuer creates PRISM DID with <curve> key having <purpose> purpose
    Then He sees PRISM DID was created successfully
    And He sees PRISM DID data was stored correctly with <curve> and <purpose>
    Examples:
      | curve     | purpose         |
      | secp256k1 | authentication  |
      | secp256k1 | assertionMethod |
      | Ed25519   | authentication  |
      | Ed25519   | assertionMethod |
      | X25519    | keyAgreement    |

  Scenario Outline: Create PRISM DID with <curve> for <purpose> should not work
    When Issuer creates PRISM DID with <curve> key having <purpose> purpose
    Then He sees PRISM DID was not successfully created
    Examples:
      | curve   | purpose         |
      | Ed25519 | keyAgreement    |
      | X25519  | authentication  |
      | X25519  | assertionMethod |

  Scenario: Create PRISM DID with internal VDR key
    When Issuer creates PRISM DID with internal VDR key
    Then He sees PRISM DID was created successfully
    And He sees PRISM DID data was stored correctly with secp256k1 and authentication

  Scenario: Successfully publish DID to ledger
    Given Issuer creates empty unpublished DID
    When He publishes DID to ledger
    Then He resolves DID document corresponds to W3C standard
