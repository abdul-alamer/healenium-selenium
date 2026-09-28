package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.github.abdulalamer.selfhealing.demo.pages.AddRemoveElementsPage;
import io.github.abdulalamer.selfhealing.demo.pages.CheckboxesPage;
import io.github.abdulalamer.selfhealing.demo.pages.DemoHomePage;
import io.github.abdulalamer.selfhealing.demo.pages.DemoPage;
import io.github.abdulalamer.selfhealing.demo.pages.DropdownPage;
import io.github.abdulalamer.selfhealing.demo.pages.DynamicControlsPage;
import io.github.abdulalamer.selfhealing.demo.pages.FramesPage;
import io.github.abdulalamer.selfhealing.demo.pages.TablesPage;
import java.util.List;
import java.util.Locale;

/**
 * Navigation and the assertions that are the same on every page.
 *
 * <p>One parameterised step opens any example, so adding a page to the suite means adding a page
 * object and one line here - not a new step per page.
 */
public class NavigationSteps extends AbstractSteps {

  @Given("I open the demo home page")
  public void iOpenTheDemoHomePage() {
    DemoHomePage page = new DemoHomePage(driver());
    page.open();
    context().put(CURRENT_PAGE, page);
  }

  /**
   * Opens one of the examples.
   *
   * @param name the example name as used in the feature files
   */
  @Given("I open the {string} example")
  public void iOpenTheExample(String name) {
    DemoPage page = pageFor(name);
    page.open();
    context().put(CURRENT_PAGE, page);
  }

  @Then("the page heading contains {string}")
  public void thePageHeadingContains(String fragment) {
    DemoPage page = currentPage(DemoPage.class);
    assertThat(page.getHeading())
        .as("heading of %s", page.getClass().getSimpleName())
        .contains(fragment);
  }

  @Then("the page heading is {string}")
  public void thePageHeadingIs(String expected) {
    DemoPage page = currentPage(DemoPage.class);
    assertThat(page.getHeading())
        .as("heading of %s", page.getClass().getSimpleName())
        .isEqualTo(expected);
  }

  @Then("the example index lists {string}")
  public void theExampleIndexLists(String name) {
    DemoHomePage home = currentPage(DemoHomePage.class);
    assertThat(home.hasExample(name))
        .as("'%s' to be listed among %s", name, home.getExampleNames())
        .isTrue();
  }

  @Then("the example index lists at least {int} examples")
  public void theExampleIndexListsAtLeast(int minimum) {
    List<String> names = currentPage(DemoHomePage.class).getExampleNames();
    assertThat(names).as("examples listed on the index").hasSizeGreaterThanOrEqualTo(minimum);
  }

  private DemoPage pageFor(String name) {
    return switch (name.trim().toLowerCase(Locale.ROOT)) {
      case "dropdown" -> new DropdownPage(driver());
      case "checkboxes" -> new CheckboxesPage(driver());
      case "data tables" -> new TablesPage(driver());
      case "dynamic controls" -> new DynamicControlsPage(driver());
      case "add/remove elements" -> new AddRemoveElementsPage(driver());
      case "iframe" -> new FramesPage(driver());
      default -> throw new IllegalArgumentException("No page object for the '" + name
          + "' example. Known examples: Dropdown, Checkboxes, Data Tables, Dynamic Controls, "
          + "Add/Remove Elements, iFrame.");
    };
  }
}
