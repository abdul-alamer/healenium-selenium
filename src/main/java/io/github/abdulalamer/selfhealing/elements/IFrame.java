package io.github.abdulalamer.selfhealing.elements;

import java.time.Duration;
import java.util.function.Supplier;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * An {@code <iframe>}, with a scoped switch that cannot be forgotten.
 *
 * <p>Frame handling is stateful on the driver, which makes the obvious API the dangerous one: a test
 * that switches in and then fails mid-way leaves every later step looking in the wrong document, and
 * the resulting cascade of "element not found" failures points nowhere near the real problem. So the
 * method to reach for is {@link #inFrame(Supplier)}, which switches back in a {@code finally} block:
 *
 * <pre>{@code
 * String body = editorFrame.inFrame(() -> editorBody.getText());
 * }</pre>
 *
 * <p>{@link #switchTo()} and {@link #switchToDefaultContent()} remain available for the cases where a
 * scenario genuinely spans several steps inside a frame, but they put the discipline back on the
 * caller.
 */
public class IFrame extends BaseComponent {

  public IFrame(WebDriver driver, WebElement element) {
    super(driver, element);
  }

  public IFrame(WebDriver driver, SearchContext parent, WebElement element) {
    super(driver, parent, element);
  }

  public IFrame(WebDriver driver, By locator) {
    super(driver, locator);
  }

  public IFrame(WebDriver driver, SearchContext parent, By locator) {
    super(driver, parent, locator);
  }

  /** Switches the driver into this frame. */
  public void switchTo() {
    getDriver().switchTo().frame(getWebElement());
  }

  /** Switches the driver back to the top-level document. */
  public void switchToDefaultContent() {
    getDriver().switchTo().defaultContent();
  }

  /**
   * Waits until the frame is available, switches into it, and switches straight back out.
   *
   * <p>Useful as a readiness check: a frame element can exist in the DOM well before its document is
   * loadable, and this is the cheapest way to find out which side of that line you are on.
   */
  public void waitUntilAvailable() {
    waitUntilAvailable(timeouts.pageLoad());
  }

  /** Waits until the frame is available, bounded by an explicit timeout. */
  public void waitUntilAvailable(Duration timeout) {
    webDriverWait(timeout)
        .until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(getWebElement()));
    switchToDefaultContent();
  }

  /**
   * Runs an action inside this frame and switches back out afterwards, even if it throws.
   *
   * @param action what to do inside the frame
   */
  public void inFrame(Runnable action) {
    inFrame(() -> {
      action.run();
      return null;
    });
  }

  /**
   * Evaluates a supplier inside this frame and switches back out afterwards, even if it throws.
   *
   * @param <T>    the supplied type
   * @param action what to evaluate inside the frame
   * @return whatever the action returned
   */
  public <T> T inFrame(Supplier<T> action) {
    switchTo();
    try {
      return action.get();
    } finally {
      switchToDefaultContent();
    }
  }

  /**
   * The frame is ready when its document is loadable, not merely when the element exists.
   */
  @Override
  public void waitPageLoading() {
    waitUntilAvailable();
  }
}
