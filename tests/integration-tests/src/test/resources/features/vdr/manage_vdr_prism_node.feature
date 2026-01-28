@vdr @prism_node
Feature: Manage a VDR entry with prism-node driver

  Background:
    When Issuer creates PRISM DID with internal VDR key

  Scenario Outline: Successfully create and resolve VDR entry using prism-node driver
    When Issuer creates a VDR entry with value 112233 using <driver> driver
    Then Issuer uses the VDR URL to locate the data with value 112233
    Examples:
      | driver     |
      | prism-node |

  Scenario Outline: Successfully share resolvable VDR URL for other party using prism-node driver
    Given Issuer has a VDR entry with value 112233 using <driver> driver
    When Issuer shares the VDR URL with Holder
    Then Holder uses the VDR URL to locate the data with value 112233
    Examples:
      | driver     |
      | prism-node |

  Scenario Outline: Successfully update VDR entry using prism-node driver
    Given Issuer has a VDR entry with value 112233 using <driver> driver
    When Issuer updates the VDR entry with value 445566
    Then Issuer uses the VDR URL to locate the data with value 445566
    Examples:
      | driver     |
      | prism-node |

  Scenario Outline: Successfully deactivate VDR entry using prism-node driver
    Given Issuer has a VDR entry with value 112233 using <driver> driver
    When Issuer deletes the VDR entry
    Then Issuer could not resolve the VDR URL
    Examples:
      | driver     |
      | prism-node |
