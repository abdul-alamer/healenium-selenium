package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The add/remove-elements example: the worked case for a list of typed components.
 *
 * <p>{@code deleteButtons} is a {@code List<BaseComponent>} that re-resolves on every access, which
 * is what makes {@link #removeFirst()} safe. Each deletion rebuilds the list in the DOM; a list
 * captured once would hand back a detached node on the second call.
 */
public class AddRemoveElementsPage extends BasePage implements DemoPage {

  public static final String PATH = "/add_remove_elements/";

  @FindBy(css = "h3")
  private BaseComponent heading;

  @FindBy(css = "button[onclick='addElement()']")
  private BaseComponent addButton;

  @FindBy(css = "#elements button.added-manually")
  private List<BaseComponent> deleteButtons;

  public AddRemoveElementsPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    addButton.waitUntilClickable();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** Adds one element and waits for the list to grow. */
  public void addElement() {
    int before = count();
    addButton.click();
    waitUntil(() -> count() > before, "the element list to grow past " + before);
  }

  /** Adds several elements. */
  public void addElements(int howMany) {
    for (int added = 0; added < howMany; added++) {
      addElement();
    }
  }

  /** Removes the first element and waits for the list to shrink. */
  public void removeFirst() {
    int before = count();
    if (before == 0) {
      throw new IllegalStateException("There is nothing to remove");
    }
    deleteButtons.get(0).click();
    waitUntil(() -> count() < before, "the element list to shrink below " + before);
  }

  /** How many removable elements are on the page. */
  public int count() {
    return elementCount(By.cssSelector("#elements button.added-manually"));
  }
}
