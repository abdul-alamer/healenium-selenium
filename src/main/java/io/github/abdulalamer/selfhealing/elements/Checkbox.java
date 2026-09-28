package io.github.abdulalamer.selfhealing.elements;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A checkbox or radio input, or a wrapper containing one or several of them.
 *
 * <p>Checking a box is one of the few interactions where the naive version is genuinely wrong.
 * {@code click()} toggles, so a test that clicks to "tick" a box un-ticks it whenever the previous
 * state was already ticked - and then fails somewhere else entirely. Every method here is stated as a
 * target state, never as a toggle, apart from {@link #toggle()} which says what it does.
 *
 * <p>The root may be the {@code <input>} itself or a wrapper around it. Design systems hide the real
 * input behind a styled {@code <label>} or {@code <span>}, so pointing {@code @FindBy} at the visible
 * wrapper is usually the only option; this component finds the input inside it.
 */
public class Checkbox extends BaseComponent {

  private static final Logger LOG = LoggerFactory.getLogger(Checkbox.class);
  private static final By INPUTS =
      By.cssSelector("input[type='checkbox'], input[type='radio']");

  public Checkbox(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public Checkbox(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public Checkbox(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public Checkbox(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** Whether the first input is ticked. */
  public boolean isChecked() {
    return firstInput().isSelected();
  }

  /** Whether the input at this index is ticked. */
  public boolean isChecked(int index) {
    return inputAt(index).isSelected();
  }

  /** Whether the input carrying this {@code value} attribute is ticked. */
  public boolean isChecked(String value) {
    return inputWithValue(value).isSelected();
  }

  /** Ticks the first input if it is not already ticked. */
  public void check() {
    setChecked(firstInput(), true, describe());
  }

  /** Unticks the first input if it is ticked. */
  public void uncheck() {
    setChecked(firstInput(), false, describe());
  }

  /** Puts the first input into the requested state. */
  public void setChecked(boolean checked) {
    if (checked) {
      check();
    } else {
      uncheck();
    }
  }

  /** Ticks the input at this index. */
  public void check(int index) {
    setChecked(inputAt(index), true, describe() + "[" + index + "]");
  }

  /** Unticks the input at this index. */
  public void uncheck(int index) {
    setChecked(inputAt(index), false, describe() + "[" + index + "]");
  }

  /** Ticks the input carrying this {@code value} attribute. */
  public void check(String value) {
    setChecked(inputWithValue(value), true, describe() + "[value=" + value + "]");
  }

  /** Unticks the input carrying this {@code value} attribute. */
  public void uncheck(String value) {
    setChecked(inputWithValue(value), false, describe() + "[value=" + value + "]");
  }

  /** Flips the first input, whatever state it is in. */
  public void toggle() {
    click();
  }

  /** How many inputs this component covers. */
  public int size() {
    return inputs().size();
  }

  /** The visible text around the checkbox, which is normally its label. */
  public String getLabelText() {
    return getNormalizedText();
  }

  /**
   * Every input this component covers.
   *
   * <p>When the root is itself an input the list is just that input; otherwise every checkbox and
   * radio inside the root, in document order.
   */
  protected List<WebElement> inputs() {
    WebElement root = getWebElement();
    if ("input".equalsIgnoreCase(root.getTagName())) {
      return List.of(root);
    }
    return root.findElements(INPUTS);
  }

  /**
   * Drives one input to a target state.
   *
   * <p>A styled checkbox is frequently a zero-sized or visually-hidden input behind a decorative
   * label, so a native click can land on the label and be swallowed. If the state did not change,
   * this falls back to a scripted click - the same two-step the reference framework used, but
   * symmetric (it only did this when ticking) and logged rather than silent.
   */
  private void setChecked(WebElement input, boolean target, String what) {
    if (input.isSelected() == target) {
      LOG.debug("{} is already {}", what, target ? "checked" : "unchecked");
      return;
    }
    input.click();
    if (input.isSelected() == target) {
      return;
    }
    LOG.info("Native click did not change the state of {}; falling back to a scripted click", what);
    executeScript("arguments[0].click();", input);
    if (input.isSelected() != target) {
      throw new IllegalStateException(
          what + " could not be " + (target ? "checked" : "unchecked") + "; it is most likely "
              + "disabled or covered by another element");
    }
  }

  private WebElement firstInput() {
    return inputAt(0);
  }

  private WebElement inputAt(int index) {
    List<WebElement> inputs = inputs();
    if (index < 0 || index >= inputs.size()) {
      throw new IndexOutOfBoundsException("No input at index " + index + " in " + describe()
          + "; it covers " + inputs.size() + " input(s)");
    }
    return inputs.get(index);
  }

  private WebElement inputWithValue(String value) {
    String wanted = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    Optional<WebElement> match = inputs().stream()
        .filter(input -> {
          String actual = input.getDomProperty("value");
          return actual != null && actual.trim().toLowerCase(Locale.ROOT).equals(wanted);
        })
        .findFirst();
    return match.orElseThrow(() -> new NoSuchElementException(
        "No input with value '" + value + "' in " + describe()));
  }
}
