@smoke
Feature: The demo application is reachable and its basic controls behave

  A short suite that proves the framework is wired up: configuration resolves, a browser starts,
  page objects find their elements and typed components drive the controls. Run it first after
  cloning, and run it on every pull request.

  Scenario: The example index lists the pages this suite exercises
    Given I open the demo home page
    Then the page heading is "Welcome to the-internet"
    And the example index lists at least 30 examples
    And the example index lists "Dynamic Controls"
    And the example index lists "Checkboxes"

  Scenario: Choosing an option from a native dropdown
    Given I open the "Dropdown" example
    Then the page heading contains "Dropdown"
    And the dropdown offers "Option 2"
    When I choose "Option 2" from the dropdown
    Then the dropdown shows "Option 2"

  Scenario: The first selectable option is chosen, not the disabled placeholder
    Given I open the "Dropdown" example
    When I choose the first selectable option
    Then the dropdown shows "Option 1"

  Scenario: Ticking a box states the outcome, so repeating it changes nothing
    Given I open the "Checkboxes" example
    Then there are 2 checkboxes
    And checkbox 1 is unchecked
    And checkbox 2 is checked
    When I check checkbox 1
    And I check checkbox 1
    Then checkbox 1 is checked
    When I uncheck checkbox 2
    Then checkbox 2 is unchecked
