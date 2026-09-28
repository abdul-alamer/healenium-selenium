package io.github.abdulalamer.selfhealing.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * A dual list box: two panes of options and buttons that move items between them.
 *
 * <p>There is no cross-application convention for this widget, so the structure is expressed as four
 * overridable lookups rather than as fixed selectors. The defaults cover the ARIA pattern
 * ({@code [role="listbox"]} panes holding {@code [role="option"]} items) and plain markup
 * ({@code <select multiple>}, {@code <ul>}), with the panes and the buttons identified by their order
 * in the DOM - which, for this widget, is reliable in a way that class names are not.
 *
 * <p>When a design system differs, subclass and override the lookup:
 *
 * <pre>{@code
 * public class TeamPicker extends ListBox {
 *
 *   @Override
 *   protected By paneLocator() {
 *     return By.cssSelector(".picker__column");
 *   }
 *
 *   // constructors delegating to super
 * }
 * }</pre>
 *
 * <p>Option matching is case-insensitive and whitespace-normalised.
 */
public class ListBox extends BaseComponent {

  public ListBox(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public ListBox(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public ListBox(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public ListBox(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** The two option panes. The first match is "available", the second is "chosen". */
  protected By paneLocator() {
    return By.cssSelector("[role='listbox'], select, ul");
  }

  /** The options within a pane. */
  protected By optionLocator() {
    return By.cssSelector("[role='option'], option, li");
  }

  /** The buttons that move options between the panes. */
  protected By controlLocator() {
    return By.cssSelector("button");
  }

  /** Index of the "available" pane in {@link #paneLocator()} matches. */
  protected int availablePaneIndex() {
    return 0;
  }

  /** Index of the "chosen" pane in {@link #paneLocator()} matches. */
  protected int chosenPaneIndex() {
    return 1;
  }

  /** Index of the "move to chosen" button in {@link #controlLocator()} matches. */
  protected int moveToChosenIndex() {
    return 0;
  }

  /** Index of the "move to available" button in {@link #controlLocator()} matches. */
  protected int moveToAvailableIndex() {
    return 1;
  }

  /** The options that have not been chosen yet. */
  public List<String> getAvailableOptions() {
    return optionTexts(pane(availablePaneIndex()));
  }

  /** The options that have been chosen. */
  public List<String> getChosenOptions() {
    return optionTexts(pane(chosenPaneIndex()));
  }

  /** Every option, chosen or not. */
  public List<String> getAllOptions() {
    List<String> all = new ArrayList<>(getAvailableOptions());
    all.addAll(getChosenOptions());
    return all;
  }

  /** Whether an option has been chosen. */
  public boolean isChosen(String option) {
    return contains(getChosenOptions(), option);
  }

  /** Whether an option is still available. */
  public boolean isAvailable(String option) {
    return contains(getAvailableOptions(), option);
  }

  /**
   * Moves one option from available to chosen.
   *
   * @param option the option's visible text
   * @throws NoSuchElementException when the option is not in the available pane
   */
  public void choose(String option) {
    selectOption(pane(availablePaneIndex()), option);
    control(moveToChosenIndex()).click();
  }

  /**
   * Moves one option from chosen back to available.
   *
   * @param option the option's visible text
   * @throws NoSuchElementException when the option is not in the chosen pane
   */
  public void remove(String option) {
    selectOption(pane(chosenPaneIndex()), option);
    control(moveToAvailableIndex()).click();
  }

  /**
   * Moves the first {@code count} available options to chosen, one at a time.
   *
   * <p>One at a time on purpose: the panes re-render after each move, so selecting several and then
   * pressing the button once works in some implementations and silently drops items in others.
   *
   * @param count how many to move; more than are available moves all of them
   */
  public void chooseFirst(int count) {
    for (int moved = 0; moved < count; moved++) {
      List<String> available = getAvailableOptions();
      if (available.isEmpty()) {
        return;
      }
      choose(available.get(0));
    }
  }

  /** Moves every chosen option back to available. */
  public void clearChosen() {
    while (true) {
      List<String> chosen = getChosenOptions();
      if (chosen.isEmpty()) {
        return;
      }
      remove(chosen.get(0));
    }
  }

  private WebElement pane(int index) {
    List<WebElement> panes = findElements(paneLocator());
    if (index >= panes.size()) {
      throw new NoSuchElementException("Expected at least " + (index + 1) + " option pane(s) in "
          + describe() + " but found " + panes.size() + ". Override paneLocator() for this widget.");
    }
    return panes.get(index);
  }

  private WebElement control(int index) {
    List<WebElement> controls = findElements(controlLocator());
    if (index >= controls.size()) {
      throw new NoSuchElementException("Expected at least " + (index + 1) + " control button(s) in "
          + describe() + " but found " + controls.size()
          + ". Override controlLocator() for this widget.");
    }
    return controls.get(index);
  }

  private void selectOption(WebElement pane, String option) {
    String wanted = normalise(option).toLowerCase(Locale.ROOT);
    for (WebElement candidate : pane.findElements(optionLocator())) {
      if (normalise(getInnerText(candidate)).toLowerCase(Locale.ROOT).equals(wanted)) {
        candidate.click();
        return;
      }
    }
    throw new NoSuchElementException(
        "No option '" + option + "' in that pane of " + describe()
            + ". Options are: " + optionTexts(pane));
  }

  private List<String> optionTexts(WebElement pane) {
    List<WebElement> options = pane.findElements(optionLocator());
    List<String> texts = new ArrayList<>(options.size());
    options.forEach(option -> texts.add(normalise(getInnerText(option))));
    return texts;
  }

  private static boolean contains(List<String> options, String option) {
    String wanted = normalise(option).toLowerCase(Locale.ROOT);
    return options.stream().anyMatch(each -> each.toLowerCase(Locale.ROOT).equals(wanted));
  }

  private static String normalise(String value) {
    return value == null ? "" : value.replaceAll("\\s+", " ").trim();
  }
}
