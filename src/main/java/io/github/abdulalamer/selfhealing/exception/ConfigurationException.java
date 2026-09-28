package io.github.abdulalamer.selfhealing.exception;

/**
 * Thrown when the framework cannot be configured: a required key is missing from every
 * configuration layer, a value cannot be coerced to the expected type, or a value names something
 * the framework does not support (an unknown browser, a malformed URL).
 *
 * <p>Configuration problems are deliberately fatal and fail fast. A suite that silently falls back
 * to a default browser or a default base URL produces results nobody can interpret.
 */
public class ConfigurationException extends RuntimeException {

  public ConfigurationException(String message) {
    super(message);
  }

  public ConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
