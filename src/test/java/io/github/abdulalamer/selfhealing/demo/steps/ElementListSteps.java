package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.abdulalamer.selfhealing.demo.pages.AddRemoveElementsPage;

/**
 * Adding and removing elements, which exercises a {@code List} of typed components against a DOM
 * that changes underneath it.
 */
public class ElementListSteps extends AbstractSteps {

  @When("I add {int} elements")
  public void iAddElements(int howMany) {
    currentPage(AddRemoveElementsPage.class).addElements(howMany);
  }

  @When("I remove the first element")
  public void iRemoveTheFirstElement() {
    currentPage(AddRemoveElementsPage.class).removeFirst();
  }

  @Then("there are {int} removable elements")
  public void thereAreRemovableElements(int expected) {
    assertThat(currentPage(AddRemoveElementsPage.class).count())
        .as("number of removable elements")
        .isEqualTo(expected);
  }
}
