package io.github.abdulalamer.selfhealing.exception;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;

/**
 * Thrown when a locator could not be resolved and self-healing did not recover it.
 *
 * <p>It deliberately extends Selenium's {@link NoSuchElementException} rather than
 * {@code RuntimeException}. Selenium's own waits ignore {@code NoSuchElementException} while
 * polling; a framework exception outside that hierarchy would break every
 * {@code WebDriverWait...until(...)} in user code. Extending it keeps waits working and still
 * gives the terminal failure a message that says whether healing was even switched on.
 */
public class ElementNotHealedException extends NoSuchElementException {

  private final By locator;

  public ElementNotHealedException(By locator, boolean healingEnabled, Throwable cause) {
    super(buildMessage(locator, healingEnabled), cause);
    this.locator = locator;
  }

  /** The locator that could not be resolved. */
  public By getLocator() {
    return locator;
  }

  private static String buildMessage(By locator, boolean healingEnabled) {
    if (healingEnabled) {
      return "Locator [" + locator + "] did not match and self-healing could not recover it. "
          + "Either the element is genuinely gone, or Healenium has no stored node tree for this "
          + "locator yet (healing can only repair a locator that succeeded at least once while "
          + "the backend was reachable), or no candidate scored above score-cap. "
          + "See docs/SELF_HEALING.md.";
    }
    return "Locator [" + locator + "] did not match. Self-healing is disabled "
        + "(heal-enabled=false), so no recovery was attempted.";
  }
}
