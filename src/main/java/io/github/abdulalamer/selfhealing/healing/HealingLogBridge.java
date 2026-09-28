package io.github.abdulalamer.selfhealing.healing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * Turns Healenium's log output into first-class test metadata.
 *
 * <h2>Why a log bridge</h2>
 *
 * <p>Healenium OSS has no listener interface, no event bus and no public API for "a locator was just
 * healed". The only place a recovery is announced in-process is its own SLF4J logger. So this class
 * attaches a capture appender to {@code com.epam.healenium} and feeds matching events into
 * {@link HealingMetrics}, from where they reach the Allure report and the build log.
 *
 * <p>The trade-off is explicit and worth stating, because a reviewer will ask: this couples the
 * summary to Healenium's log text. If Healenium changes its wording, the summary under-reports.
 * Nothing else breaks - no test outcome depends on this class, and the healing itself is entirely
 * unaffected. A silent, slightly stale report was judged better than no visibility at all, and the
 * alternative (querying the backend database directly) would couple us to an internal schema instead,
 * which is worse.
 *
 * <h2>Requirements</h2>
 *
 * <ul>
 *   <li>Logback must be the active SLF4J binding. With any other binding the install is skipped with
 *       a warning.
 *   <li>The {@code com.epam.healenium} logger must be at {@code INFO} or lower. Raising it silently
 *       empties the healing summary, which is why {@code logback.xml} pins it and says so.
 * </ul>
 */
public final class HealingLogBridge {

  /** Logger Healenium reports through. */
  public static final String HEALENIUM_LOGGER = "com.epam.healenium";

  private static final String APPENDER_NAME = "healenium-capture";

  /**
   * Matches {@code heal}, {@code healed} and {@code healing} but not {@code healenium}, so start-up
   * banners naming the product are not mistaken for recoveries.
   */
  private static final Pattern HEALING_MESSAGE = Pattern.compile("(?i)heal(?!enium)");

  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(HealingLogBridge.class);

  private HealingLogBridge() {
  }

  /**
   * Attaches the capture appender. Idempotent and safe to call from every driver creation; only the
   * first call does anything.
   */
  public static void install() {
    if (!INSTALLED.compareAndSet(false, true)) {
      return;
    }
    try {
      ILoggerFactory factory = LoggerFactory.getILoggerFactory();
      if (!(factory instanceof LoggerContext context)) {
        LOG.warn("Logback is not the active SLF4J binding ({}), so healing events cannot be "
                + "captured. Self-healing still works; only the Allure healing summary will be empty.",
            factory.getClass().getName());
        INSTALLED.set(false);
        return;
      }
      ch.qos.logback.classic.Logger healeniumLogger = context.getLogger(HEALENIUM_LOGGER);
      if (healeniumLogger.getAppender(APPENDER_NAME) != null) {
        return;
      }
      if (!healeniumLogger.isEnabledFor(Level.INFO)) {
        LOG.warn("Logger '{}' is above INFO, so healing events will not be observed. Lower it in "
            + "logback.xml to populate the healing summary.", HEALENIUM_LOGGER);
      }
      CaptureAppender appender = new CaptureAppender();
      appender.setName(APPENDER_NAME);
      appender.setContext(context);
      appender.start();
      healeniumLogger.addAppender(appender);
      LOG.debug("Healing log bridge installed on logger '{}'", HEALENIUM_LOGGER);
    } catch (RuntimeException | LinkageError ex) {
      // Never let reporting instrumentation break a test run.
      INSTALLED.set(false);
      LOG.warn("Could not install the healing log bridge: {}", ex.toString());
    }
  }

  /** Whether the appender is currently attached. */
  public static boolean isInstalled() {
    return INSTALLED.get();
  }

  /** Detaches the capture appender. Mainly for tests. */
  public static void uninstall() {
    if (!INSTALLED.compareAndSet(true, false)) {
      return;
    }
    try {
      if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
        context.getLogger(HEALENIUM_LOGGER).detachAppender(APPENDER_NAME);
      }
    } catch (RuntimeException | LinkageError ex) {
      LOG.debug("Could not detach the healing log bridge: {}", ex.toString());
    }
  }

  /** Forwards healing-related log events to {@link HealingMetrics}. */
  private static final class CaptureAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
      String message = event.getFormattedMessage();
      if (message != null && HEALING_MESSAGE.matcher(message).find()) {
        HealingMetrics.record(message.strip());
      }
    }
  }
}
