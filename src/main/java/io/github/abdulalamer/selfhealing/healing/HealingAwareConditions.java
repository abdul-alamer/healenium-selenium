package io.github.abdulalamer.selfhealing.healing;

import java.util.List;
import java.util.Objects;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;

/**
 * Wait conditions that cooperate with lazily-resolving, self-healing element references.
 *
 * <p>{@code PageFactory} hands page objects a proxy, not an element: nothing is looked up until a
 * method is called on it. Selenium's own {@code ExpectedConditions.visibilityOf(element)} therefore
 * resolves the proxy once, immediately, and a failure at that instant ends the wait. With a
 * self-healing driver that is exactly the wrong moment to give up, because the retry is what triggers
 * recovery.
 *
 * <p>The conditions here poke the proxy with the cheapest possible call and treat "not there yet" as
 * "keep waiting", which is what the reference framework's {@code proxyWebElementLocated} did. This is
 * the generalised version of it.
 */
public final class HealingAwareConditions {

  private HealingAwareConditions() {
  }

  /**
   * Waits until a lazily-resolved element reference can actually be resolved.
   *
   * <p>Calls {@code getTagName()} on the proxy - the cheapest method that forces a lookup and that
   * every element answers - and keeps polling while the lookup reports the element as absent or
   * stale. Each poll is a fresh lookup, so each poll is another chance for healing to recover the
   * locator.
   *
   * @param proxy the element reference to resolve, typically a {@code @FindBy} field
   * @return the same reference once it resolves
   */
  public static ExpectedCondition<WebElement> proxyElementLocated(WebElement proxy) {
    Objects.requireNonNull(proxy, "proxy");
    return new ExpectedCondition<>() {
      @Override
      public WebElement apply(WebDriver driver) {
        try {
          proxy.getTagName();
          return proxy;
        } catch (NoSuchElementException | StaleElementReferenceException ex) {
          return null;
        }
      }

      @Override
      public String toString() {
        return "element reference to be locatable: " + proxy;
      }
    };
  }

  /**
   * Waits until a locator matches at least {@code minimum} elements within a context.
   *
   * <p>Useful for lists that populate asynchronously, where asserting on {@code size()} straight away
   * races the render. Note that {@code findElements} never triggers healing - Healenium heals a
   * lookup that failed, and an empty list is a successful lookup that found nothing.
   *
   * @param context where to search, a driver or a parent element
   * @param locator what to search for
   * @param minimum how many matches are enough
   * @return the matched elements once there are enough of them
   */
  public static ExpectedCondition<List<WebElement>> elementCountAtLeast(
      SearchContext context, By locator, int minimum) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(locator, "locator");
    return new ExpectedCondition<>() {
      @Override
      public List<WebElement> apply(WebDriver driver) {
        try {
          List<WebElement> found = context.findElements(locator);
          return found.size() >= minimum ? found : null;
        } catch (StaleElementReferenceException ex) {
          return null;
        }
      }

      @Override
      public String toString() {
        return "at least " + minimum + " element(s) matching " + locator;
      }
    };
  }

  /**
   * Waits until the browser reports the document as fully loaded.
   *
   * @return true once {@code document.readyState} is {@code complete}
   */
  public static ExpectedCondition<Boolean> documentReady() {
    return new ExpectedCondition<>() {
      @Override
      public Boolean apply(WebDriver driver) {
        if (!(driver instanceof JavascriptExecutor executor)) {
          return Boolean.TRUE;
        }
        Object state = executor.executeScript("return document.readyState");
        return "complete".equals(String.valueOf(state));
      }

      @Override
      public String toString() {
        return "document.readyState to be 'complete'";
      }
    };
  }
}
