package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.Checkbox;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The checkbox example.
 *
 * <p>The {@code @FindBy} points at the form, not at an input, so one {@link Checkbox} component
 * covers both boxes and is addressed by index. That is the common shape in real applications, where
 * the clickable thing is a styled wrapper and the real {@code <input>} is hidden behind it.
 */
public class CheckboxesPage extends BasePage implements DemoPage {

  public static final String PATH = "/checkboxes";

  @FindBy(css = "h3")
  private BaseComponent heading;

  @FindBy(id = "checkboxes")
  private Checkbox checkboxes;

  public CheckboxesPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    checkboxes.waitUntilVisible();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** How many boxes the form contains. */
  public int getCheckboxCount() {
    return checkboxes.size();
  }

  /** Whether the box at this zero-based position is ticked. */
  public boolean isChecked(int index) {
    return checkboxes.isChecked(index);
  }

  /** Ticks the box at this zero-based position, doing nothing if it is already ticked. */
  public void check(int index) {
    checkboxes.check(index);
  }

  /** Unticks the box at this zero-based position, doing nothing if it is already unticked. */
  public void uncheck(int index) {
    checkboxes.uncheck(index);
  }
}
