package io.github.abdulalamer.selfhealing.demo.support;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.driver.DriverManager;
import io.github.abdulalamer.selfhealing.driver.SelfHealingDriverFactory;
import io.github.abdulalamer.selfhealing.healing.HealingMetrics;
import io.github.abdulalamer.selfhealing.healing.HealingReportCollector;
import io.github.abdulalamer.selfhealing.support.Screenshots;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-scenario lifecycle: start a browser, capture evidence, clean up.
 *
 * <p>Hook ordering is explicit rather than left to the default, because the order is load-bearing.
 * Cucumber runs {@code @Before} hooks in ascending order and {@code @After} hooks in descending
 * order, so with the values below a scenario runs:
 *
 * <pre>
 *   before: startBrowser (10)
 *   after : cleanDownloads (30) -> captureEvidence (20) -> quitBrowser (10)
 * </pre>
 *
 * <p>Evidence has to be captured before the browser is quit - a screenshot of a closed browser is
 * an exception, not a screenshot - and that single constraint is the reason the numbers are written
 * down instead of inherited.
 */
public class Hooks {

  private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);

  @Before(order = 10)
  public void startBrowser(Scenario scenario) {
    HealingMetrics.bindScenario(scenario.getName());
    DriverManager.set(new SelfHealingDriverFactory().create());
    LOG.info("--- Scenario start: {}", scenario.getName());
  }

  /**
   * Attaches whatever will make a failure diagnosable: a screenshot, the DOM that the failing
   * locator did not match, and the healing activity for this scenario.
   */
  @After(order = 20)
  public void captureEvidence(Scenario scenario) {
    if (!DriverManager.isSet()) {
      return;
    }
    if (scenario.isFailed()) {
      Screenshots.attachOnFailure(DriverManager.get(), "Screenshot on failure");
      // The page source matters more than the screenshot when the failure is a locator that no
      // longer matches: the report then contains the markup it was matched against.
      Screenshots.attachPageSource(DriverManager.get(), "Page source on failure");
    }
    HealingReportCollector.attachScenarioSummary(scenario.getName());
  }

  /**
   * Empties the configured download directory for scenarios tagged {@code @CleanDownloadFolder}.
   *
   * <p>A tag-scoped hook, carried over from the reference framework: downloads are slow to clean and
   * only a handful of scenarios produce any, so paying for it everywhere is waste.
   */
  @After(value = "@CleanDownloadFolder", order = 30)
  public void cleanDownloadFolder() {
    Path downloads = FrameworkConfig.get().downloadDir();
    if (!Files.isDirectory(downloads)) {
      return;
    }
    try (Stream<Path> entries = Files.walk(downloads)) {
      entries.sorted(Comparator.reverseOrder())
          .filter(path -> !path.equals(downloads))
          .forEach(Hooks::deleteQuietly);
      LOG.debug("Cleaned the download directory {}", downloads.toAbsolutePath());
    } catch (IOException ex) {
      LOG.warn("Could not clean the download directory {}: {}", downloads, ex.getMessage());
    }
  }

  @After(order = 10)
  public void quitBrowser(Scenario scenario) {
    DriverManager.quit();
    HealingMetrics.unbindScenario();
    ScenarioContext.clear();
    LOG.info("--- Scenario end: {} [{}]", scenario.getName(), scenario.getStatus());
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ex) {
      LOG.debug("Could not delete {}: {}", path, ex.getMessage());
    }
  }
}
