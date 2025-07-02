@vdr
Feature: Manage a VDR entry

Scenario: Successfully create and resolve VDR entry
    When Issuer creates a VDR entry with value 112233
    Then Issuer uses the VDR URL to locate the data with value 112233

Scenario: Successfully share resolvable VDR URL for other party
    Given Issuer has a VDR entry with value 112233
    When Issuer shares the VDR URL with Holder
    Then Holder uses the VDR URL to locate the data with value 112233

Scenario: Successfully update VDR entry
    Given Issuer has a VDR entry with value 112233
    When Issuer updates the VDR entry with value 445566
    Then Issuer uses the VDR URL to locate the data with value 445566

Scenario: Successfully deactivate VDR entry
    Given Issuer has a VDR entry with value 112233
    When Issuer deletes the VDR entry
    Then Issuer could not resolve the VDR URL
