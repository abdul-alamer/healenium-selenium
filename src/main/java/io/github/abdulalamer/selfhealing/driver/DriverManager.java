package io.github.abdulalamer.selfhealing.driver;

import java.util.Optional;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the driver for the current thread.
 *
 * <p>A {@link ThreadLocal} rather than a singleton, because Cucumber's JUnit Platform engine runs
 * scenarios on a pool of threads when {@code cucumber.execution.parallel.enabled=true}. With a
 * singleton, two scenarios share one browser and produce failures that are impossible to read; with a
 * thread-local, raising {@code -Pthreads} is the only change needed to run wide.
 *
 * <p>The contract is narrow on purpose: the scenario hooks own the lifecycle
 * ({@link #set(WebDriver)} in {@code @Before}, {@link #quit()} in {@code @After}), and everything
 * else only ever reads {@link #get()}. Page objects take the driver as a constructor argument, so
 * nothing below the hooks depends on this class at all - which is what keeps the page layer usable
 * from a plain JUnit test with a driver you created yourself.
 *
 * <p>{@link #quit()} removes the thread-local entry even when the browser refuses to close, because a
 * stale entry left on a pooled thread would be handed to the next scenario.
 */
public final class DriverManager {

  private static final Logger LOG = LoggerFactory.getLogger(DriverManager.class);
  private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

  private DriverManager() {
  }

  /**
   * Binds a driver to the current thread.
   *
   * @param driver the driver to bind, never null
   */
  public static void set(WebDriver driver) {
    if (driver == null) {
      throw new IllegalArgumentException("Cannot bind a null WebDriver");
    }
    if (DRIVER.get() != null) {
      LOG.warn("A WebDriver is already bound to thread '{}'; the previous one is being replaced "
          + "without being quit, which usually means a hook did not run", currentThread());
    }
    DRIVER.set(driver);
  }

  /**
   * The driver bound to the current thread.
   *
   * @throws IllegalStateException when no driver is bound, which means a scenario ran without its
   *     {@code @Before} hook or a page object was used from a thread that owns no browser
   */
  public static WebDriver get() {
    WebDriver driver = DRIVER.get();
    if (driver == null) {
      throw new IllegalStateException("No WebDriver is bound to thread '" + currentThread()
          + "'. Bind one in a @Before hook with DriverManager.set(new SelfHealingDriverFactory()"
          + ".create()).");
    }
    return driver;
  }

  /** The driver bound to the current thread, if any. */
  public static Optional<WebDriver> peek() {
    return Optional.ofNullable(DRIVER.get());
  }

  /** Whether a driver is bound to the current thread. */
  public static boolean isSet() {
    return DRIVER.get() != null;
  }

  /**
   * Quits the bound driver and unbinds it. Safe to call when nothing is bound, and never throws: a
   * browser that will not close must not turn a passing scenario red.
   */
  public static void quit() {
    WebDriver driver = DRIVER.get();
    if (driver == null) {
      return;
    }
    try {
      driver.quit();
    } catch (RuntimeException ex) {
      LOG.warn("WebDriver did not quit cleanly on thread '{}': {}", currentThread(),
          ex.getMessage());
    } finally {
      DRIVER.remove();
    }
  }

  private static String currentThread() {
    return Thread.currentThread().getName();
  }
}
