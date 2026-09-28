@waits
Feature: Waiting for outcomes instead of for the clock

  Both controls on this page hide a loading bar of a few seconds. Every step below waits for the
  state it names - a checkbox that is gone, a field that accepts input - so the suite is neither
  flaky on a slow machine nor artificially slow on a fast one.

  Background:
    Given I open the "Dynamic Controls" example

  Scenario: Removing and restoring the optional checkbox
    Given the optional checkbox is present
    When I press the swap control
    Then the optional checkbox is not present
    And the checkbox message reads "It's gone!"
    When I press the swap control
    Then the optional checkbox is present again
    And the checkbox message reads "It's back!"

  Scenario: Enabling the disabled text field
    Given the text field is disabled
    When I press the enable control
    Then the text field is enabled
    And the input message reads "It's enabled!"
    And I can type "locator churn" into the text field
