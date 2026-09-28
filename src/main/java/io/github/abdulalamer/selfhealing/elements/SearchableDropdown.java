package io.github.abdulalamer.selfhealing.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scripted combo box: a trigger that opens a menu, an optional filter field, and a list of options
 * that are {@code <div>}s and {@code <li>}s rather than {@code <option>}s.
 *
 * <p>This is what nearly every component library ships instead of a native {@code <select>}, and it
 * is where hand-written page objects usually go wrong: they click the trigger, immediately read the
 * options, and get an empty list because the menu animates in. Everything here waits for the menu
 * before touching it.
 *
 * <h2>Portalled menus</h2>
 *
 * <p>Many such widgets render their menu at the end of {@code <body>} rather than inside the
 * component, so an element-scoped lookup finds nothing however long it waits. Override
 * {@link #optionsSearchContext()} to return the driver for those:
 *
 * <pre>{@code
 * @Override
 * protected SearchContext optionsSearchContext() {
 *   return getDriver();   // menu is portalled to document body
 * }
 * }</pre>
 *
 * <p>If a dropdown "never has any options", this is almost always why.
 */
public class SearchableDropdown extends BaseComponent {

  private static final Logger LOG = LoggerFactory.getLogger(SearchableDropdown.class);

  public SearchableDropdown(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public SearchableDropdown(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public SearchableDropdown(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public SearchableDropdown(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** The control that opens the menu. */
  protected By triggerLocator() {
    return By.cssSelector("[role='combobox'], input, button");
  }

  /** The field that filters the options, when the widget has one. */
  protected By searchInputLocator() {
    return By.cssSelector("input[type='text'], input[type='search'], input:not([type])");
  }

  /** The options themselves. */
  protected By optionLocator() {
    return By.cssSelector("[role='option'], li");
  }

  /**
   * Where the options are looked up from. Defaults to this component; override with
   * {@link #getDriver()} for a menu that is portalled to the document body.
   */
  protected SearchContext optionsSearchContext() {
    return asSearchContext();
  }

  /** Whether the menu currently has options on screen. */
  public boolean isOpen() {
    return withoutImplicitWait(() -> !options().isEmpty());
  }

  /** Opens the menu and waits until at least one option is rendered. Idempotent. */
  public void open() {
    if (isOpen()) {
      return;
    }
    retry(() -> {
      trigger().click();
      waitUntil(() -> !options().isEmpty(), describe() + " options to be rendered");
    }, "open " + describe());
  }

  /** Closes the menu with Escape, if it is open. */
  public void close() {
    if (!isOpen()) {
      return;
    }
    trigger().sendKeys(Keys.ESCAPE);
    waitUntil(() -> options().isEmpty(), describe() + " options to disappear");
  }

  /**
   * Opens the menu and types into the filter field.
   *
   * @param text what to type; the field is cleared first
   */
  public void search(String text) {
    open();
    WebElement input = findElement(searchInputLocator());
    input.clear();
    input.sendKeys(text);
  }

  /** The visible text of every option currently rendered. The menu is opened if necessary. */
  public List<String> getOptionTexts() {
    open();
    List<WebElement> options = options();
    List<String> texts = new ArrayList<>(options.size());
    options.forEach(option -> texts.add(normalise(getInnerText(option))));
    return texts;
  }

  /** The text currently shown on the trigger, i.e. the selection. */
  public String getSelectedText() {
    WebElement trigger = trigger();
    String value = trigger.getDomProperty("value");
    return normalise(value != null && !value.isBlank() ? value : getInnerText(trigger));
  }

  /**
   * Selects the option whose text matches exactly, ignoring case and surrounding whitespace.
   *
   * @param option the option's visible text
   * @throws NoSuchElementException when no option matches, listing the ones that do exist
   */
  public void selectByText(String option) {
    select(option, true);
  }

  /**
   * Selects the first option whose text contains the given text, ignoring case.
   *
   * @param fragment the text the option must contain
   * @throws NoSuchElementException when no option matches
   */
  public void selectContaining(String fragment) {
    select(fragment, false);
  }

  /** Types into the filter field and then selects the option matching that text exactly. */
  public void searchAndSelect(String option) {
    search(option);
    selectByText(option);
  }

  /** Selects the option at this position in the menu. */
  public void selectByIndex(int index) {
    open();
    List<WebElement> options = options();
    if (index < 0 || index >= options.size()) {
      throw new IndexOutOfBoundsException("No option at index " + index + " in " + describe()
          + "; the menu has " + options.size() + " option(s)");
    }
    clickOption(options.get(index));
  }

  /**
   * Selects the first option in the menu.
   *
   * @return the text of the option that was selected
   */
  public String selectFirst() {
    open();
    List<WebElement> options = options();
    if (options.isEmpty()) {
      throw new NoSuchElementException(describe() + " has no options to select");
    }
    WebElement first = options.get(0);
    String text = normalise(getInnerText(first));
    clickOption(first);
    return text;
  }

  /** The control that opens the menu. */
  protected WebElement trigger() {
    return findElement(triggerLocator());
  }

  /** The options currently rendered, which may be an empty list when the menu is closed. */
  protected List<WebElement> options() {
    return optionsSearchContext().findElements(optionLocator());
  }

  private void select(String wanted, boolean exact) {
    open();
    String needle = normalise(wanted).toLowerCase(Locale.ROOT);
    List<String> seen = new ArrayList<>();
    for (WebElement option : options()) {
      String text = normalise(getInnerText(option)).toLowerCase(Locale.ROOT);
      seen.add(text);
      if (exact ? text.equals(needle) : text.contains(needle)) {
        clickOption(option);
        return;
      }
    }
    throw new NoSuchElementException("No option " + (exact ? "equal to" : "containing") + " '"
        + wanted + "' in " + describe() + ". Options are: " + seen);
  }

  private void clickOption(WebElement option) {
    scrollIntoView(option, false);
    try {
      option.click();
    } catch (RuntimeException ex) {
      LOG.info("Native click on an option of {} failed ({}); falling back to a scripted click",
          describe(), ex.getClass().getSimpleName());
      executeScript("arguments[0].click();", option);
    }
  }

  private static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
