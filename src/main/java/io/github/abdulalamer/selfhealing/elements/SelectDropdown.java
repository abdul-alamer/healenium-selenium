package io.github.abdulalamer.selfhealing.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

/**
 * A native HTML {@code <select>}.
 *
 * <p>Thin by design: Selenium's own {@link Select} already handles native selects correctly, so this
 * component adds only what a test actually asks for - reading the options and the selection as
 * strings, and selecting the first option that can be selected.
 *
 * <p>The root may be the {@code <select>} itself or any wrapper around exactly one, which matters
 * because design systems usually wrap the control in a label-and-hint container that is the natural
 * thing to point {@code @FindBy} at.
 *
 * <p>For a scripted dropdown built from {@code <div>}s and {@code [role="option"]} - the kind most
 * component libraries ship - use {@link SearchableDropdown} instead. The two are not
 * interchangeable, and the failure when you pick the wrong one ("Element should have been select but
 * was div") is at least a clear one.
 */
public class SelectDropdown extends BaseComponent {

  private static final By NESTED_SELECT = By.tagName("select");

  public SelectDropdown(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public SelectDropdown(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public SelectDropdown(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public SelectDropdown(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** Selects the option with this visible text. */
  public void selectByVisibleText(String text) {
    select().selectByVisibleText(text);
  }

  /** Selects the option with this {@code value} attribute. */
  public void selectByValue(String value) {
    select().selectByValue(value);
  }

  /** Selects the option at this zero-based index. */
  public void selectByIndex(int index) {
    select().selectByIndex(index);
  }

  /**
   * Selects the first option that is not disabled.
   *
   * <p>Dropdowns very often open on a disabled "Please choose..." placeholder, so "select the first
   * one" and "select index 0" are different requests. This one does what the tester meant.
   *
   * @return the visible text of the option that was selected
   * @throws NoSuchElementException when every option is disabled
   */
  public String selectFirstEnabled() {
    for (WebElement option : select().getOptions()) {
      if (option.isEnabled()) {
        String text = normalise(option.getText());
        select().selectByVisibleText(text);
        return text;
      }
    }
    throw new NoSuchElementException("Every option of " + describe() + " is disabled");
  }

  /** The visible text of the selected option. */
  public String getSelectedOption() {
    return normalise(select().getFirstSelectedOption().getText());
  }

  /** The visible text of every selected option, for a multiple select. */
  public List<String> getSelectedOptions() {
    return textsOf(select().getAllSelectedOptions());
  }

  /** The visible text of every option, selectable or not. */
  public List<String> getOptions() {
    return textsOf(select().getOptions());
  }

  /** Whether an option with this visible text exists. */
  public boolean hasOption(String text) {
    String wanted = normalise(text).toLowerCase(Locale.ROOT);
    return getOptions().stream()
        .anyMatch(option -> option.toLowerCase(Locale.ROOT).equals(wanted));
  }

  /** Whether more than one option can be selected at a time. */
  public boolean isMultiple() {
    return select().isMultiple();
  }

  /** Clears the selection of a multiple select. */
  public void deselectAll() {
    select().deselectAll();
  }

  /**
   * The Selenium {@link Select} wrapper around the underlying control.
   *
   * <p>Resolved on every call rather than cached, so it always wraps the element
   * {@link #getWebElement()} currently vouches for.
   */
  protected Select select() {
    WebElement root = getWebElement();
    WebElement control = "select".equalsIgnoreCase(root.getTagName())
        ? root
        : root.findElement(NESTED_SELECT);
    return new Select(control);
  }

  private static List<String> textsOf(List<WebElement> options) {
    List<String> texts = new ArrayList<>(options.size());
    options.forEach(option -> texts.add(normalise(option.getText())));
    return texts;
  }

  private static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
