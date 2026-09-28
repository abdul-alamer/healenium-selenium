package io.github.abdulalamer.selfhealing.config;

import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Self-healing settings, and the bridge that hands them to Healenium.
 *
 * <p>The five Healenium keys ({@value #KEY_ENABLED}, {@value #KEY_SERVER_URL},
 * {@value #KEY_IMITATOR_URL}, {@value #KEY_RECOVERY_TRIES}, {@value #KEY_SCORE_CAP}) are spelled in
 * this framework's configuration files exactly as Healenium spells them in
 * {@code healenium.properties}. That is a deliberate choice: {@link #applySystemProperties()} can
 * then forward them verbatim, so there is no translation table to drift out of date and anything
 * documented for Healenium works here unchanged.
 *
 * <p><b>Ordering matters.</b> Healenium resolves its configuration into a {@code static} field when
 * its engine class is first loaded, and system properties override the properties file. So
 * {@link #applySystemProperties()} must run <em>before</em> the first
 * {@code SelfHealingDriver.create(...)} call in the JVM. {@link
 * io.github.abdulalamer.selfhealing.driver.SelfHealingDriverFactory} guarantees that ordering.
 *
 * @param enabled       whether locator recovery is attempted at all
 * @param serverUrl     Healenium backend base URL, e.g. {@code http://localhost:7878}
 * @param imitatorUrl   selector-imitator base URL, e.g. {@code http://localhost:8000}
 * @param recoveryTries how many stored candidate node trees to try per broken locator
 * @param scoreCap      minimum similarity score, 0.0 to 1.0, for a candidate to be accepted
 * @param reportDir     local directory scanned for healing report artefacts
 */
public record HealingConfig(
    boolean enabled,
    String serverUrl,
    String imitatorUrl,
    int recoveryTries,
    double scoreCap,
    Path reportDir) {

  public static final String KEY_ENABLED = "heal-enabled";
  public static final String KEY_SERVER_URL = "hlm.server.url";
  public static final String KEY_IMITATOR_URL = "hlm.imitator.url";
  public static final String KEY_RECOVERY_TRIES = "recovery-tries";
  public static final String KEY_SCORE_CAP = "score-cap";
  public static final String KEY_REPORT_DIR = "healing-report-dir";

  /** Path the Healenium backend serves its healing report from. */
  public static final String REPORT_PATH = "/healenium/report";

  private static final Logger LOG = LoggerFactory.getLogger(HealingConfig.class);
  private static final int DEFAULT_BACKEND_PORT = 7878;

  public HealingConfig {
    if (recoveryTries < 1) {
      throw new ConfigurationException("'" + KEY_RECOVERY_TRIES + "' must be at least 1.");
    }
    if (scoreCap < 0.0d || scoreCap > 1.0d) {
      throw new ConfigurationException("'" + KEY_SCORE_CAP + "' must be between 0.0 and 1.0.");
    }
  }

  /** Reads the healing settings from the configuration stack. */
  public static HealingConfig from(FrameworkConfig config) {
    return new HealingConfig(
        config.getBoolean(KEY_ENABLED, false),
        config.getString(KEY_SERVER_URL, "http://localhost:" + DEFAULT_BACKEND_PORT),
        config.getString(KEY_IMITATOR_URL, "http://localhost:8000"),
        config.getInt(KEY_RECOVERY_TRIES, 1),
        config.getDouble(KEY_SCORE_CAP, 0.5d),
        Path.of(config.getString(KEY_REPORT_DIR, "build/healenium/report")));
  }

  /**
   * Publishes these settings as JVM system properties, which is how Healenium picks up
   * configuration that does not live in {@code healenium.properties}.
   *
   * <p>An existing system property is never overwritten, so an explicit {@code -Dheal-enabled=false}
   * on the command line always wins - that is the documented way to switch healing off for a
   * release-gating pipeline.
   */
  public void applySystemProperties() {
    setIfAbsent(KEY_ENABLED, Boolean.toString(enabled));
    setIfAbsent(KEY_SERVER_URL, serverUrl);
    setIfAbsent(KEY_IMITATOR_URL, imitatorUrl);
    setIfAbsent(KEY_RECOVERY_TRIES, Integer.toString(recoveryTries));
    setIfAbsent(KEY_SCORE_CAP, Double.toString(scoreCap));
    LOG.debug("Healenium system properties applied: enabled={}, server={}, imitator={}, "
        + "recovery-tries={}, score-cap={}", enabled, serverUrl, imitatorUrl, recoveryTries,
        scoreCap);
  }

  /** URL of the healing report UI served by the backend. */
  public String reportUrl() {
    return trimTrailingSlash(serverUrl) + REPORT_PATH;
  }

  /** Backend host, for a pre-flight reachability check. */
  public String backendHost() {
    return backendUri().getHost();
  }

  /** Backend port, defaulting to {@value #DEFAULT_BACKEND_PORT} when the URL omits one. */
  public int backendPort() {
    int port = backendUri().getPort();
    return port == -1 ? DEFAULT_BACKEND_PORT : port;
  }

  private URI backendUri() {
    try {
      return new URI(serverUrl);
    } catch (URISyntaxException ex) {
      throw new ConfigurationException(
          "'" + KEY_SERVER_URL + "' is not a valid URL: " + serverUrl, ex);
    }
  }

  private static void setIfAbsent(String key, String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
