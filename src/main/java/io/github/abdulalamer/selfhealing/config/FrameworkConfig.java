package io.github.abdulalamer.selfhealing.config;

import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Layered, vendor-neutral configuration.
 *
 * <p>Four layers, highest precedence first:
 *
 * <ol>
 *   <li><b>System property</b> - {@code -Dbrowser=firefox}, which is what Gradle {@code -P} flags
 *       are forwarded to.
 *   <li><b>Environment variable</b> - the key upper-cased with {@code .} and {@code -} replaced by
 *       {@code _}, so {@code base-url} is read from {@code BASE_URL}.
 *   <li><b>Per-environment overlay</b> - {@code <env>-config.properties} on the classpath, where
 *       {@code <env>} comes from {@code framework.env} / {@code FRAMEWORK_ENV} and defaults to
 *       {@code local}. An overlay may define any subset of keys.
 *   <li><b>Defaults</b> - {@code default-config.properties}, the one file that must be complete.
 * </ol>
 *
 * <p>Values in the two properties layers support substitution:
 *
 * <pre>
 *   remote-url = ${env:SELENIUM_REMOTE_URL}                # environment variable, empty when unset
 *   report-url = ${hlm.server.url}/healenium/report         # another configuration key
 * </pre>
 *
 * <p>Missing required keys and unparseable values raise {@link ConfigurationException} rather than
 * quietly defaulting, so a mis-configured pipeline fails at startup instead of producing results
 * that look real.
 */
public final class FrameworkConfig {

  /** System property / environment variable naming the overlay to apply. */
  public static final String ENVIRONMENT_KEY = "framework.env";

  public static final String KEY_BASE_URL = "base-url";
  public static final String KEY_BROWSER = "browser";
  public static final String KEY_HEADLESS = "headless";
  public static final String KEY_REMOTE_URL = "remote-url";
  public static final String KEY_WINDOW_WIDTH = "selenium-window-width";
  public static final String KEY_WINDOW_HEIGHT = "selenium-window-height";
  public static final String KEY_WINDOW_X = "selenium-window-position-x";
  public static final String KEY_WINDOW_Y = "selenium-window-position-y";
  public static final String KEY_RETRY_ATTEMPTS = "retry-attempts";
  public static final String KEY_RETRY_BACKOFF = "retry-backoff-in-millis";
  public static final String KEY_PARALLEL_THREADS = "parallel-thread-count";
  public static final String KEY_SCREENSHOT_ON_FAILURE = "screenshot-on-failure";
  public static final String KEY_ALLURE_RESULTS_DIR = "allure-results-dir";
  public static final String KEY_DOWNLOAD_DIR = "download-dir";
  public static final String KEY_TMP_DIR = "tmp-dir";

