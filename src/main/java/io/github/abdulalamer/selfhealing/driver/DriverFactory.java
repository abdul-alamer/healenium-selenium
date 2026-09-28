package io.github.abdulalamer.selfhealing.driver;

import io.github.abdulalamer.selfhealing.config.BrowserType;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.config.TimeoutConfig;
import java.net.URL;
import java.util.Optional;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates plain WebDriver instances - local or remote - from configuration.
 *
 * <p>No driver binaries are downloaded or managed. Selenium 4 ships Selenium Manager, which resolves
 * and caches the correct driver for the installed browser, so WebDriverManager and a
 * {@code selenium-chrome-driver-version} config key are both unnecessary. One fewer dependency and
 * one fewer thing to pin per Chrome release.
 *
 * <p>This class knows nothing about self-healing. {@link SelfHealingDriverFactory} extends it and
 * wraps the result, which keeps the plain driver path usable and testable on its own.
 */
public class DriverFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

  protected final FrameworkConfig config;
  protected final DriverOptionsBuilder optionsBuilder;

  public DriverFactory() {
    this(FrameworkConfig.get(), new DriverOptionsBuilder(FrameworkConfig.get()));
  }

  public DriverFactory(FrameworkConfig config, DriverOptionsBuilder optionsBuilder) {
    this.config = config;
    this.optionsBuilder = optionsBuilder;
  }

  /** Creates a driver for the configured browser. */
  public WebDriver create() {
    return create(config.browser());
  }

  /**
   * Creates a driver for a specific browser, ignoring the {@code browser} configuration key.
   *
   * @param browser the browser to start
   * @return a configured driver with timeouts and window geometry already applied
   */
  public WebDriver create(BrowserType browser) {
    Optional<URL> remote = config.remoteUrl();
    WebDriver driver = remote.isPresent()
        ? createRemote(remote.get(), browser)
        : createLocal(browser);
    applyTimeouts(driver);
    applyWindowGeometry(driver);
    LOG.info("Started {} driver ({}), headless={}, window={}x{}", browser.configName(),
        remote.map(url -> "remote " + url).orElse("local"), config.headless(),
        config.windowSize().getWidth(), config.windowSize().getHeight());
    return driver;
  }

  /** Starts a browser on this machine. */
  protected WebDriver createLocal(BrowserType browser) {
    return switch (browser) {
      case CHROME -> new ChromeDriver(optionsBuilder.chrome());
      case FIREFOX -> new FirefoxDriver(optionsBuilder.firefox());
      case EDGE -> new EdgeDriver(optionsBuilder.edge());
    };
  }

  /** Starts a browser on a Grid or a standalone Selenium container. */
  protected WebDriver createRemote(URL endpoint, BrowserType browser) {
    AbstractDriverOptions<?> options = optionsBuilder.build(browser);
    return new RemoteWebDriver(endpoint, options);
  }

  /**
   * Applies the four configured timeouts.
   *
   * <p>The implicit wait is deliberately short. It is not a substitute for an explicit wait, and with
   * self-healing switched on it is also the delay before Healenium is even asked to help: healing
   * starts only once the original lookup has failed, and the implicit wait is how long that failure
   * takes to arrive.
   */
  protected void applyTimeouts(WebDriver driver) {
    TimeoutConfig timeouts = config.timeouts();
    driver.manage().timeouts()
        .implicitlyWait(timeouts.implicitWait())
        .pageLoadTimeout(timeouts.pageLoad())
        .scriptTimeout(timeouts.script());
  }

  /** Positions and sizes the window so screenshots are comparable between machines. */
  protected void applyWindowGeometry(WebDriver driver) {
    try {
      driver.manage().window().setPosition(config.windowPosition());
      driver.manage().window().setSize(config.windowSize());
    } catch (RuntimeException ex) {
      // Some headless and remote configurations reject window manipulation. The size was already
      // requested through browser arguments, so this is a refinement, not a requirement.
      LOG.debug("Could not set window geometry: {}", ex.getMessage());
    }
  }
}
