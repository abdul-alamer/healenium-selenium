package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.Checkbox;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The dynamic-controls example: a checkbox that can be removed and re-added, and a field that can be
 * enabled and disabled, each behind a loading indicator.
 *
 * <p>This page carries the self-healing demonstration, because it is the one example on the demo
 * site with a stable control inside a container that can plausibly be "refactored".
 *
 * <h2>How the demonstration works</h2>
 *
 * <p>Healing repairs a locator that <em>used to work</em>. Healenium stores the node tree of every
 * element it successfully finds, and consults that stored tree only when the same locator later
 * matches nothing. A locator that has never succeeded therefore cannot be healed - there is nothing
 * stored to compare against - which rules out the obvious demo of writing a wrong selector and
 * hoping.
 *
 * <p>So the demonstration changes the page rather than the locator, which is what happens in real
 * life anyway:
 *
 * <ol>
 *   <li>{@link #getSwapControlLabel()} resolves {@code #checkbox-example button} against the page as
 *       shipped. It succeeds, and Healenium stores the button's node tree against that locator.
 *   <li>{@link #simulateFrontEndRefactor()} renames the container from {@code checkbox-example} to
 *       {@code checkbox-example-v2}, exactly as a front-end change might. The markup is otherwise
 *       untouched: same button, same text, same position, same ancestors.
 *   <li>{@link #getSwapControlLabel()} is called again. The locator now matches nothing, Healenium
 *       asks the selector-imitator to score the stored tree against the current DOM, the button
 *       scores far above {@code score-cap} because only one attribute of one ancestor changed, and
 *       the element comes back.
 * </ol>
 *
 * <p>Both lookups go through the same method on purpose: Healenium keys a stored tree by the locator
 * together with the context it was first resolved from, so training and healing through one method
 * keeps that key stable.
 *
 * <p>The DOM is mutated with JavaScript because the demo site is public and cannot be edited. That
 * is the only artificial part of the demonstration - everything after the mutation is the real
 * Healenium path, with a real backend and a real scoring round-trip.
 */
public class DynamicControlsPage extends BasePage implements DemoPage {

  public static final String PATH = "/dynamic_controls";

  /** The container id as the page ships. */
  public static final String CHECKBOX_FORM_ID = "checkbox-example";

  /** The container id after the simulated refactor. */
  public static final String REFACTORED_FORM_ID = "checkbox-example-v2";

  /**
   * The locator that self-healing is asked to rescue.
   *
   * <p>It is correct for the page as shipped and deliberately outdated the moment
   * {@link #simulateFrontEndRefactor()} runs, at which point it matches nothing. With healing
   * enabled and the backend reachable, the element still comes back; with
   * {@code -Dheal-enabled=false} the same code raises {@code ElementNotHealedException}. Running the
   * scenario both ways is the quickest way to see what healing is actually doing.
   */
  @FindBy(css = "#checkbox-example button")
  private BaseComponent swapControl;

  @FindBy(css = "#checkbox-example #checkbox")
  private Checkbox optionalCheckbox;

  @FindBy(css = "#checkbox-example #message")
  private BaseComponent checkboxMessage;

  @FindBy(css = "#input-example button")
  private BaseComponent toggleInputControl;

  @FindBy(css = "#input-example input[type='text']")
  private BaseComponent textInput;

  @FindBy(css = "#input-example #message")
  private BaseComponent inputMessage;

  @FindBy(css = "h4")
  private BaseComponent heading;

  private static final By CHECKBOX_INPUT = By.cssSelector("#checkbox input[type='checkbox']");
  private static final Duration SWAP_TIMEOUT = Duration.ofSeconds(15);

  public DynamicControlsPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    heading.waitUntilVisible();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  // ------------------------------------------------------------------ self-healing demonstration

  /**
   * Reads the label of the swap control through the healable locator.
   *
   * <p>Called once before the refactor to train Healenium and once after it to be healed. Deliberately
   * a read rather than a click: a click would change the page and muddy what the second call proves.
   *
   * @return {@code Remove} or {@code Add}, depending on the current state
   */
  public String getSwapControlLabel() {
    return swapControl.getNormalizedText();
  }

  /**
   * Renames the checkbox container so that {@code #checkbox-example button} stops matching.
   *
   * <p>Stands in for a front-end refactor. The button itself is untouched, which is precisely the
   * situation healing is designed for: the element still exists and is still recognisable, only the
   * path to it has changed.
   *
   * @return the container's new id
   * @throws IllegalStateException when the container is not on the page, which means the page was
   *     not opened or has already been refactored
   */
  public String simulateFrontEndRefactor() {
    String newId = executeScript(
        "const container = document.getElementById(arguments[0]);"
            + "if (!container) { return null; }"
            + "container.id = arguments[1];"
            + "return container.id;",
        CHECKBOX_FORM_ID, REFACTORED_FORM_ID);
    if (newId == null) {
      throw new IllegalStateException("No element with id '" + CHECKBOX_FORM_ID
          + "' on " + currentUrl() + "; open the page before refactoring it");
    }
    return newId;
  }

  /** Whether the container still carries its original id. */
  public boolean hasOriginalContainerId() {
    return isPresentFast(By.id(CHECKBOX_FORM_ID));
  }

  // ------------------------------------------------------------------------- checkbox controls

  /** Whether the optional checkbox is currently on the page. */
  public boolean isCheckboxPresent() {
    return isPresentFast(CHECKBOX_INPUT);
  }

  /**
   * Presses the swap control and waits for the checkbox to appear or disappear.
   *
   * <p>The example hides a three-second loading bar behind that button, so this is also the page's
   * demonstration of waiting on an outcome rather than on the clock.
   */
  public void toggleCheckbox() {
    boolean presentBefore = isCheckboxPresent();
    swapControl.waitUntilClickable();
    swapControl.click();
    waitUntil(() -> isCheckboxPresent() != presentBefore,
        "the optional checkbox to " + (presentBefore ? "disappear" : "appear"), SWAP_TIMEOUT);
  }

  /** Ticks the optional checkbox. */
  public void checkOptionalCheckbox() {
    optionalCheckbox.check();
  }

  /** Whether the optional checkbox is ticked. */
  public boolean isOptionalCheckboxChecked() {
    return optionalCheckbox.isChecked();
  }

  /** The confirmation message shown after the checkbox is added or removed. */
  public String getCheckboxMessage() {
    checkboxMessage.waitUntilVisible(SWAP_TIMEOUT);
    return checkboxMessage.getNormalizedText();
  }

  // ---------------------------------------------------------------------------- input controls

  /** Whether the text field accepts input. */
  public boolean isTextInputEnabled() {
    return textInput.isEnabled();
  }

  /** Presses the enable/disable control and waits for the field to change state. */
  public void toggleTextInput() {
    boolean enabledBefore = isTextInputEnabled();
    toggleInputControl.waitUntilClickable();
    toggleInputControl.click();
    waitUntil(() -> isTextInputEnabled() != enabledBefore,
        "the text field to be " + (enabledBefore ? "disabled" : "enabled"), SWAP_TIMEOUT);
  }

  /** Types into the text field. */
  public void typeIntoTextInput(String text) {
    textInput.clearAndSendKeys(text);
  }

  /** What the text field currently contains. */
  public String getTextInputValue() {
    return textInput.getValue();
  }

  /** The confirmation message shown after the field is enabled or disabled. */
  public String getInputMessage() {
    inputMessage.waitUntilVisible(SWAP_TIMEOUT);
    return inputMessage.getNormalizedText();
  }
}