  private static final String DEFAULT_ENVIRONMENT = "local";
  private static final String DEFAULT_CONFIG_FILE = "default-config.properties";
  private static final String OVERLAY_SUFFIX = "-config.properties";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(env:)?([^}]+)}");
  private static final int MAX_INTERPOLATION_DEPTH = 8;
  private static final Logger LOG = LoggerFactory.getLogger(FrameworkConfig.class);

  private static volatile FrameworkConfig instance;

  private final String environment;
  private final Map<String, String> fileValues;

  private FrameworkConfig(String environment, Map<String, String> fileValues) {
    this.environment = environment;
    this.fileValues = Collections.unmodifiableMap(fileValues);
  }

  /** Returns the shared configuration, loading it on first use. */
  public static FrameworkConfig get() {
    FrameworkConfig local = instance;
    if (local == null) {
      synchronized (FrameworkConfig.class) {
        local = instance;
        if (local == null) {
          local = load();
          instance = local;
        }
      }
    }
    return local;
  }

  /**
   * Discards the cached configuration so the next {@link #get()} re-reads every layer. Intended for
   * tests that manipulate system properties; production code never needs it.
   */
  public static void reload() {
    synchronized (FrameworkConfig.class) {
      instance = null;
    }
  }

  /** The active overlay name, for example {@code local} or {@code ci}. */
  public String environment() {
    return environment;
  }

  /** Looks a key up through all four layers. */
  public Optional<String> find(String key) {
    String fromSystem = System.getProperty(key);
    if (isPresent(fromSystem)) {
      return Optional.of(fromSystem.trim());
    }
    String fromEnvironment = System.getenv(toEnvironmentVariable(key));
    if (isPresent(fromEnvironment)) {
      return Optional.of(fromEnvironment.trim());
    }
    String fromFile = fileValues.get(key);
    return isPresent(fromFile) ? Optional.of(fromFile.trim()) : Optional.empty();
  }

  /**
   * Returns a required value.
   *
   * @throws ConfigurationException when no layer defines the key
   */
  public String getString(String key) {
    return find(key).orElseThrow(() -> new ConfigurationException(
        "Required configuration key '" + key + "' is not set. Looked at the system property '"
            + key + "', the environment variable '" + toEnvironmentVariable(key) + "', the '"
            + environment + OVERLAY_SUFFIX + "' overlay and " + DEFAULT_CONFIG_FILE + "."));
  }

  /** Returns a value, or {@code fallback} when no layer defines it. */
  public String getString(String key, String fallback) {
    return find(key).orElse(fallback);
  }

  public int getInt(String key) {
    return parseInt(key, getString(key));
  }

  public int getInt(String key, int fallback) {
    return find(key).map(value -> parseInt(key, value)).orElse(fallback);
  }

  public double getDouble(String key) {
    return parseDouble(key, getString(key));
  }

  public double getDouble(String key, double fallback) {
    return find(key).map(value -> parseDouble(key, value)).orElse(fallback);
  }

  public boolean getBoolean(String key) {
    return parseBoolean(key, getString(key));
  }

  public boolean getBoolean(String key, boolean fallback) {
    return find(key).map(value -> parseBoolean(key, value)).orElse(fallback);
  }

  /** Reads a key expressed in whole seconds as a {@link Duration}. */
  public Duration getSeconds(String key) {
    return Duration.ofSeconds(getInt(key));
  }

  /** Base URL of the application under test, without a trailing slash. */
  public String baseUrl() {
    return trimTrailingSlash(getString(KEY_BASE_URL));
  }

  public BrowserType browser() {
    return BrowserType.from(getString(KEY_BROWSER));
  }

  public boolean headless() {
    return getBoolean(KEY_HEADLESS, true);
  }

  /**
   * Grid or remote WebDriver endpoint. Empty means "start a local browser", which is the default and
   * what Selenium Manager handles without any driver binary management.
   */
  public Optional<URL> remoteUrl() {
    String configured = getString(KEY_REMOTE_URL, "");
    if (configured.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(URI.create(configured).toURL());
    } catch (MalformedURLException | IllegalArgumentException ex) {
      throw new ConfigurationException(
          "'" + KEY_REMOTE_URL + "' is not a valid URL: " + configured, ex);
    }
  }

  public Dimension windowSize() {
    return new Dimension(getInt(KEY_WINDOW_WIDTH, 1366), getInt(KEY_WINDOW_HEIGHT, 768));
  }

  public Point windowPosition() {
    return new Point(getInt(KEY_WINDOW_X, 0), getInt(KEY_WINDOW_Y, 0));
  }

  public TimeoutConfig timeouts() {
    return TimeoutConfig.from(this);
  }

  public HealingConfig healing() {
    return HealingConfig.from(this);
  }

  public int retryAttempts() {
    return getInt(KEY_RETRY_ATTEMPTS, 3);
  }

  public Duration retryBackoff() {
    return Duration.ofMillis(getInt(KEY_RETRY_BACKOFF, 200));
  }

  public int parallelThreadCount() {
    return getInt(KEY_PARALLEL_THREADS, 1);
  }

  public boolean screenshotOnFailure() {
    return getBoolean(KEY_SCREENSHOT_ON_FAILURE, true);
  }

  public Path allureResultsDir() {
    return Path.of(getString(KEY_ALLURE_RESULTS_DIR, "build/allure-results"));
  }

  public Path downloadDir() {
    return Path.of(getString(KEY_DOWNLOAD_DIR, "build/downloads"));
  }

  public Path tmpDir() {
    return Path.of(getString(KEY_TMP_DIR, "build/tmp"));
  }

  /**
   * Every key defined by the properties layers with its effective value, sorted by key. Used to log
   * the resolved configuration once at suite start, which turns "which base URL did that run
   * actually use?" from an argument into a scroll-up.
   */
  public Map<String, String> effectiveValues() {
    Map<String, String> effective = new TreeMap<>();
    fileValues.keySet().forEach(key -> effective.put(key, getString(key, "")));
    return Collections.unmodifiableMap(effective);
  }

  /** Translates a configuration key into its environment variable name. */
  public static String toEnvironmentVariable(String key) {
    return key.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_');
  }

  private static FrameworkConfig load() {
    String environment = resolveEnvironment();
    Map<String, String> defaults = readProperties(DEFAULT_CONFIG_FILE, true);
    Map<String, String> overlay = readProperties(environment + OVERLAY_SUFFIX, false);

    Map<String, String> merged = new LinkedHashMap<>(defaults);
    merged.putAll(overlay);

    Map<String, String> interpolated = new LinkedHashMap<>();
    merged.forEach((key, value) ->
        interpolated.put(key, interpolate(value, merged, new HashSet<>(), 0)));

    LOG.info("Configuration loaded: environment='{}', {} default key(s), {} overlay key(s)",
        environment, defaults.size(), overlay.size());
    return new FrameworkConfig(environment, interpolated);
  }

  private static String resolveEnvironment() {
    String fromSystem = System.getProperty(ENVIRONMENT_KEY);
    if (isPresent(fromSystem)) {
      return fromSystem.trim();
    }
    String fromEnvironment = System.getenv(toEnvironmentVariable(ENVIRONMENT_KEY));
    return isPresent(fromEnvironment) ? fromEnvironment.trim() : DEFAULT_ENVIRONMENT;
  }

  private static Map<String, String> readProperties(String resource, boolean required) {
    ClassLoader loader = Optional.ofNullable(Thread.currentThread().getContextClassLoader())
        .orElseGet(FrameworkConfig.class::getClassLoader);
    try (InputStream stream = loader.getResourceAsStream(resource)) {
      if (stream == null) {
        if (required) {
          throw new ConfigurationException(
              "Required configuration file '" + resource + "' was not found on the classpath.");
        }
        LOG.debug("No optional configuration overlay '{}' on the classpath", resource);
        return Map.of();
      }
      Properties properties = new Properties();
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
      Map<String, String> values = new LinkedHashMap<>();
      properties.stringPropertyNames()
          .forEach(name -> values.put(name, properties.getProperty(name, "").trim()));
      return values;
    } catch (IOException ex) {
      throw new ConfigurationException("Could not read configuration file '" + resource + "'", ex);
    }
  }

  private static String interpolate(String value, Map<String, String> raw, Set<String> visiting,
      int depth) {
    if (value == null || !value.contains("${")) {
      return value;
    }
    if (depth > MAX_INTERPOLATION_DEPTH) {
      throw new ConfigurationException(
          "Configuration substitution nested more than " + MAX_INTERPOLATION_DEPTH
              + " levels deep, which usually means two keys reference each other: " + value);
    }
    Matcher matcher = PLACEHOLDER.matcher(value);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      boolean fromEnvironment = matcher.group(1) != null;
      String name = matcher.group(2).trim();
      String replacement;
      if (fromEnvironment) {
        replacement = Optional.ofNullable(System.getenv(name)).orElse("");
        if (replacement.isEmpty()) {
          LOG.debug("Environment variable '{}' is not set; substituting an empty value", name);
        }
      } else if (visiting.contains(name)) {
        throw new ConfigurationException(
            "Circular configuration reference detected on key '" + name + "'.");
      } else {
        visiting.add(name);
        replacement = interpolate(raw.getOrDefault(name, ""), raw, visiting, depth + 1);
        visiting.remove(name);
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static int parseInt(String key, String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      throw new ConfigurationException(
          "Configuration key '" + key + "' must be a whole number but was '" + value + "'.", ex);
    }
  }

  private static double parseDouble(String key, String value) {
    try {
      return Double.parseDouble(value.trim());
    } catch (NumberFormatException ex) {
      throw new ConfigurationException(
          "Configuration key '" + key + "' must be a number but was '" + value + "'.", ex);
    }
  }

  private static boolean parseBoolean(String key, String value) {
    String normalised = value.trim().toLowerCase(Locale.ROOT);
    return switch (normalised) {
      case "true", "yes", "on", "1" -> true;
      case "false", "no", "off", "0" -> false;
      default -> throw new ConfigurationException("Configuration key '" + key
          + "' must be a boolean (true/false) but was '" + value + "'.");
    };
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }

  private static String trimTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
