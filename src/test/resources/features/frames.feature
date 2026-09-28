@frames
Feature: Working inside an iframe without stranding the driver

  Frame switching is state held on the driver, so a scenario that switches in and then fails leaves
  every later scenario looking in the wrong document. IFrame.inFrame switches back in a finally
  block; the heading assertion at the end is there to prove it did.

  Scenario: Reading and replacing the editor content
    Given I open the "iFrame" example
    Then the editor contains "Your content goes here."
    When I replace the editor content with "Locator churn is a maintenance cost."
    Then the editor contains "Locator churn is a maintenance cost."
    And the page heading contains "TinyMCE"
