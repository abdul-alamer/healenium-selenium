package io.github.abdulalamer.selfhealing.support;

import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.exception.FrameworkTimeoutException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded retry with logging and linear backoff.
 *
 * <p>This is the generalised form of the reference framework's {@code sfRetry(Runnable, String)}
 * helper. Two deliberate changes were made while extracting it:
 *
 * <ul>
 *   <li><b>Attempts and backoff are configuration, not constants.</b> The original hard-coded three
 *       attempts and a 100 ms pause inside the page base class.
 *   <li><b>Only {@link RuntimeException} is retried.</b> The original caught a long list of Selenium
 *       exceptions and then {@code RuntimeException} as well, which meant a genuinely failing
 *       assertion was retried and finally reported as a timeout. Here {@link Error} - and therefore
 *       {@code AssertionError} - propagates on the first attempt, so a real failure fails fast and
 *       keeps its original message.
 * </ul>
 *
 * <p>Retry is for genuine flakiness in the transport: an element that is still animating, a click
 * intercepted by a toast that is fading out. It is not a substitute for a correct wait, and it is
 * never a substitute for fixing a broken locator.
 */
public final class Retry {

  private static final Logger LOG = LoggerFactory.getLogger(Retry.class);

  private Retry() {
  }

  /**
   * Runs an action, retrying on {@link RuntimeException} using the configured attempt count and
   * backoff.
   *
   * @param action      what to do
   * @param description what the action is, used in logs and in the failure message
   */
  public static void run(Runnable action, String description) {
    FrameworkConfig config = FrameworkConfig.get();
    run(action, description, config.retryAttempts(), config.retryBackoff());
  }

  /** Runs an action with an explicit attempt count and backoff. */
  public static void run(Runnable action, String description, int attempts, Duration backoff) {
    get(() -> {
      action.run();
      return null;
    }, description, attempts, backoff);
  }

  /**
   * Evaluates a supplier, retrying on {@link RuntimeException} using the configured attempt count
   * and backoff.
   *
   * @param <T>         the supplied type
   * @param supplier    what to evaluate
   * @param description what the evaluation is, used in logs and in the failure message
   * @return the first successful result
   */
  public static <T> T get(Supplier<T> supplier, String description) {
    FrameworkConfig config = FrameworkConfig.get();
    return get(supplier, description, config.retryAttempts(), config.retryBackoff());
  }

  /**
   * Evaluates a supplier with an explicit attempt count and backoff.
   *
   * @param <T>         the supplied type
   * @param supplier    what to evaluate
   * @param description what the evaluation is, used in logs and in the failure message
   * @param attempts    total attempts, at least one
   * @param backoff     pause between attempts, multiplied by the attempt number
   * @return the first successful result
   * @throws FrameworkTimeoutException when every attempt failed
   */
  public static <T> T get(Supplier<T> supplier, String description, int attempts,
      Duration backoff) {
    int total = Math.max(1, attempts);
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= total; attempt++) {
      try {
        T result = supplier.get();
        if (attempt > 1) {
          LOG.info("[{}] succeeded on attempt {} of {}", description, attempt, total);
        }
        return result;
      } catch (RuntimeException ex) {
        lastFailure = ex;
        if (attempt == total) {
          break;
        }
        Duration pause = backoff.multipliedBy(attempt);
        LOG.warn("[{}] attempt {} of {} failed with {}: {} - retrying in {} ms", description,
            attempt, total, ex.getClass().getSimpleName(), ex.getMessage(), pause.toMillis());
        sleep(pause);
      }
    }
    throw new FrameworkTimeoutException(
        "[" + description + "] failed after " + total + " attempt(s)", lastFailure);
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(Math.max(0L, duration.toMillis()));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new FrameworkTimeoutException("Interrupted while waiting to retry", ex);
    }
  }
}
