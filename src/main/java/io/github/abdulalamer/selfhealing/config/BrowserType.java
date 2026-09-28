package io.github.abdulalamer.selfhealing.config;

import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The browsers this framework knows how to build options and a driver for.
 *
 * <p>Kept as a closed enum on purpose: every value here has a matching branch in
 * {@link io.github.abdulalamer.selfhealing.driver.DriverOptionsBuilder} and
 * {@link io.github.abdulalamer.selfhealing.driver.DriverFactory}, so an unsupported value fails at
 * configuration time with a message listing what is supported, instead of failing later with a
 * driver-level error.
 */
public enum BrowserType {

  CHROME,
  FIREFOX,
  EDGE;

  /**
   * Parses a configuration value such as {@code chrome} or {@code Firefox}.
   *
   * @param raw the configured value, case-insensitive
   * @return the matching browser
   * @throws ConfigurationException if the value names no supported browser
   */
  public static BrowserType from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ConfigurationException(
          "No browser configured. Set the 'browser' key to one of " + supported() + ".");
    }
    String normalised = raw.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(browser -> browser.name().equals(normalised))
        .findFirst()
        .orElseThrow(() -> new ConfigurationException(
            "Unsupported browser '" + raw + "'. Supported browsers: " + supported() + "."));
  }

  /** The lower-case name used in configuration files and on the command line. */
  public String configName() {
    return name().toLowerCase(Locale.ROOT);
  }

  private static String supported() {
    return Arrays.stream(values())
        .map(BrowserType::configName)
        .collect(Collectors.joining(", "));
  }
}
