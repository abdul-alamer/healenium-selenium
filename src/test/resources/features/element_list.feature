@components
Feature: A list of typed components re-resolves as the page changes

  The delete buttons are injected as a List of components that is rebuilt on every access. A list
  captured once would hand back a detached node as soon as the first deletion re-rendered the DOM.

  Background:
    Given I open the "Add/Remove Elements" example

  Scenario: Adding and removing elements
    Then the page heading contains "Add/Remove Elements"
    When I add 3 elements
    Then there are 3 removable elements
    When I remove the first element
    Then there are 2 removable elements
    When I remove the first element
    And I remove the first element
    Then there are 0 removable elements
