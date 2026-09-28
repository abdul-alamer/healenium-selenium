package io.github.abdulalamer.selfhealing.driver;

import io.github.abdulalamer.selfhealing.config.BrowserType;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import io.github.abdulalamer.selfhealing.support.RuntimeEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds browser options from configuration.
 *
 * <p>Separated from {@link DriverFactory} so that "what the browser is configured to do" can be
 * reviewed, unit-tested and overridden without touching "how the driver is started". Teams
 * typically need one or two extra arguments for their own environment; subclass this and override a
 * single method rather than forking the factory.
 *
 * <p>Two categories of flag are applied:
 *
 * <ul>
 *   <li><b>Always</b> - window size, download directory, notification suppression. These make runs
 *       reproducible between machines.
 *   <li><b>CI only</b> - {@code --no-sandbox}, {@code --disable-dev-shm-usage}. These exist because
 *       containers have no user namespace and a 64 MB {@code /dev/shm}; adding them on a developer
 *       laptop weakens the browser sandbox for no benefit.
 * </ul>
 */
public class DriverOptionsBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(DriverOptionsBuilder.class);

  protected final FrameworkConfig config;

  public DriverOptionsBuilder() {
    this(FrameworkConfig.get());
  }

  public DriverOptionsBuilder(FrameworkConfig config) {
    this.config = config;
  }

  /** Builds options for the given browser. */
  public AbstractDriverOptions<?> build(BrowserType browser) {
    return switch (browser) {
      case CHROME -> chrome();
      case FIREFOX -> firefox();
      case EDGE -> edge();
    };
  }

  /** Chrome options. Override to add project-specific arguments. */
  public ChromeOptions chrome() {
    ChromeOptions options = new ChromeOptions();
    applyChromiumArguments(options::addArguments);
    options.setExperimentalOption("prefs", chromiumPreferences());
    // Chrome logs "DevTools listening on..." and a handful of GPU warnings to stderr; excluding the
    // automation switches keeps the console readable without hiding real errors.
    options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
    applyCommon(options);
    return options;
  }

  /** Firefox options. Override to add project-specific preferences. */
  public FirefoxOptions firefox() {
    FirefoxOptions options = new FirefoxOptions();
    if (config.headless()) {
      options.addArguments("-headless");
    }
    Dimension window = config.windowSize();
    options.addArguments("--width=" + window.getWidth(), "--height=" + window.getHeight());
    options.addPreference("browser.download.folderList", 2);
    options.addPreference("browser.download.dir", downloadDirectory());
    options.addPreference("browser.download.useDownloadDir", true);
    options.addPreference("browser.helperApps.neverAsk.saveToDisk",
        "application/pdf,application/octet-stream,text/csv,application/zip");
    options.addPreference("pdfjs.disabled", true);
    options.addPreference("dom.webnotifications.enabled", false);
    applyCommon(options);
    return options;
  }

  /** Edge options. Edge is Chromium-based, so it takes the same arguments as Chrome. */
  public EdgeOptions edge() {
    EdgeOptions options = new EdgeOptions();
    applyChromiumArguments(options::addArguments);
    options.setExperimentalOption("prefs", chromiumPreferences());
    applyCommon(options);
    return options;
  }

  /** Absolute path browsers are told to download into, created if it does not exist. */
  protected String downloadDirectory() {
    Path directory = config.downloadDir().toAbsolutePath();
    try {
      Files.createDirectories(directory);
    } catch (IOException ex) {
      throw new ConfigurationException(
          "Could not create the download directory " + directory, ex);
    }
    return directory.toString();
  }

  private void applyChromiumArguments(ArgumentSink sink) {
    Dimension window = config.windowSize();
    if (config.headless()) {
      // The new headless mode is the same binary as headed Chrome; the old one behaved
      // differently enough that bugs reproduced in one and not the other.
      sink.add("--headless=new");
    }
    sink.add("--window-size=" + window.getWidth() + "," + window.getHeight());
    sink.add("--remote-allow-origins=*");
    sink.add("--disable-notifications");
    sink.add("--disable-popup-blocking");
    sink.add("--disable-search-engine-choice-screen");
    if (RuntimeEnvironment.isCi()) {
      LOG.debug("CI detected ({}); adding container-safe browser flags",
          RuntimeEnvironment.describe());
      sink.add("--no-sandbox");
      sink.add("--disable-dev-shm-usage");
      sink.add("--disable-gpu");
    }
  }

  private Map<String, Object> chromiumPreferences() {
    Map<String, Object> preferences = new HashMap<>();
    preferences.put("download.default_directory", downloadDirectory());
    preferences.put("download.prompt_for_download", false);
    preferences.put("plugins.always_open_pdf_externally", true);
    preferences.put("profile.default_content_setting_values.notifications", 2);
    return preferences;
  }

  private void applyCommon(AbstractDriverOptions<?> options) {
    options.setAcceptInsecureCerts(true);
    // NORMAL, not EAGER: a page that is still fetching sub-resources is a page whose JavaScript may
    // not have wired up its listeners yet, which produces exactly the "clicked but nothing
    // happened" flakiness this framework exists to remove.
    options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
  }

  /** Lets the Chromium argument list be shared between {@link ChromeOptions} and {@link EdgeOptions}. */
  @FunctionalInterface
  private interface ArgumentSink {
    void add(String argument);
  }
}
