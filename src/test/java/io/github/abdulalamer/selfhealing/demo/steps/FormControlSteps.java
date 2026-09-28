package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.abdulalamer.selfhealing.demo.pages.CheckboxesPage;
import io.github.abdulalamer.selfhealing.demo.pages.DropdownPage;

/** Dropdown and checkbox behaviour. */
public class FormControlSteps extends AbstractSteps {

  @When("I choose {string} from the dropdown")
  public void iChooseFromTheDropdown(String option) {
    currentPage(DropdownPage.class).select(option);
  }

  @When("I choose the first selectable option")
  public void iChooseTheFirstSelectableOption() {
    currentPage(DropdownPage.class).selectFirstSelectableOption();
  }

  @Then("the dropdown shows {string}")
  public void theDropdownShows(String expected) {
    assertThat(currentPage(DropdownPage.class).getSelectedOption())
        .as("the selected option")
        .isEqualTo(expected);
  }

  @Then("the dropdown offers {string}")
  public void theDropdownOffers(String option) {
    assertThat(currentPage(DropdownPage.class).getOptions())
        .as("the available options")
        .contains(option);
  }

  @When("I check checkbox {int}")
  public void iCheckCheckbox(int position) {
    currentPage(CheckboxesPage.class).check(position - 1);
  }

  @When("I uncheck checkbox {int}")
  public void iUncheckCheckbox(int position) {
    currentPage(CheckboxesPage.class).uncheck(position - 1);
  }

  @Then("checkbox {int} is checked")
  public void checkboxIsChecked(int position) {
    assertThat(currentPage(CheckboxesPage.class).isChecked(position - 1))
        .as("checkbox %d to be ticked", position)
        .isTrue();
  }

  @Then("checkbox {int} is unchecked")
  public void checkboxIsUnchecked(int position) {
    assertThat(currentPage(CheckboxesPage.class).isChecked(position - 1))
        .as("checkbox %d to be unticked", position)
        .isFalse();
  }

  @Then("there are {int} checkboxes")
  public void thereAreCheckboxes(int expected) {
    assertThat(currentPage(CheckboxesPage.class).getCheckboxCount())
        .as("number of checkboxes")
        .isEqualTo(expected);
  }
}
