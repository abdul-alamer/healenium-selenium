package io.github.abdulalamer.selfhealing.config;

import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import java.time.Duration;

/**
 * The four Selenium timeouts, resolved once and passed around as a value object.
 *
 * <p>Timeouts are the single most copy-pasted magic number in a UI suite. Binding them to
 * configuration keys in one place means a slow environment is a one-line override rather than a
 * find-and-replace across page objects.
 *
 * @param implicitWait driver-level implicit wait applied to every {@code findElement}
 * @param explicitWait default ceiling for {@code WebDriverWait} and framework waits
 * @param pageLoad     ceiling for a navigation to finish
 * @param script       ceiling for an asynchronous script to call back
 */
public record TimeoutConfig(
    Duration implicitWait,
    Duration explicitWait,
    Duration pageLoad,
    Duration script) {

  public static final String KEY_IMPLICIT = "selenium-implicit-wait-timeout-in-seconds";
  public static final String KEY_EXPLICIT = "selenium-web-driver-wait-timeout-in-seconds";
  public static final String KEY_PAGE_LOAD = "selenium-page-load-timeout-in-seconds";
  public static final String KEY_SCRIPT = "selenium-script-timeout-in-seconds";

  public TimeoutConfig {
    requireNonNegative(implicitWait, KEY_IMPLICIT);
    requirePositive(explicitWait, KEY_EXPLICIT);
    requirePositive(pageLoad, KEY_PAGE_LOAD);
    requirePositive(script, KEY_SCRIPT);
  }

  /** Reads all four timeouts from the configuration stack. */
  public static TimeoutConfig from(FrameworkConfig config) {
    return new TimeoutConfig(
        Duration.ofSeconds(config.getInt(KEY_IMPLICIT)),
        Duration.ofSeconds(config.getInt(KEY_EXPLICIT)),
        Duration.ofSeconds(config.getInt(KEY_PAGE_LOAD)),
        Duration.ofSeconds(config.getInt(KEY_SCRIPT)));
  }

  private static void requireNonNegative(Duration value, String key) {
    if (value == null || value.isNegative()) {
      throw new ConfigurationException("Timeout '" + key + "' must not be negative.");
    }
  }

  private static void requirePositive(Duration value, String key) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new ConfigurationException("Timeout '" + key + "' must be greater than zero.");
    }
  }
}
