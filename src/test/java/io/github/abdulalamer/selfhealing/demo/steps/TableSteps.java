package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.github.abdulalamer.selfhealing.demo.pages.TablesPage;

/** Reading a table by column header rather than by cell position. */
public class TableSteps extends AbstractSteps {

  @Then("the table has {int} rows")
  public void theTableHasRows(int expected) {
    assertThat(currentPage(TablesPage.class).getRowCount())
        .as("number of body rows")
        .isEqualTo(expected);
  }

  @Then("the table columns include {string}")
  public void theTableColumnsInclude(String header) {
    assertThat(currentPage(TablesPage.class).getColumns())
        .as("the table's column headers")
        .contains(header);
  }

  @Then("the {string} column contains {string}")
  public void theColumnContains(String header, String value) {
    TablesPage page = currentPage(TablesPage.class);
    assertThat(page.columnContains(header, value))
        .as("column '%s' to contain '%s'; it holds %s", header, value,
            page.getColumnValues(header))
        .isTrue();
  }

  @Then("the {string} column does not contain {string}")
  public void theColumnDoesNotContain(String header, String value) {
    assertThat(currentPage(TablesPage.class).columnContains(header, value))
        .as("column '%s' not to contain '%s'", header, value)
        .isFalse();
  }

  @Then("row {int} of the table has {string} = {string}")
  public void rowOfTheTableHas(int position, String header, String expected) {
    assertThat(currentPage(TablesPage.class).getCell(position - 1, header))
        .as("row %d, column '%s'", position, header)
        .isEqualTo(expected);
  }
}
