package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.abdulalamer.selfhealing.demo.pages.DynamicControlsPage;

/**
 * Controls that appear, disappear and change state behind a loading indicator.
 *
 * <p>No step here sleeps. Every one of them waits for the outcome it names, which is the whole point
 * of the example.
 */
public class DynamicControlsSteps extends AbstractSteps {

  @Given("the optional checkbox is present")
  public void theOptionalCheckboxIsPresent() {
    assertThat(page().isCheckboxPresent()).as("the optional checkbox to be on the page").isTrue();
  }

  @When("I press the swap control")
  public void iPressTheSwapControl() {
    page().toggleCheckbox();
  }

  @Then("the optional checkbox is not present")
  public void theOptionalCheckboxIsNotPresent() {
    assertThat(page().isCheckboxPresent()).as("the optional checkbox to be gone").isFalse();
  }

  @Then("the optional checkbox is present again")
  public void theOptionalCheckboxIsPresentAgain() {
    assertThat(page().isCheckboxPresent()).as("the optional checkbox to be back").isTrue();
  }

  @Then("the checkbox message reads {string}")
  public void theCheckboxMessageReads(String expected) {
    assertThat(page().getCheckboxMessage()).as("the checkbox confirmation message")
        .isEqualTo(expected);
  }

  @Given("the text field is disabled")
  public void theTextFieldIsDisabled() {
    assertThat(page().isTextInputEnabled()).as("the text field to start disabled").isFalse();
  }

  @When("I press the enable control")
  public void iPressTheEnableControl() {
    page().toggleTextInput();
  }

  @Then("the text field is enabled")
  public void theTextFieldIsEnabled() {
    assertThat(page().isTextInputEnabled()).as("the text field to be enabled").isTrue();
  }

  @Then("I can type {string} into the text field")
  public void iCanTypeIntoTheTextField(String text) {
    page().typeIntoTextInput(text);
    assertThat(page().getTextInputValue()).as("what the text field holds").isEqualTo(text);
  }

  @Then("the input message reads {string}")
  public void theInputMessageReads(String expected) {
    assertThat(page().getInputMessage()).as("the input confirmation message").isEqualTo(expected);
  }

  private DynamicControlsPage page() {
    return currentPage(DynamicControlsPage.class);
  }
}
