package io.github.abdulalamer.selfhealing.exception;

/**
 * Thrown when a wait or a retry gives up.
 *
 * <p>The framework wraps the timeout exceptions of its underlying libraries (Awaitility's
 * {@code ConditionTimeoutException}, Selenium's {@code TimeoutException}) in this single type so
 * that step definitions and reports have one recognisable "we waited and it never happened"
 * failure, carrying the human-readable description of what was being waited for.
 */
public class FrameworkTimeoutException extends RuntimeException {

  public FrameworkTimeoutException(String message) {
    super(message);
  }

  public FrameworkTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
