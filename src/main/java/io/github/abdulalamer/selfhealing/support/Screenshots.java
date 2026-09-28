package io.github.abdulalamer.selfhealing.support;

import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.qameta.allure.Allure;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures browser evidence and attaches it to the Allure report.
 *
 * <p>Every method here is failure-path code, so none of them may throw. A screenshot that blows up
 * while a test is already failing replaces a useful assertion message with a useless one; the
 * capture is best-effort and logs at warn level when it cannot be taken.
 */
public final class Screenshots {

  private static final Logger LOG = LoggerFactory.getLogger(Screenshots.class);
  private static final String PNG = "image/png";
  private static final String HTML = "text/html";

  private Screenshots() {
  }

  /**
   * Captures the viewport as PNG bytes.
   *
   * @param driver the driver to capture from
   * @return the PNG bytes, or empty when the driver cannot take screenshots or the capture failed
   */
  public static Optional<byte[]> capture(WebDriver driver) {
    if (!(driver instanceof TakesScreenshot camera)) {
      LOG.warn("Driver {} cannot take screenshots", driver == null ? "null"
          : driver.getClass().getSimpleName());
      return Optional.empty();
    }
    try {
      return Optional.of(camera.getScreenshotAs(OutputType.BYTES));
    } catch (RuntimeException ex) {
      LOG.warn("Could not capture a screenshot: {}", ex.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Captures the viewport and attaches it to the current Allure step.
   *
   * @param driver the driver to capture from
   * @param name   attachment name shown in the report
   * @return true when an attachment was added
   */
  public static boolean attach(WebDriver driver, String name) {
    Optional<byte[]> png = capture(driver);
    png.ifPresent(bytes -> attachBytes(name, PNG, "png", bytes));
    return png.isPresent();
  }

  /**
   * Attaches a screenshot only when {@code screenshot-on-failure} is enabled. Called from the failure
   * branch of the scenario hooks.
   *
   * @param driver the driver to capture from
   * @param name   attachment name shown in the report
   */
  public static void attachOnFailure(WebDriver driver, String name) {
    if (!FrameworkConfig.get().screenshotOnFailure()) {
      LOG.debug("screenshot-on-failure is disabled; skipping capture for '{}'", name);
      return;
    }
    attach(driver, name);
  }

  /**
   * Attaches the current DOM. Far more useful than a screenshot when the failure is a locator that
   * no longer matches, because the report then contains the markup that did not match it.
   *
   * @param driver the driver to read the page source from
   * @param name   attachment name shown in the report
   */
  public static void attachPageSource(WebDriver driver, String name) {
    if (driver == null) {
      return;
    }
    try {
      attachBytes(name, HTML, "html", driver.getPageSource().getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException ex) {
      LOG.warn("Could not capture the page source: {}", ex.getMessage());
    }
  }

  /** Attaches arbitrary plain text, for example the self-healing summary. */
  public static void attachText(String name, String content) {
    attachBytes(name, "text/plain", "txt", content.getBytes(StandardCharsets.UTF_8));
  }

  private static void attachBytes(String name, String type, String extension, byte[] body) {
    try {
      Allure.getLifecycle().addAttachment(name, type, extension, body);
    } catch (RuntimeException ex) {
      LOG.warn("Could not attach '{}' to the Allure report: {}", name, ex.getMessage());
    }
  }
}
