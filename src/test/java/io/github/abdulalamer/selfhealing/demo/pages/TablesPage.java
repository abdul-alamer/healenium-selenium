package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.DataTable;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The data-table example.
 *
 * <p>Every read goes through the column header, so inserting a column upstream cannot silently
 * change what a test asserts on. This is the difference the {@link DataTable} component is there to
 * make.
 */
public class TablesPage extends BasePage implements DemoPage {

  public static final String PATH = "/tables";

  @FindBy(css = "h3")
  private BaseComponent heading;

  @FindBy(id = "table1")
  private DataTable exampleTable;

  public TablesPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    exampleTable.waitUntilPopulated();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** The table's column headers. */
  public List<String> getColumns() {
    return exampleTable.getHeaders();
  }

  /** How many rows the table has. */
  public int getRowCount() {
    return exampleTable.getRowCount();
  }

  /** Every value in a column, addressed by its header. */
  public List<String> getColumnValues(String header) {
    return exampleTable.getColumnValues(header);
  }

  /** Whether a column holds a value anywhere. */
  public boolean columnContains(String header, String value) {
    return exampleTable.containsValueInColumn(header, value);
  }

  /** One row as header-to-value pairs. */
  public Map<String, String> getRow(int index) {
    return exampleTable.getRow(index);
  }

  /** The value of one cell, addressed by row index and column header. */
  public String getCell(int rowIndex, String header) {
    return exampleTable.getCellValue(rowIndex, header);
  }
}
