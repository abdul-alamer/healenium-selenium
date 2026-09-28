# Writing page objects

## The shape

```java
package com.example.orders.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.DataTable;
import io.github.abdulalamer.selfhealing.elements.SearchableDropdown;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

public class OrdersPage extends BasePage {

  public static final String PATH = "/orders";

  @FindBy(css = "h1")
  private BaseComponent heading;

  @FindBy(id = "orders-table")
  private DataTable orders;

  @FindBy(css = "[data-test='status-filter']")
  private SearchableDropdown statusFilter;

  public OrdersPage(WebDriver driver) {
    super(driver);
  }

  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    orders.waitUntilPopulated();
  }

  public void filterBy(String status) {
    statusFilter.selectByText(status);
    waitUntil(() -> orders.getColumnValues("Status").stream().allMatch(status::equals),
        "every visible order to have status " + status);
  }

  public List<String> visibleOrderIds() {
    return orders.getColumnValues("Order ID");
  }
}
```

Five things are doing work there:

1. **`extends BasePage`** brings the waits, retries, frames, alerts, scripts and the
   implicit-timeout guard.
2. **Typed fields.** `orders` is a `DataTable`, not a `WebElement`, so the page object never
   contains a loop over table cells.
3. **`PATH` plus `openPath`.** No base URL in the page object; the environment is configuration.
4. **`waitPageLoading()`** states what "ready" means for this page, once, so no step definition has
   to guess.
5. **`filterBy` waits for the outcome it caused**, not for a fixed number of milliseconds.

## Rules that earn their keep

**Page objects expose intent, not mechanics.** `filterBy("Shipped")`, not `clickFilter()` followed
by `selectOption()` followed by `waitForTable()`. If a step definition has to call three methods in
the right order, that order belongs in the page object.

**Page objects do not assert.** They return values; step definitions assert on them. A page object
that asserts can only be used by the one test that wants that assertion.

**`waitPageLoading()` is not optional and is not a sleep.** Express it as something the page only has
when it is genuinely usable: a heading rendered, a table populated, a spinner gone.

**Never store the base URL, credentials or environment names in a page object.** They belong in
configuration. See [CONFIGURATION.md](CONFIGURATION.md).

