package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.FindBy;

/**
 * The index of the demo application, which is a list of links to the individual examples.
 *
 * <p>Note what is not here: a URL. {@link BasePage#openPath(String)} resolves paths against the
 * configured {@code base-url}, so pointing the suite at a different deployment is a configuration
 * change, never a code change.
 */
public class DemoHomePage extends BasePage implements DemoPage {

  public static final String PATH = "/";

  @FindBy(css = "h1.heading")
  private BaseComponent heading;

  @FindBy(css = "#content ul li a")
  private List<BaseComponent> exampleLinks;

  public DemoHomePage(WebDriver driver) {
    super(driver);
  }

  /** Opens the index page. */
  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    heading.waitUntilVisible();
    waitUntil(() -> !exampleLinks.isEmpty(), "the example list to be rendered");
  }

  /** The page heading. */
  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** The name of every example linked from this page. */
  public List<String> getExampleNames() {
    List<String> names = new ArrayList<>();
    exampleLinks.forEach(link -> names.add(link.getNormalizedText()));
    return names;
  }

  /** Whether an example with this name is listed. */
  public boolean hasExample(String name) {
    String wanted = name.trim().toLowerCase(Locale.ROOT);
    return getExampleNames().stream()
        .anyMatch(listed -> listed.toLowerCase(Locale.ROOT).equals(wanted));
  }

  /**
   * Follows the link to an example.
   *
   * @param name the link text, matched case-insensitively
   * @throws NoSuchElementException when no link carries that text
   */
  public void openExample(String name) {
    String wanted = name.trim().toLowerCase(Locale.ROOT);
    for (BaseComponent link : exampleLinks) {
      if (link.getNormalizedText().toLowerCase(Locale.ROOT).equals(wanted)) {
        link.scrollIntoView();
        link.click();
        return;
      }
    }
    throw new NoSuchElementException(
        "No example named '" + name + "'. Available: " + getExampleNames());
  }
}
