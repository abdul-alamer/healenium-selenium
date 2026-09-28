package io.github.abdulalamer.selfhealing.healing;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory record of the self-healing activity of a test run.
 *
 * <p>Healing is only valuable if it is visible. A suite that quietly repairs twenty locators a night
 * and never says so has converted a maintenance problem into a hidden one. These metrics exist so
 * that every healed locator ends up in the Allure report and in the build log, where somebody has to
 * look at it and decide whether to fix the page object.
 *
 * <p>Events arrive from {@link HealingLogBridge}. Healenium OSS does not expose a programmatic
 * healing callback, so the bridge listens to its logger instead; see that class for the trade-off.
 * One recovery can produce more than one log line, so {@link #eventCount()} is deliberately named
 * after log events rather than pretending to be an exact count of healed locators.
 *
 * <p>Thread-safe: parallel scenarios record into the same list.
 */
public final class HealingMetrics {

  /**
   * A single healing log event.
   *
   * @param at       when it was observed
   * @param thread   the scenario thread that observed it
   * @param scenario the scenario that was running, or {@code unknown}
   * @param message  the formatted log message from Healenium
   */
  public record HealingEvent(Instant at, String thread, String scenario, String message) {

    private static final DateTimeFormatter TIME =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** One report-friendly line. */
    public String describe() {
      return TIME.format(at) + "  [" + scenario + "]  " + message;
    }
  }

  private static final String UNKNOWN_SCENARIO = "unknown";
  private static final List<HealingEvent> EVENTS = new CopyOnWriteArrayList<>();
  private static final ThreadLocal<String> CURRENT_SCENARIO = new ThreadLocal<>();

  private HealingMetrics() {
  }

  /**
   * Names the scenario running on this thread so healing events can be attributed to it. Called from
   * the {@code @Before} hook.
   */
  public static void bindScenario(String name) {
    CURRENT_SCENARIO.set(name == null || name.isBlank() ? UNKNOWN_SCENARIO : name);
  }

  /** Clears the scenario name for this thread. Called from the {@code @After} hook. */
  public static void unbindScenario() {
    CURRENT_SCENARIO.remove();
  }

  /** Records one healing log event. */
  public static void record(String message) {
    EVENTS.add(new HealingEvent(Instant.now(), Thread.currentThread().getName(),
        Optional.ofNullable(CURRENT_SCENARIO.get()).orElse(UNKNOWN_SCENARIO), message));
  }

  /** Every event observed so far, oldest first. */
  public static List<HealingEvent> events() {
    return List.copyOf(EVENTS);
  }

  /** Events attributed to a single scenario. */
  public static List<HealingEvent> eventsFor(String scenario) {
    return EVENTS.stream()
        .filter(event -> event.scenario().equals(scenario))
        .collect(Collectors.toUnmodifiableList());
  }

  /** The scenario currently bound to this thread, or {@code unknown}. */
  public static String currentScenario() {
    return Optional.ofNullable(CURRENT_SCENARIO.get()).orElse(UNKNOWN_SCENARIO);
  }

  /** Whether the scenario running on this thread has had a locator healed. */
  public static boolean hasHealingInCurrentScenario() {
    return !eventsFor(currentScenario()).isEmpty();
  }

  /** How many healing log events were observed. */
  public static int eventCount() {
    return EVENTS.size();
  }

  /** Whether any healing happened at all during this run. */
  public static boolean hasHealing() {
    return !EVENTS.isEmpty();
  }

  /** Forgets every event. Called once at test-run start. */
  public static void reset() {
    EVENTS.clear();
  }

  /**
   * A plain-text summary suitable for the build log, an Allure attachment or a file.
   *
   * @return a multi-line summary, never empty
   */
  public static String summary() {
    if (EVENTS.isEmpty()) {
      return """
          Self-healing summary
          ====================
          No locators were healed during this run.

          That is the outcome you want: every locator matched the DOM on the first attempt.
          """;
    }
    String scenarios = EVENTS.stream()
        .map(HealingEvent::scenario)
        .distinct()
        .sorted()
        .collect(Collectors.joining(", "));
    String lines = EVENTS.stream()
        .map(HealingEvent::describe)
        .collect(Collectors.joining(System.lineSeparator()));
    return "Self-healing summary" + System.lineSeparator()
        + "====================" + System.lineSeparator()
        + "Healing log events : " + EVENTS.size() + System.lineSeparator()
        + "Scenarios affected : " + scenarios + System.lineSeparator()
        + System.lineSeparator()
        + "A single recovery can emit more than one log line, so treat the count as a signal "
        + "rather than an exact tally of healed locators." + System.lineSeparator()
        + System.lineSeparator()
        + "Every line below is technical debt. Healing kept the run green; it did not fix the "
        + "page object. Update the locator and the debt goes away." + System.lineSeparator()
        + System.lineSeparator()
        + lines + System.lineSeparator();
  }
}
