package io.github.abdulalamer.selfhealing.healing;

import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.config.HealingConfig;
import io.github.abdulalamer.selfhealing.support.Screenshots;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes what self-healing did during a run.
 *
 * <p>Healing evidence lives in three places, and this class gathers all three so a reader does not
 * have to know that:
 *
 * <ol>
 *   <li><b>The Allure report</b> - the {@link HealingMetrics} summary is attached as text, so healed
 *       locators show up next to the scenario that healed them.
 *   <li><b>A file in the build directory</b> - {@code build/healenium/summary.txt}, so CI can upload
 *       it as an artefact without parsing the Allure results.
 *   <li><b>The Healenium backend</b> - the backend renders its own report, including before/after
 *       screenshots of the healed element, at {@link HealingConfig#reportUrl()}. That report is the
 *       authoritative one; this class links to it and additionally attaches anything found in the
 *       configured local report directory.
 * </ol>
 *
 * <p>Nothing here is allowed to fail a run. Report collection happens in {@code @After} and at
 * test-run finish, where an exception would replace a real result with a reporting bug.
 */
public final class HealingReportCollector {

  private static final Logger LOG = LoggerFactory.getLogger(HealingReportCollector.class);
  private static final String SUMMARY_ATTACHMENT = "Self-healing summary";
  private static final String SUMMARY_FILE = "summary.txt";
  private static final int MAX_ATTACHED_FILES = 20;
  private static final long MAX_ATTACHED_BYTES = 5L * 1024 * 1024;

  private HealingReportCollector() {
  }

  /**
   * Attaches the healing summary to the Allure report for the scenario that is finishing. Call from
   * the {@code @After} hook.
   */
  public static void attachSummaryToAllure() {
    Screenshots.attachText(SUMMARY_ATTACHMENT, HealingMetrics.summary());
  }

  /**
   * Attaches the healing activity of one scenario, and nothing when that scenario healed nothing.
   *
   * <p>Per-scenario rather than global on purpose: attaching the whole run's summary to every
   * scenario buries the one report that matters under fifty identical copies.
   *
   * @param scenarioName the scenario to report on, as bound by the {@code @Before} hook
   */
  public static void attachScenarioSummary(String scenarioName) {
    List<HealingMetrics.HealingEvent> events = HealingMetrics.eventsFor(scenarioName);
    if (events.isEmpty()) {
      return;
    }
    String text = "Locators healed during this scenario: " + events.size()
        + System.lineSeparator()
        + "These are technical debt: healing kept the scenario green, the page object is still "
        + "out of date." + System.lineSeparator()
        + "Backend report (before/after screenshots): " + backendReportUrl()
        + System.lineSeparator() + System.lineSeparator()
        + events.stream()
            .map(HealingMetrics.HealingEvent::describe)
            .collect(Collectors.joining(System.lineSeparator()))
        + System.lineSeparator();
    Screenshots.attachText(SUMMARY_ATTACHMENT, text);
  }

  /**
   * Writes the summary to disk, attaches any local healing artefacts and logs where to look. Call
   * once from the test-run finish listener.
   *
   * @return the summary file, or empty when it could not be written
   */
  public static Optional<Path> publish() {
    HealingConfig healing = FrameworkConfig.get().healing();
    logSummary(healing);
    attachLocalArtefacts(healing.reportDir());
    return writeSummaryFile(healing.reportDir().resolveSibling(SUMMARY_FILE));
  }

  /** URL of the backend's healing report, which carries the before/after screenshots. */
  public static String backendReportUrl() {
    return FrameworkConfig.get().healing().reportUrl();
  }

  /**
   * Writes the plain-text summary.
   *
   * @param target file to write, parent directories are created
   * @return the written path, or empty on failure
   */
  public static Optional<Path> writeSummaryFile(Path target) {
    try {
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, HealingMetrics.summary(), StandardCharsets.UTF_8);
      LOG.info("Self-healing summary written to {}", target.toAbsolutePath());
      return Optional.of(target);
    } catch (IOException ex) {
      LOG.warn("Could not write the self-healing summary to {}: {}", target, ex.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Attaches every file found under the configured healing report directory, if it exists. The
   * Healenium backend can be configured to write screenshots of healed elements to a mounted volume;
   * point {@code healing-report-dir} at it and those images land in the Allure report too.
   *
   * @param reportDir directory to scan, missing directories are ignored
   */
  public static void attachLocalArtefacts(Path reportDir) {
    if (reportDir == null || !Files.isDirectory(reportDir)) {
      LOG.debug("No local healing report directory at {}", reportDir);
      return;
    }
    try (Stream<Path> files = Files.walk(reportDir)) {
      List<Path> attachable = files
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparing(Path::toString))
          .limit(MAX_ATTACHED_FILES)
          .toList();
      for (Path file : attachable) {
        attach(file);
      }
      if (!attachable.isEmpty()) {
        LOG.info("Attached {} healing artefact(s) from {}", attachable.size(), reportDir);
      }
    } catch (IOException ex) {
      LOG.warn("Could not read the healing report directory {}: {}", reportDir, ex.getMessage());
    }
  }

  private static void attach(Path file) {
    try {
      if (Files.size(file) > MAX_ATTACHED_BYTES) {
        LOG.debug("Skipping healing artefact {} because it exceeds {} bytes", file,
            MAX_ATTACHED_BYTES);
        return;
      }
      String name = file.getFileName().toString();
      byte[] body = Files.readAllBytes(file);
      io.qameta.allure.Allure.getLifecycle()
          .addAttachment(name, contentType(name), extension(name), body);
    } catch (IOException | RuntimeException ex) {
      LOG.debug("Could not attach healing artefact {}: {}", file, ex.toString());
    }
  }

  private static void logSummary(HealingConfig healing) {
    if (!healing.enabled()) {
      LOG.info("Self-healing was disabled for this run; no healing report to collect.");
      return;
    }
    LOG.info("Self-healing: {} log event(s) captured. Backend report: {}",
        HealingMetrics.eventCount(), healing.reportUrl());
    if (HealingMetrics.hasHealing()) {
      LOG.warn("Locators were healed during this run. Healing kept the suite green but the page "
          + "objects are now out of date - see the summary and fix them.");
    }
  }

  private static String contentType(String fileName) {
    String lower = fileName.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".html") || lower.endsWith(".htm")) {
      return "text/html";
    }
    if (lower.endsWith(".json")) {
      return "application/json";
    }
    return "text/plain";
  }

  private static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "txt" : fileName.substring(dot + 1);
  }
}
