@vdr_neoprism
Feature: Manage a VDR entry with neoprism driver

  # Single end-to-end flow to avoid recreating the VDR key across scenarios.
  Scenario: Create, update, resolve and deactivate a VDR entry via neoprism
    Given Issuer creates PRISM DID with internal VDR key
    And Issuer publishes DID to ledger
    Then Issuer sees PRISM DID has the VDR key

    When Issuer creates a VDR entry with value 112233 using neoprism driver
    Then Issuer uses the VDR URL to locate the data with value 112233

    When Issuer updates the VDR entry with value 445566
    Then Issuer uses the VDR URL to locate the data with value 445566

    # Second update to detect cache vs. latest-head handling
    When Issuer updates the VDR entry with value 778899
    Then Issuer uses the VDR URL to locate the data with value 778899

    When Issuer deletes the VDR entry
    Then Issuer could not resolve the VDR URL
