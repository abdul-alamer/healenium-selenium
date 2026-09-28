package io.github.abdulalamer.selfhealing.elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * An HTML table addressed by column header rather than by cell position.
 *
 * <p>Column-addressed reads are the single highest-value thing a table component can offer. A test
 * that says {@code table.getColumnValues("Due")} keeps working when somebody inserts a column;
 * {@code td[4]} does not, and nothing about the resulting failure tells you why.
 *
 * <pre>{@code
 * @FindBy(id = "orders")
 * private DataTable orders;
 *
 * // ...
 * assertThat(orders.getColumnValues("Status")).containsOnly("Shipped");
 * Map<String, String> first = orders.getRow(0);
 * }</pre>
 *
 * <p>Both nested fields are typed-component lists, so this class is also the worked example of what
 * {@link ComponentFieldDecorator} makes possible: the rows re-resolve on every access, which is what
 * makes reading a table that re-renders between two calls safe.
 *
 * <p><b>Scope.</b> A rectangular table is assumed; {@code colspan} and {@code rowspan} are not
 * interpreted, and a table that uses them needs a subclass that overrides {@link #cellsOf}. Header
 * matching is case-insensitive and whitespace-normalised, because visual headers pick up stray
 * whitespace from the markup far more often than anyone expects.
 */
public class DataTable extends BaseComponent {

  /** Header cells. A {@code <thead>} is used when present; see {@link #getHeaders()}. */
  @FindBy(css = "thead th")
  private List<BaseComponent> headerCells;

  /** Body rows. */
  @FindBy(css = "tbody tr")
  private List<BaseComponent> bodyRows;

  private static final By CELL = By.cssSelector("td, th");
  private static final By FIRST_ROW_HEADER = By.cssSelector("tr:first-child th, tr:first-child td");

  public DataTable(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public DataTable(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public DataTable(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public DataTable(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /**
   * The column headers, in document order.
   *
   * <p>Falls back to the first row's cells when the table has no {@code <thead>}, which is common in
   * hand-written markup and in grids that render their header as an ordinary row.
   */
  public List<String> getHeaders() {
    List<String> fromThead = textsOf(headerCells);
    if (!fromThead.isEmpty()) {
      return fromThead;
    }
    List<String> fromFirstRow = new ArrayList<>();
    findElements(FIRST_ROW_HEADER).forEach(cell -> fromFirstRow.add(normalise(getInnerText(cell))));
    return fromFirstRow;
  }

  /** How many body rows the table currently has. */
  public int getRowCount() {
    return bodyRows.size();
  }

  /** Whether the table has any body rows. */
  public boolean isEmpty() {
    return getRowCount() == 0;
  }

  /**
   * The zero-based position of a column.
   *
   * @param header the visible header text, matched case-insensitively after normalising whitespace
   * @return the column index
   * @throws IllegalArgumentException when no column matches, listing the headers that do exist
   */
  public int getColumnIndex(String header) {
    List<String> headers = getHeaders();
    String wanted = normalise(header).toLowerCase(Locale.ROOT);
    for (int index = 0; index < headers.size(); index++) {
      if (headers.get(index).toLowerCase(Locale.ROOT).equals(wanted)) {
        return index;
      }
    }
    throw new IllegalArgumentException(
        "No column named '" + header + "' in " + describe() + ". Columns are: " + headers);
  }

  /** Whether a column exists. */
  public boolean hasColumn(String header) {
    String wanted = normalise(header).toLowerCase(Locale.ROOT);
    return getHeaders().stream()
        .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(wanted));
  }

  /**
   * Every value in a column, top to bottom.
   *
   * @param header the visible header text
   * @return one entry per body row
   */
  public List<String> getColumnValues(String header) {
    int column = getColumnIndex(header);
    List<String> values = new ArrayList<>();
    for (BaseComponent row : bodyRows) {
      List<WebElement> cells = cellsOf(row);
      values.add(column < cells.size() ? normalise(getInnerText(cells.get(column))) : "");
    }
    return values;
  }

  /**
   * One cell.
   *
   * @param rowIndex zero-based body row
   * @param header   the visible header text
   * @return the cell's rendered text
   */
  public String getCellValue(int rowIndex, String header) {
    List<WebElement> cells = cellsOf(requireRow(rowIndex));
    int column = getColumnIndex(header);
    return column < cells.size() ? normalise(getInnerText(cells.get(column))) : "";
  }

  /**
   * One row as header-to-value pairs, in column order.
   *
   * @param rowIndex zero-based body row
   * @return an insertion-ordered map
   */
  public Map<String, String> getRow(int rowIndex) {
    List<String> headers = getHeaders();
    List<WebElement> cells = cellsOf(requireRow(rowIndex));
    Map<String, String> row = new LinkedHashMap<>();
    for (int index = 0; index < headers.size(); index++) {
      row.put(headers.get(index),
          index < cells.size() ? normalise(getInnerText(cells.get(index))) : "");
    }
    return row;
  }

  /** Every row as header-to-value pairs. */
  public List<Map<String, String>> getRows() {
    int rows = getRowCount();
    List<Map<String, String>> all = new ArrayList<>(rows);
    for (int index = 0; index < rows; index++) {
      all.add(getRow(index));
    }
    return all;
  }

  /**
   * Whether a column contains a value, compared case-insensitively after normalising whitespace.
   *
   * @param header the visible header text
   * @param value  the value to look for
   * @return true when at least one row matches
   */
  public boolean containsValueInColumn(String header, String value) {
    String wanted = normalise(value).toLowerCase(Locale.ROOT);
    return getColumnValues(header).stream()
        .anyMatch(actual -> actual.toLowerCase(Locale.ROOT).equals(wanted));
  }

  /**
   * The first row whose column holds a value.
   *
   * @param header the visible header text
   * @param value  the value to look for
   * @return the zero-based row index, or empty when nothing matches
   */
  public OptionalInt findRowIndex(String header, String value) {
    List<String> values = getColumnValues(header);
    String wanted = normalise(value).toLowerCase(Locale.ROOT);
    for (int index = 0; index < values.size(); index++) {
      if (values.get(index).toLowerCase(Locale.ROOT).equals(wanted)) {
        return OptionalInt.of(index);
      }
    }
    return OptionalInt.empty();
  }

  /**
   * One row as a component, for clicking a link or a control inside it.
   *
   * <p>Do not hold the returned component across an interaction that re-renders the table; read what
   * you need from it, or fetch it again.
   *
   * @param rowIndex zero-based body row
   * @return the row component
   */
  public BaseComponent getRowComponent(int rowIndex) {
    return requireRow(rowIndex);
  }

  /** Waits until the table has at least one body row. */
  public void waitUntilPopulated() {
    waitUntil(() -> getRowCount() > 0, describe() + " to have at least one row");
  }

  /** The cells of a row. Override for tables that merge cells. */
  protected List<WebElement> cellsOf(BaseComponent row) {
    return row.findElements(CELL);
  }

  private BaseComponent requireRow(int rowIndex) {
    int rows = getRowCount();
    if (rowIndex < 0 || rowIndex >= rows) {
      throw new IndexOutOfBoundsException(
          "Row " + rowIndex + " does not exist; " + describe() + " has " + rows + " row(s)");
    }
    return bodyRows.get(rowIndex);
  }

  private List<String> textsOf(List<BaseComponent> cells) {
    List<String> texts = new ArrayList<>(cells.size());
    cells.forEach(cell -> texts.add(normalise(cell.getInnerText())));
    return texts;
  }

  private static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
