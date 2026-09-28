package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.SelectDropdown;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The native-dropdown example.
 *
 * <p>Shows a typed component doing the work: the page object exposes intent ("choose Option 2",
 * "what is selected?") and holds no knowledge of how a {@code <select>} is driven.
 */
public class DropdownPage extends BasePage implements DemoPage {

  public static final String PATH = "/dropdown";

  @FindBy(css = "h3")
  private BaseComponent heading;

  @FindBy(id = "dropdown")
  private SelectDropdown dropdown;

  public DropdownPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    dropdown.waitUntilVisible();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** Every option, including the disabled placeholder. */
  public List<String> getOptions() {
    return dropdown.getOptions();
  }

  /** Chooses an option by its visible text. */
  public void select(String option) {
    dropdown.selectByVisibleText(option);
  }

  /**
   * Chooses the first option that is not disabled.
   *
   * <p>This example opens on a disabled "Please select an option" placeholder, which is exactly the
   * case {@code selectFirstEnabled()} exists for.
   */
  public String selectFirstSelectableOption() {
    return dropdown.selectFirstEnabled();
  }

  /** The visible text of the current selection. */
  public String getSelectedOption() {
    return dropdown.getSelectedOption();
  }
}
