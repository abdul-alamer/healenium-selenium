package io.github.abdulalamer.selfhealing.elements;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * A type-ahead field: typing triggers a lookup and a list of suggestions appears.
 *
 * <p>Different from {@link SearchableDropdown} in the way that matters for tests. A dropdown's
 * options exist as soon as it opens; an autocomplete's suggestions arrive from the server after a
 * debounce, so "type then read" is a race and the only correct pattern is "type, wait for
 * suggestions, then read". {@link #typeAndSelect(String)} does exactly that, and is what most tests
 * should call.
 *
 * <p>As with {@link SearchableDropdown}, suggestions are frequently portalled to the end of
 * {@code <body>}; override {@link #suggestionsSearchContext()} to return the driver in that case.
 */
public class AutoComplete extends BaseComponent {

  public AutoComplete(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public AutoComplete(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public AutoComplete(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public AutoComplete(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** The field that is typed into. */
  protected By inputLocator() {
    return By.cssSelector("input, textarea");
  }

  /** The suggestions produced by typing. */
  protected By suggestionLocator() {
    return By.cssSelector("[role='option'], li");
  }

  /**
   * Where suggestions are looked up from. Defaults to this component; override with
   * {@link #getDriver()} when they are portalled to the document body.
   */
  protected SearchContext suggestionsSearchContext() {
    return asSearchContext();
  }

  /** The field that is typed into. */
  public WebElement input() {
    WebElement root = getWebElement();
    String tag = root.getTagName();
    if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)) {
      return root;
    }
    return root.findElement(inputLocator());
  }

  /** What the field currently contains. */
  public String getTypedText() {
    String value = input().getDomProperty("value");
    return value == null ? "" : value;
  }

  /** Types without clearing the field first. */
  public void type(String text) {
    input().sendKeys(text);
  }

  /**
   * Clears the field and types.
   *
   * <p>Clears with a select-all and delete as well as {@code clear()}, because a scripted field
   * frequently ignores {@code clear()} - it sets the DOM value without firing the {@code input} event
   * the widget listens for, so the old text reappears on the next keystroke.
   */
  public void clearAndType(String text) {
    WebElement field = input();
    field.click();
    field.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
    field.clear();
    field.sendKeys(text);
  }

  /** Presses Enter in the field, for widgets that accept free text. */
  public void submit() {
    input().sendKeys(Keys.ENTER);
  }

  /** Waits until at least one suggestion is rendered. */
  public void waitForSuggestions() {
    waitUntil(() -> !suggestions().isEmpty(), describe() + " to show suggestions");
  }

  /**
   * Whether any suggestion appears within a timeout, without failing when none does. Use it to assert
   * that a query legitimately has no results.
   */
  public boolean hasSuggestionsWithin(Duration timeout) {
    try {
      waitUntil(() -> !suggestions().isEmpty(), describe() + " to show suggestions", timeout);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /** The visible text of every suggestion currently rendered. */
  public List<String> getSuggestions() {
    List<WebElement> found = suggestions();
    List<String> texts = new ArrayList<>(found.size());
    found.forEach(suggestion -> texts.add(normalise(getInnerText(suggestion))));
    return texts;
  }

  /**
   * The full interaction: clear, type, wait for suggestions, pick the one containing the text.
   *
   * @param text what to type and match against
   * @throws NoSuchElementException when no suggestion contains the text
   */
  public void typeAndSelect(String text) {
    clearAndType(text);
    waitForSuggestions();
    selectSuggestionContaining(text);
  }

  /** Clears, types, waits, and takes the first suggestion regardless of its text. */
  public String typeAndSelectFirst(String text) {
    clearAndType(text);
    waitForSuggestions();
    return selectFirstSuggestion();
  }

  /**
   * Selects the suggestion whose text matches exactly, ignoring case and surrounding whitespace.
   *
   * @throws NoSuchElementException when no suggestion matches
   */
  public void selectSuggestion(String text) {
    selectMatching(text, true);
  }

  /**
   * Selects the first suggestion containing the given text, ignoring case.
   *
   * @throws NoSuchElementException when no suggestion matches
   */
  public void selectSuggestionContaining(String fragment) {
    selectMatching(fragment, false);
  }

  /**
   * Selects the first suggestion.
   *
   * @return the text of the suggestion that was selected
   */
  public String selectFirstSuggestion() {
    List<WebElement> found = suggestions();
    if (found.isEmpty()) {
      throw new NoSuchElementException(describe() + " has no suggestions to select");
    }
    WebElement first = found.get(0);
    String text = normalise(getInnerText(first));
    first.click();
    return text;
  }

  /** The suggestions currently rendered; an empty list when none are. */
  protected List<WebElement> suggestions() {
    return suggestionsSearchContext().findElements(suggestionLocator());
  }

  private void selectMatching(String wanted, boolean exact) {
    String needle = normalise(wanted).toLowerCase(Locale.ROOT);
    List<String> seen = new ArrayList<>();
    for (WebElement suggestion : suggestions()) {
      String text = normalise(getInnerText(suggestion)).toLowerCase(Locale.ROOT);
      seen.add(text);
      if (exact ? text.equals(needle) : text.contains(needle)) {
        scrollIntoView(suggestion, false);
        suggestion.click();
        return;
      }
    }
    throw new NoSuchElementException("No suggestion " + (exact ? "equal to" : "containing") + " '"
        + wanted + "' in " + describe() + ". Suggestions are: " + seen);
  }

  private static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
