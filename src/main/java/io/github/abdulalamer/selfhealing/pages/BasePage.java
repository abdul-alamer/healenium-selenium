package io.github.abdulalamer.selfhealing.pages;

import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.config.TimeoutConfig;
import io.github.abdulalamer.selfhealing.elements.ComponentFieldDecorator;
import io.github.abdulalamer.selfhealing.exception.ElementNotHealedException;
import io.github.abdulalamer.selfhealing.exception.FrameworkTimeoutException;
import io.github.abdulalamer.selfhealing.healing.HealingAwareConditions;
import io.github.abdulalamer.selfhealing.support.Retry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for page objects: a driver, a search context, and the primitives every page needs.
 *
 * <p>Two things make a subclass work:
 *
 * <ol>
 *   <li>Fields annotated {@code @FindBy} are injected by {@link ComponentFieldDecorator}, so a field
 *       may be a plain {@code WebElement}, a typed component such as
 *       {@code io.github.abdulalamer.selfhealing.elements.DataTable}, or a {@code List} of either.
 *   <li>{@link #waitPageLoading()} must be implemented. A page object that cannot say when it is
 *       ready pushes that decision into every step definition, and that is where sleeps come from.
 * </ol>
 *
 * <p>The base URL is never written in a page object. {@link #openPath(String)} resolves paths against
 * the configured {@code base-url}, so the same page objects run against local, staging and production
 * without edits.
 *
 * <p>This class carries no application-specific vocabulary. The framework it was extracted from had
 * toast-message assertions, notification dismissal and picklist helpers on its page base - useful
 * there, meaningless anywhere else. Only the primitives survived: waits, retries, scrolling, frames,
 * alerts, windows, script execution and the implicit-timeout guard.
 */
public abstract class BasePage {

  private static final Logger LOG = LoggerFactory.getLogger(BasePage.class);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

  protected final WebDriver driver;
  protected final SearchContext searchContext;
  protected final FrameworkConfig config;
  protected final TimeoutConfig timeouts;

  private Actions actions;

  /** A page whose fields are located from the driver. */
  protected BasePage(WebDriver driver) {
    this(driver, driver, true);
  }

  /** A page whose fields are located from a narrower context, for example inside a dialog. */
  protected BasePage(WebDriver driver, SearchContext searchContext) {
    this(driver, searchContext, true);
  }

  /**
   * Full constructor.
   *
   * @param driver           the driver, which for a self-healing run is the wrapped
   *                         {@code SelfHealingDriver}: every {@code @FindBy} resolved through it
   *                         becomes healable
   * @param searchContext    where annotated fields are located from; null means the driver
   * @param initialiseFields whether to inject annotated fields now. Components pass false because
   *                         their root element is not known until their own constructor has run;
   *                         calling an overridable method from this constructor would read fields the
   *                         subclass has not assigned yet
   */
  protected BasePage(WebDriver driver, SearchContext searchContext, boolean initialiseFields) {
    this.driver = Objects.requireNonNull(driver, "driver");
    this.searchContext = searchContext == null ? driver : searchContext;
    this.config = FrameworkConfig.get();
    this.timeouts = config.timeouts();
    if (initialiseFields) {
      initFields(this.searchContext);
    }
  }

  /**
   * Injects every {@code @FindBy} field of this object, resolving them from {@code root}.
   *
   * <p>One pass, not two: {@link ComponentFieldDecorator} falls back to Selenium's default
   * decoration for plain {@code WebElement} fields, so raw elements and typed components are both
   * scoped to the same root. The framework this was extracted from ran {@code PageFactory} twice -
   * once against the driver for elements and once against the component root for components - which
   * meant a plain {@code WebElement} field inside a component was silently searched from the whole
   * document rather than from the component.
   *
   * @param root the search context annotated fields are resolved against
   */
  protected final void initFields(SearchContext root) {
    PageFactory.initElements(new ComponentFieldDecorator(driver, root), this);
  }

  /**
   * Blocks until this page is ready to be used.
   *
   * <p>Implement it in terms of something the page only has when it is genuinely usable - a heading,
   * a populated table, a spinner that has gone - not a fixed pause.
   */
  public abstract void waitPageLoading();

  public WebDriver getDriver() {
    return driver;
  }

  public SearchContext getSearchContext() {
    return searchContext;
  }

  public FrameworkConfig config() {
    return config;
  }

  public TimeoutConfig timeouts() {
    return timeouts;
  }

  // --------------------------------------------------------------------------- navigation

  /**
   * Navigates to a path relative to the configured base URL and waits for the page to be ready.
   *
   * @param path a path such as {@code /tables}, with or without the leading slash
   */
  public void openPath(String path) {
    String url = config.baseUrl() + (path.startsWith("/") ? path : "/" + path);
    LOG.info("Opening {}", url);
    driver.get(url);
    waitPageLoading();
  }

  public String currentUrl() {
    return driver.getCurrentUrl();
  }

  public String pageTitle() {
    return driver.getTitle();
  }

  /** Reloads the page and waits for it to be ready again. */
  public void refresh() {
    driver.navigate().refresh();
    waitPageLoading();
  }

  /** Goes back and waits for the previous page to be ready. */
  public void navigateBack() {
    driver.navigate().back();
    waitPageLoading();
  }

  // --------------------------------------------------------------------------- waits

  /** A wait bounded by the configured explicit-wait timeout. */
  public WebDriverWait webDriverWait() {
    return webDriverWait(timeouts.explicitWait());
  }

  /** A wait bounded by an explicit timeout. */
  public WebDriverWait webDriverWait(Duration timeout) {
    WebDriverWait wait = new WebDriverWait(driver, timeout);
    wait.pollingEvery(POLL_INTERVAL);
    return wait;
  }

  /**
   * Waits for a condition, using the configured explicit-wait timeout.
   *
   * @param condition   what must become true
   * @param description what is being waited for, used in the timeout message
   */
  public void waitUntil(BooleanSupplier condition, String description) {
    waitUntil(condition, description, timeouts.explicitWait());
  }

  /**
   * Waits for a condition.
   *
   * <p>Exceptions thrown while polling are ignored, because "the element is not there yet" is the
   * normal state of affairs early in a wait. The condition either becomes true within the timeout or
   * a {@link FrameworkTimeoutException} names what never happened.
   *
   * @param condition   what must become true
   * @param description what is being waited for, used in the timeout message
   * @param timeout     how long to wait
   */
  public void waitUntil(BooleanSupplier condition, String description, Duration timeout) {
    try {
      Awaitility.await(description)
          .atMost(timeout)
          .pollDelay(Duration.ZERO)
          .pollInterval(POLL_INTERVAL)
          .ignoreExceptions()
          .until(condition::getAsBoolean);
    } catch (ConditionTimeoutException ex) {
      throw new FrameworkTimeoutException(
          "Timed out after " + timeout.toSeconds() + "s waiting for: " + description, ex);
    }
  }

  /** Waits until the browser reports the document as loaded. */
  public void waitUntilPageLoaded() {
    webDriverWait(timeouts.pageLoad()).until(HealingAwareConditions.documentReady());
  }

  public WebElement waitUntilVisible(WebElement element) {
    return waitUntilVisible(element, timeouts.explicitWait());
  }

  public WebElement waitUntilVisible(WebElement element, Duration timeout) {
    return webDriverWait(timeout).until(ExpectedConditions.visibilityOf(element));
  }

  public WebElement waitUntilClickable(WebElement element) {
    return waitUntilClickable(element, timeouts.explicitWait());
  }

  public WebElement waitUntilClickable(WebElement element, Duration timeout) {
    return webDriverWait(timeout).until(ExpectedConditions.elementToBeClickable(element));
  }

  public boolean waitUntilInvisible(WebElement element) {
    return waitUntilInvisible(element, timeouts.explicitWait());
  }

  public boolean waitUntilInvisible(WebElement element, Duration timeout) {
    return webDriverWait(timeout).until(ExpectedConditions.invisibilityOf(element));
  }

  /**
   * Waits until a lazily-resolved element reference can be resolved at all.
   *
   * <p>Prefer this over {@link #waitUntilVisible(WebElement)} for a {@code @FindBy} field that may
   * not exist yet: each poll is a fresh lookup, and with self-healing on, each fresh lookup is
   * another opportunity for recovery.
   *
   * @param element the element reference to resolve
   * @return the same reference once it resolves
   */
  public WebElement waitUntilLocated(WebElement element) {
    return waitUntilLocated(element, timeouts.explicitWait());
  }

  public WebElement waitUntilLocated(WebElement element, Duration timeout) {
    return webDriverWait(timeout).until(HealingAwareConditions.proxyElementLocated(element));
  }

  // --------------------------------------------------------------------------- retries

  /**
   * Retries an action using the configured attempt count and backoff.
   *
   * @param action      what to do
   * @param description what the action is, used in logs and in the failure message
   */
  public void retry(Runnable action, String description) {
    Retry.run(action, description);
  }

  /** Retries an action with an explicit attempt count and backoff. */
  public void retry(Runnable action, String description, int attempts, Duration backoff) {
    Retry.run(action, description, attempts, backoff);
  }

  // --------------------------------------------------------------------------- element access

  /**
   * Finds one element within this page's search context.
   *
   * @param locator what to look for
   * @return the element
   * @throws ElementNotHealedException when the locator matches nothing; the message says whether
   *     self-healing was enabled, which is the first question anybody asks
   */
  public WebElement findElement(By locator) {
    try {
      return searchContext.findElement(locator);
    } catch (NoSuchElementException ex) {
      throw new ElementNotHealedException(locator, config.healing().enabled(), ex);
    }
  }

  /** Finds every element matching a locator; an empty list is a valid answer, not a failure. */
  public List<WebElement> findElements(By locator) {
    return searchContext.findElements(locator);
  }

  /** How many elements a locator matches right now. */
  public int elementCount(By locator) {
    return findElements(locator).size();
  }

  /** Whether a locator matches anything, waiting up to the implicit timeout. */
  public boolean isPresent(By locator) {
    return elementCount(locator) > 0;
  }

  /**
   * Whether a locator matches anything, without waiting.
   *
   * <p>Uses the implicit-timeout guard, so a negative answer costs milliseconds instead of the full
   * implicit wait. Use it for "is this optional thing on screen?", never for "has it appeared yet?".
   */
  public boolean isPresentFast(By locator) {
    return withoutImplicitWait(() -> elementCount(locator) > 0);
  }

  // --------------------------------------------------------------------- implicit timeout guard

  /** Sets the driver-level implicit wait. */
  public void setImplicitTimeout(Duration timeout) {
    driver.manage().timeouts().implicitlyWait(timeout);
  }

  /** Restores the implicit wait to the configured value. */
  public void resetImplicitTimeout() {
    setImplicitTimeout(timeouts.implicitWait());
  }

  /**
   * Runs a supplier with the implicit wait set to zero, restoring it afterwards.
   *
   * <p>The reference framework spread {@code setImplicitTimeout(0)} / {@code resetImplicitTimeout()}
   * pairs through try/finally blocks in a dozen methods; one leaked pair leaves the whole suite with
   * no implicit wait. Wrapping the pattern once makes that impossible.
   *
   * @param <T>    the supplied type
   * @param action what to run without waiting
   * @return whatever the action returned
   */
  public <T> T withoutImplicitWait(Supplier<T> action) {
    setImplicitTimeout(Duration.ZERO);
    try {
      return action.get();
    } finally {
      resetImplicitTimeout();
    }
  }

  /** Runs an action with the implicit wait set to zero, restoring it afterwards. */
  public void withoutImplicitWait(Runnable action) {
    withoutImplicitWait(() -> {
      action.run();
      return null;
    });
  }

  // --------------------------------------------------------------------------- scripts

  /**
   * Executes JavaScript in the page.
   *
   * @param <T>    expected result type
   * @param script the script, which may {@code return} a value
   * @param args   arguments exposed to the script as {@code arguments[0]}, {@code arguments[1]}, ...
   * @return the script result, cast to {@code T}
   */
  @SuppressWarnings("unchecked")
  public <T> T executeScript(String script, Object... args) {
    return (T) ((JavascriptExecutor) driver).executeScript(script, args);
  }

  /**
   * Reads an element's rendered text through {@code innerText}.
   *
   * <p>Selenium's {@code getText()} returns the empty string for anything the browser considers
   * not-displayed, which includes text clipped by CSS truncation - very common in tables. Reading
   * {@code innerText} gets the value anyway.
   */
  public String getInnerText(WebElement element) {
    return executeScript("return arguments[0].innerText;", element);
  }

  // --------------------------------------------------------------------------- scrolling

  /** Scrolls an element into view, aligned to the bottom of the viewport. */
  public void scrollIntoView(WebElement element) {
    scrollIntoView(element, false);
  }

  /**
   * Scrolls an element into view.
   *
   * @param element    what to scroll to
   * @param alignToTop true to align with the top of the viewport, false with the bottom
   */
  public void scrollIntoView(WebElement element, boolean alignToTop) {
    executeScript("arguments[0].scrollIntoView(arguments[1]);", element, alignToTop);
  }

  /** Scrolls to the bottom of the document, which is how lazily-loaded lists are made to load. */
  public void scrollToBottom() {
    actions().keyDown(Keys.CONTROL).sendKeys(Keys.END).keyUp(Keys.CONTROL).perform();
  }

  /** Scrolls to the top of the document. */
  public void scrollToTop() {
    actions().keyDown(Keys.CONTROL).sendKeys(Keys.HOME).keyUp(Keys.CONTROL).perform();
  }

  // --------------------------------------------------------------------------- interactions

  /** A reusable {@link Actions} instance for this page. */
  public Actions actions() {
    if (actions == null) {
      actions = new Actions(driver);
    }
    return actions;
  }

  /** Moves the pointer onto an element, which is how hover-only menus are opened. */
  public void moveToElement(WebElement element) {
    actions().moveToElement(element).perform();
  }

  /** Moves the pointer onto an element and clicks it. */
  public void moveAndClick(WebElement element) {
    actions().moveToElement(element).pause(Duration.ofMillis(100)).click().perform();
  }

  /**
   * Dispatches a synthetic {@code mouseover} and then clicks, both through JavaScript.
   *
   * <p>For controls that only become clickable once a framework's own {@code mouseover} handler has
   * run, and which a native pointer move cannot reach reliably - a menu item revealed by hovering its
   * parent row, for example.
   */
  public void clickWithMouseover(WebElement element) {
    executeScript(
        "arguments[0].dispatchEvent(new MouseEvent('mouseover', {bubbles: true}));", element);
    executeScript("arguments[0].click();", element);
  }

  // --------------------------------------------------------------------------- alerts

  /** Accepts a native alert if one appears within the given timeout. */
  public boolean acceptAlertIfPresent(Duration timeout) {
    Optional<Alert> alert = awaitAlert(timeout);
    alert.ifPresent(Alert::accept);
    return alert.isPresent();
  }

  /** Dismisses a native alert if one appears within the given timeout. */
  public boolean dismissAlertIfPresent(Duration timeout) {
    Optional<Alert> alert = awaitAlert(timeout);
    alert.ifPresent(Alert::dismiss);
    return alert.isPresent();
  }

  /** The text of a native alert, if one appears within the given timeout. */
  public Optional<String> alertText(Duration timeout) {
    return awaitAlert(timeout).map(Alert::getText);
  }

  private Optional<Alert> awaitAlert(Duration timeout) {
    try {
      return Optional.of(webDriverWait(timeout).until(ExpectedConditions.alertIsPresent()));
    } catch (RuntimeException ex) {
      LOG.debug("No alert appeared within {}s", timeout.toSeconds());
      return Optional.empty();
    }
  }

  // --------------------------------------------------------------------------- frames

  /** Switches into a frame element. */
  public void switchToFrame(WebElement frame) {
    driver.switchTo().frame(frame);
  }

  /** Waits for a frame to be available and switches into it. */
  public void switchToFrame(By locator) {
    webDriverWait().until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(locator));
  }

  /** Switches back to the top-level document. */
  public void switchToDefaultContent() {
    driver.switchTo().defaultContent();
  }

  // --------------------------------------------------------------------------- windows and tabs

  /** Opens a new blank tab and switches to it. */
  public void openNewTab() {
    driver.switchTo().newWindow(WindowType.TAB);
  }

  /** Every open window handle, in the order the browser reports them. */
  public List<String> windowHandles() {
    return new ArrayList<>(driver.getWindowHandles());
  }

  /**
   * Switches to a window by index.
   *
   * @param index zero-based position in {@link #windowHandles()}
   */
  public void switchToTab(int index) {
    List<String> handles = windowHandles();
    if (index < 0 || index >= handles.size()) {
      throw new IllegalArgumentException(
          "No window at index " + index + "; " + handles.size() + " window(s) are open");
    }
    driver.switchTo().window(handles.get(index));
  }

  /** Closes the current window and switches back to the first remaining one. */
  public void closeCurrentTab() {
    driver.close();
    List<String> remaining = windowHandles();
    if (!remaining.isEmpty()) {
      driver.switchTo().window(remaining.get(0));
    }
  }

  // --------------------------------------------------------------------------- helpers

  /**
   * Short alias for {@link String#format}, kept because locator templates read far better with it:
   * {@code f(ROW_BY_NAME, name)}.
   */
  protected static String f(String template, Object... args) {
    return String.format(template, args);
  }
}
