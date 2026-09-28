package io.github.abdulalamer.selfhealing.elements;

import io.github.abdulalamer.selfhealing.exception.ElementNotHealedException;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for every typed component: one element, plus the interactions worth sharing.
 *
 * <p>A component is a page rooted at an element rather than at the document, which is why it extends
 * {@link BasePage} - it inherits the same waits, script execution, scrolling and implicit-timeout
 * guard, and its own {@code @FindBy} fields are scoped to its element instead of the whole page.
 *
 * <h2>Stale-element recovery, and how it differs from the original</h2>
 *
 * <p>The framework this was extracted from recovered a stale element by parsing
 * {@code WebElement.toString()}. It searched the string for markers such as {@code "-> xpath:"} and
 * {@code "cssSelector:"}, sliced the locator value out with index arithmetic, and re-ran the lookup.
 * It worked, but it was fragile by construction: the format of {@code toString()} is a debugging
 * convenience with no compatibility guarantee, it differs between a real element and a
 * {@code PageFactory} proxy (hence a second, near-duplicate parser), and any driver or proxy layer
 * that changes the text silently turns recovery into a {@code NullPointerException}. A self-healing
 * driver is exactly such a layer.
 *
 * <p>Here a component keeps what it was built from instead of trying to recover it afterwards: the
 * {@link By} locator and the {@link SearchContext} it was resolved against. A stale element is
 * therefore re-located exactly, deterministically, and from the same parent - no string parsing, no
 * guessing, and a locator type that Selenium adds tomorrow needs no change here. Decorator-injected
 * components need no locator at all: their element is a proxy that re-resolves on every call.
 *
 * <p>The same idea makes nested lookups safe. {@link #asSearchContext()} returns a context that
 * resolves through {@link #getWebElement()}, so a child component built from it inherits this
 * component's recovery rather than caching a node that may already be detached.
 */
public class BaseComponent extends BasePage {

  private static final Logger LOG = LoggerFactory.getLogger(BaseComponent.class);

  /** The locator this component was built from, or null when it was built from an element. */
  private final By locator;

  /** Where {@link #locator} is resolved from; null means the driver. */
  private final SearchContext locationContext;

  private WebElement element;

  /** A component wrapping an already-located element, usually a {@code PageFactory} proxy. */
  public BaseComponent(WebDriver driver, WebElement element) {
    this(driver, null, null, Objects.requireNonNull(element, "element"));
  }

  /** A component wrapping an already-located element, remembering the context it came from. */
  public BaseComponent(WebDriver driver, SearchContext parent, WebElement element) {
    this(driver, parent, null, Objects.requireNonNull(element, "element"));
  }

  /** A component located by {@code locator} from the document. */
  public BaseComponent(WebDriver driver, By locator) {
    this(driver, null, Objects.requireNonNull(locator, "locator"), null);
  }

  /** A component located by {@code locator} from within {@code parent}. */
  public BaseComponent(WebDriver driver, SearchContext parent, By locator) {
    this(driver, parent, Objects.requireNonNull(locator, "locator"), null);
  }

  /** A component located by {@code locator} from within another component. */
  public BaseComponent(BaseComponent parent, By locator) {
    this(parent.getDriver(), parent.asSearchContext(), Objects.requireNonNull(locator, "locator"),
        null);
  }

  private BaseComponent(WebDriver driver, SearchContext parent, By locator, WebElement element) {
    // false: this component's own @FindBy fields must be scoped to its element, which is not known
    // until the assignments below have run.
    super(driver, parent, false);
    this.locator = locator;
    this.locationContext = parent;
    this.element = element;
    initFields(asSearchContext());
  }

  /**
   * A component is ready when its element is visible. Concrete components with a richer notion of
   * readiness - a table that must have rows, a dropdown whose options must have rendered - override
   * this.
   */
  @Override
  public void waitPageLoading() {
    waitUntilVisible();
  }

  // --------------------------------------------------------------------------- element access

  /**
   * The underlying element, re-locating it if it has gone stale.
   *
   * @return a usable element
   * @throws StaleElementReferenceException when the element is stale and this component retained no
   *     locator to re-find it with, which can only happen for a component constructed directly from
   *     a caller-supplied element
   */
  public WebElement getWebElement() {
    if (element == null) {
      element = locate();
      return element;
    }
    try {
      // Cheapest call that forces the reference to be validated.
      element.getTagName();
      return element;
    } catch (StaleElementReferenceException ex) {
      if (locator == null) {
        LOG.warn("{} went stale and retained no locator, so it cannot be re-located. Build the "
            + "component from a By - or from a @FindBy field - to make recovery possible.",
            describe());
        throw ex;
      }
      LOG.debug("{} went stale; re-locating with the retained locator", describe());
      element = locate();
      return element;
    }
  }

  /** The locator this component was built from, empty when it was built from an element. */
  public Optional<By> getLocator() {
    return Optional.ofNullable(locator);
  }

  /**
   * This component as a search context that re-resolves through {@link #getWebElement()}.
   *
   * <p>Use it to build children that inherit this component's stale recovery:
   * {@code new SelectDropdown(driver, parent.asSearchContext(), By.cssSelector("select"))}.
   */
  public SearchContext asSearchContext() {
    return new SearchContext() {
      @Override
      public WebElement findElement(By by) {
        return getWebElement().findElement(by);
      }

      @Override
      public List<WebElement> findElements(By by) {
        return getWebElement().findElements(by);
      }

      @Override
      public String toString() {
        return "self-refreshing context of " + describe();
      }
    };
  }

  /** A child component located within this one, retaining its locator for recovery. */
  public BaseComponent child(By by) {
    return new BaseComponent(this, by);
  }

  /** Every child matching a locator, as components. */
  public List<BaseComponent> children(By by) {
    List<WebElement> found = getWebElement().findElements(by);
    List<BaseComponent> components = new ArrayList<>(found.size());
    found.forEach(child -> components.add(new BaseComponent(driver, asSearchContext(), child)));
    return components;
  }

  /** How many descendants match a locator. */
  public int count(By by) {
    return getWebElement().findElements(by).size();
  }

  /** How many descendants match a locator, without waiting out the implicit timeout. */
  public int countFast(By by) {
    return withoutImplicitWait(() -> {
      try {
        return getWebElement().findElements(by).size();
      } catch (RuntimeException ex) {
        return 0;
      }
    });
  }

  @Override
  public WebElement findElement(By by) {
    try {
      return getWebElement().findElement(by);
    } catch (NoSuchElementException ex) {
      throw new ElementNotHealedException(by, config.healing().enabled(), ex);
    }
  }

  @Override
  public List<WebElement> findElements(By by) {
    return getWebElement().findElements(by);
  }

  // --------------------------------------------------------------------------- interactions

  /**
   * Clicks the element, falling back to a scripted click when something is in the way.
   *
   * <p>{@code ElementClickInterceptedException} means the browser found another node at the click
   * point - a fading toast, a sticky header, an overlay mid-animation. A scripted click dispatches
   * straight at the element and gets past it. This is a pragmatic fallback, not a licence to ignore
   * overlays: it is logged at info so a component that always needs it is visible in the log.
   */
  public void click() {
    try {
      getWebElement().click();
    } catch (ElementClickInterceptedException ex) {
      LOG.info("Click on {} was intercepted; falling back to a scripted click", describe());
      jsClick();
    }
  }

  /** Clicks through JavaScript, bypassing hit-testing entirely. */
  public void jsClick() {
    executeScript("arguments[0].click();", getWebElement());
  }

  /** Double-clicks the element. */
  public void doubleClick() {
    actions().doubleClick(getWebElement()).perform();
  }

  /** Moves the pointer onto the element and clicks it. */
  public void moveAndClick() {
    moveAndClick(getWebElement());
  }

  /** Moves the pointer onto the element without clicking. */
  public void moveTo() {
    moveToElement(getWebElement());
  }

  /** Drags the element by a pixel offset. */
  public void dragAndDropBy(int xOffset, int yOffset) {
    actions()
        .moveToElement(getWebElement())
        .clickAndHold()
        .pause(Duration.ofMillis(200))
        .moveByOffset(xOffset, yOffset)
        .pause(Duration.ofMillis(200))
        .release()
        .perform();
  }

  /** Clears the element. */
  public void clear() {
    getWebElement().clear();
  }

  /** Types into the element without clearing it first. */
  public void sendKeys(CharSequence... keys) {
    getWebElement().sendKeys(keys);
  }

  /** Clears the element, waiting for it to be interactive first, then types. */
  public void clearAndSendKeys(CharSequence keys) {
    waitUntilClickable();
    WebElement target = getWebElement();
    target.clear();
    target.sendKeys(keys);
  }

  /** Submits the enclosing form. */
  public void submit() {
    getWebElement().submit();
  }

  // --------------------------------------------------------------------------- state

  /** Visible text, as Selenium reports it. */
  public String getText() {
    return getWebElement().getText();
  }

  /** Visible text with runs of whitespace collapsed to single spaces. */
  public String getNormalizedText() {
    String text = getText();
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  /**
   * Rendered text read through {@code innerText}.
   *
   * <p>Necessary far more often than it should be: Selenium's {@code getText()} returns the empty
   * string for text the browser considers not-displayed, which includes CSS-truncated table cells.
   */
  public String getInnerText() {
    return getInnerText(getWebElement());
  }

  public String getAttribute(String name) {
    return getWebElement().getAttribute(name);
  }

  /**
   * The element's current value.
   *
   * <p>Reads the DOM <em>property</em> first and falls back to the attribute, because after typing,
   * the {@code value} attribute still holds the markup's original value while the property holds what
   * the user sees.
   */
  public String getValue() {
    WebElement target = getWebElement();
    String property = target.getDomProperty("value");
    return property != null ? property : target.getAttribute("value");
  }

  public String getCssValue(String property) {
    return getWebElement().getCssValue(property);
  }

  /** Whether the element is present and visible; false instead of throwing when it is absent. */
  public boolean isDisplayed() {
    try {
      return getWebElement().isDisplayed();
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /**
   * Whether the element is visible, answered without waiting out the implicit timeout.
   *
   * <p>For "is this optional thing on screen right now?". Using {@link #isDisplayed()} for a negative
   * check costs the full implicit wait on every call, which is how suites quietly become slow.
   */
  public boolean isDisplayedFast() {
    // Block body, so the lambda is value-compatible only and cannot bind to the Runnable overload.
    return withoutImplicitWait(() -> {
      return isDisplayed();
    });
  }

  public boolean isEnabled() {
    return getWebElement().isEnabled();
  }

  public boolean isSelected() {
    return getWebElement().isSelected();
  }

  /** The element's CSS classes. */
  public List<String> classes() {
    String attribute = getAttribute("class");
    if (attribute == null || attribute.isBlank()) {
      return List.of();
    }
    return Arrays.stream(attribute.trim().split("\\s+")).toList();
  }

  /** Whether the element carries a CSS class. */
  public boolean hasClass(String className) {
    return classes().contains(className);
  }

  // --------------------------------------------------------------------------- waits

  /** Scrolls the element into view, aligned to the bottom of the viewport. */
  public void scrollIntoView() {
    scrollIntoView(false);
  }

  /** Scrolls the element into view. */
  public void scrollIntoView(boolean alignToTop) {
    scrollIntoView(getWebElement(), alignToTop);
  }

  /**
   * Waits until the element can be located at all.
   *
   * <p>Every wait below is expressed as a polled predicate rather than as a Selenium
   * {@code ExpectedCondition} over a resolved element, and that is deliberate. Handing an element to
   * {@code ExpectedConditions.visibilityOf(...)} resolves it once, immediately; for a component built
   * from a {@link By} that means the lookup - and any failure - happens before the wait has even
   * started. Polling {@link #isDisplayed()} instead re-locates on every poll, so waiting for an
   * element that has not rendered yet works, and with self-healing on, each poll is another chance to
   * recover a locator that has stopped matching.
   */
  public void waitUntilLocated() {
    waitUntilLocated(timeouts.explicitWait());
  }

  /** Waits until the element can be located at all, bounded by an explicit timeout. */
  public void waitUntilLocated(Duration timeout) {
    waitUntil(this::isLocatable, describe() + " to be locatable", timeout);
  }

  /** Waits until the element is visible. */
  public void waitUntilVisible() {
    waitUntilVisible(timeouts.explicitWait());
  }

  /** Waits until the element is visible, bounded by an explicit timeout. */
  public void waitUntilVisible(Duration timeout) {
    waitUntil(this::isDisplayed, describe() + " to be visible", timeout);
  }

  /** Waits until the element is gone or hidden. */
  public void waitUntilInvisible() {
    waitUntilInvisible(timeouts.explicitWait());
  }

  /** Waits until the element is gone or hidden, bounded by an explicit timeout. */
  public void waitUntilInvisible(Duration timeout) {
    waitUntil(() -> !isDisplayed(), describe() + " to be invisible", timeout);
  }

  /** Waits until the element is visible and enabled. */
  public void waitUntilClickable() {
    waitUntilClickable(timeouts.explicitWait());
  }

  /** Waits until the element is visible and enabled, bounded by an explicit timeout. */
  public void waitUntilClickable(Duration timeout) {
    waitUntil(() -> {
      WebElement target = getWebElement();
      return target.isDisplayed() && target.isEnabled();
    }, describe() + " to be clickable", timeout);
  }

  /** Whether the element resolves right now. */
  public boolean isLocatable() {
    try {
      getWebElement().getTagName();
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /** Whether the element becomes visible within a timeout, without failing if it does not. */
  public boolean isVisibleWithin(Duration timeout) {
    try {
      waitUntilVisible(timeout);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /** Whether the element disappears within a timeout, without failing if it does not. */
  public boolean isInvisibleWithin(Duration timeout) {
    try {
      waitUntilInvisible(timeout);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  // --------------------------------------------------------------------------- diagnostics

  /** A short description used in logs and failure messages. */
  public String describe() {
    return getClass().getSimpleName() + (locator != null ? " located by " + locator : " (element)");
  }

  @Override
  public String toString() {
    return describe();
  }

  private WebElement locate() {
    SearchContext context = locationContext == null ? driver : locationContext;
    try {
      return context.findElement(locator);
    } catch (NoSuchElementException ex) {
      throw new ElementNotHealedException(locator, config.healing().enabled(), ex);
    }
  }
}
