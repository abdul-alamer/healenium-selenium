@self-healing
Feature: A locator that stops matching is recovered instead of failing the run

  This is the scenario the framework exists for.

  A page object holds the locator "#checkbox-example button". It is correct today. Halfway through
  the scenario the page is refactored underneath it - the container is renamed, the button itself
  untouched - and the locator matches nothing. Without self-healing that is a red build and a
  morning spent on a locator. With it, Healenium recognises the button from the node tree it stored
  the first time the locator worked, the step passes, and the healed locator is reported as debt to
  fix rather than quietly forgotten.

  What each step does:
    1. Resolving the control trains Healenium: the element's node tree is stored against the
       locator. Healing can only repair a locator that has succeeded at least once, which is why
       this step exists and why a locator that was wrong from the start cannot be healed.
    2. Renaming the container stands in for a front-end change. Everything about the button - tag,
       text, position, ancestors - is the same; only the path to it is different.
    3. Resolving it again fails in Selenium, at which point Healenium sends the stored tree to the
       selector-imitator, which scores candidate nodes in the current DOM. The button scores far
       above score-cap because only one attribute of one ancestor changed, and it is returned.

  Requires the Healenium backend: docker compose -f docker/docker-compose.yml up -d
  To watch it fail instead, which is just as instructive:
      ./gradlew test -Ptags="@self-healing" -PhealEnabled=false

  Scenario: The suite survives a front-end refactor that breaks a locator
    Given I open the "Dynamic Controls" example
    And the swap control has been located once, so its node tree is stored
    When the page is refactored so the learned locator no longer matches
    Then the swap control is found again and still reads the same label
    And the healing report records at least one recovered locator