**Prefer a stable hook over a pretty selector.** `[data-test='status-filter']` survives a CSS
rewrite; `.MuiSelect-root.jss417` does not. Self-healing is a shock absorber, not a licence to write
brittle selectors - see [SELF_HEALING.md](SELF_HEALING.md#when-self-healing-is-the-wrong-answer).

## The component catalogue

Every component is injectable by `@FindBy`, scopes its own fields to its element, and recovers from
staleness by retaining its locator.

| Component | For | Key methods |
|-----------|-----|-------------|
| `BaseComponent` | Anything: a button, a heading, a container | `click`, `jsClick`, `getText`, `getInnerText`, `getValue`, `isDisplayedFast`, `hasClass`, `child`, `children`, `waitUntilVisible`, `waitUntilClickable` |
| `DataTable` | An HTML table read by column header | `getHeaders`, `getColumnValues`, `getCellValue`, `getRow`, `getRows`, `containsValueInColumn`, `findRowIndex`, `waitUntilPopulated` |
| `SelectDropdown` | A native `<select>` | `selectByVisibleText`, `selectByValue`, `selectFirstEnabled`, `getSelectedOption`, `getOptions`, `isMultiple` |
| `SearchableDropdown` | A scripted combo box built from divs | `open`, `search`, `selectByText`, `selectContaining`, `selectFirst`, `getOptionTexts`, `getSelectedText` |
| `AutoComplete` | A type-ahead field whose suggestions arrive asynchronously | `typeAndSelect`, `typeAndSelectFirst`, `getSuggestions`, `waitForSuggestions`, `hasSuggestionsWithin` |
| `Checkbox` | One checkbox, or a group behind a wrapper | `check`, `uncheck`, `setChecked`, `isChecked`, `toggle`, indexed and by-value variants |
| `ListBox` | A dual list box with available and chosen panes | `choose`, `remove`, `chooseFirst`, `clearChosen`, `getAvailableOptions`, `getChosenOptions` |
| `IFrame` | An `<iframe>` | `inFrame(Runnable)`, `inFrame(Supplier)`, `switchTo`, `waitUntilAvailable` |

### Choosing between the dropdown components

- Real `<option>` elements inside a `<select>` → `SelectDropdown`.
- Divs and `[role="option"]`, all options present when the menu opens → `SearchableDropdown`.
- Suggestions fetched from the server as you type → `AutoComplete`.

Pick wrong and the failure is at least a clear one: `SelectDropdown` reports "Element should have
been select but was div".

### The portalled-menu trap

`SearchableDropdown` and `AutoComplete` look for their options inside the component. Many widget
libraries render the menu at the end of `<body>` instead, so an element-scoped lookup finds nothing
however long it waits. If a dropdown "never has any options", this is almost always why:

```java
public class StatusFilter extends SearchableDropdown {

  @Override
  protected SearchContext optionsSearchContext() {
    return getDriver();   // menu is portalled to document body
  }

  // constructors delegating to super
}
```

## Writing a new component

Two requirements: extend `BaseComponent`, and declare the four constructors. The constructors are
how the field decorator recognises the class - there is no registry to add it to.

```java
package com.example.components;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/** A star-rating control: a row of stars, one of which is selected. */
public class StarRating extends BaseComponent {

  private static final By STARS = By.cssSelector("[data-star]");
  private static final By SELECTED = By.cssSelector("[data-star].is-selected");

  public StarRating(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public StarRating(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public StarRating(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public StarRating(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** How many stars the control offers. */
  public int maximum() {
    return count(STARS);
  }

  /** The current rating, or 0 when nothing is selected. */
  public int rating() {
    return countFast(SELECTED);
  }

  /** Sets the rating and waits for the control to reflect it. */
  public void rate(int stars) {
    if (stars < 1 || stars > maximum()) {
      throw new IllegalArgumentException(
          "Rating must be between 1 and " + maximum() + " but was " + stars);
    }
    findElements(STARS).get(stars - 1).click();
    waitUntil(() -> rating() == stars, describe() + " to show " + stars + " star(s)",
        Duration.ofSeconds(5));
  }
}
```

Then use it:

```java
@FindBy(css = "[data-test='product-rating']")
private StarRating rating;

@FindBy(css = ".review")
private List<StarRating> reviewRatings;   // lists work too
```

Guidance worth following while writing one:

- **Make structure overridable.** Where a selector is a guess about how somebody's design system is
  built, put it behind a `protected By ...Locator()` method so a subclass can correct it without
  forking the component. `ListBox` and `SearchableDropdown` are built that way.
- **State outcomes, not toggles.** `check()` and `uncheck()`, not `click()`. A method that flips
  state is a method that fails intermittently depending on what ran before it.
- **Wait for what you caused.** A component that changes the page should not return until the page
  has changed.
- **Do not assert.** Return values and let the test decide.
- **Read text with `getInnerText()` when it might be truncated.** Selenium's `getText()` returns the
  empty string for anything the browser considers not-displayed, which includes CSS-truncated table
  cells.

## Holding components across re-renders

Component lists re-resolve on every access, which is what makes them safe. The corollary: do not
hold a component from a list across an interaction that re-renders its container.

```java
// Wrong: the row is detached the moment deleting re-renders the table.
BaseComponent row = orders.getRowComponent(0);
row.child(By.cssSelector(".delete")).click();
String id = row.getText();                       // stale

// Right: read first, or fetch again.
String id = orders.getCellValue(0, "Order ID");
orders.getRowComponent(0).child(By.cssSelector(".delete")).click();
```

A component built from a `By` recovers from staleness by re-locating; one built from an element in a
list has no locator to recover with, and says so in the log when it goes stale.

## Step definitions

Keep them thin. A step definition should read as the behaviour, with the page object doing the work:

```java
public class OrderSteps extends AbstractSteps {

  @When("I filter the orders by {string}")
  public void iFilterTheOrdersBy(String status) {
    currentPage(OrdersPage.class).filterBy(status);
  }

  @Then("the orders list shows {int} orders")
  public void theOrdersListShows(int expected) {
    assertThat(currentPage(OrdersPage.class).visibleOrderIds())
        .as("orders visible after filtering")
        .hasSize(expected);
  }
}
```

Use `.as(...)` on every assertion. The message is what somebody reads at 9am from a CI summary, with
no browser and no context, and "expected true but was false" tells them nothing.
