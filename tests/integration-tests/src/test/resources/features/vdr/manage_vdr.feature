@vdr_memory_and_db
Feature: Manage a VDR entry

  Scenario: Successfully create and resolve VDR entry using memory driver
    When Issuer creates a VDR entry with value 112233 using memory driver
    Then Issuer uses the VDR URL to locate the data with value 112233

  Scenario: Successfully create and resolve VDR entry using database driver
    When Issuer creates a VDR entry with value 112233 using database driver
    Then Issuer uses the VDR URL to locate the data with value 112233

  Scenario: Successfully share resolvable VDR URL for other party using memory driver
    Given Issuer has a VDR entry with value 112233 using memory driver
    When Issuer shares the VDR URL with Holder
    Then Holder uses the VDR URL to locate the data with value 112233

  Scenario: Successfully share resolvable VDR URL for other party using database driver
    Given Issuer has a VDR entry with value 112233 using database driver
    When Issuer shares the VDR URL with Holder
    Then Holder uses the VDR URL to locate the data with value 112233

  Scenario: Successfully update VDR entry using memory driver
    Given Issuer has a VDR entry with value 112233 using memory driver
    When Issuer updates the VDR entry with value 445566
    Then Issuer uses the VDR URL to locate the data with value 445566

  Scenario: Successfully update VDR entry using database driver
    Given Issuer has a VDR entry with value 112233 using database driver
    When Issuer updates the VDR entry with value 445566
    Then Issuer uses the VDR URL to locate the data with value 445566

  Scenario: Successfully deactivate VDR entry using memory driver
    Given Issuer has a VDR entry with value 112233 using memory driver
    When Issuer deletes the VDR entry
    Then Issuer could not resolve the VDR URL

  Scenario: Successfully deactivate VDR entry using database driver
    Given Issuer has a VDR entry with value 112233 using database driver
    When Issuer deletes the VDR entry
    Then Issuer could not resolve the VDR URL
