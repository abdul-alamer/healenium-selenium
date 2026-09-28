package io.github.abdulalamer.selfhealing.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Detects whether the suite is running on a developer machine or inside a CI runner.
 *
 * <p>The distinction drives real behaviour: locally you want a visible browser and a slow, readable
 * log; on CI you want headless, extra Chrome sandbox flags, and artefacts written where the runner
 * can upload them.
 *
 * <p>Detection is by the presence of well-known CI environment variables. That is the same technique
 * the reference framework used - it checked for its build server's build-key variable - generalised
 * so it is not tied to one vendor's CI product.
 */
public final class RuntimeEnvironment {

  /**
   * Environment variables that, when present and non-blank, mean "this is a CI runner". Ordered so
   * that the most specific vendor wins when several are set.
   */
  private static final Map<String, String> CI_MARKERS = ciMarkers();

  private RuntimeEnvironment() {
  }

  /** True when any known CI marker variable is set. */
  public static boolean isCi() {
    return ciName().isPresent();
  }

  /** True when no CI marker is set, i.e. a developer machine. */
  public static boolean isRunningLocally() {
    return !isCi();
  }

  /** A human-readable name for the detected CI system, empty when running locally. */
  public static Optional<String> ciName() {
    return CI_MARKERS.entrySet().stream()
        .filter(entry -> isSet(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  /** A one-line description for the suite banner, e.g. {@code CI (GitHub Actions)}. */
  public static String describe() {
    return ciName().map(name -> "CI (" + name + ")").orElse("local machine");
  }

  private static boolean isSet(String variable) {
    String value = System.getenv(variable);
    return value != null && !value.isBlank();
  }

  private static Map<String, String> ciMarkers() {
    Map<String, String> markers = new LinkedHashMap<>();
    markers.put("GITHUB_ACTIONS", "GitHub Actions");
    markers.put("GITLAB_CI", "GitLab CI");
    markers.put("CIRCLECI", "CircleCI");
    markers.put("TF_BUILD", "Azure Pipelines");
    markers.put("TEAMCITY_VERSION", "TeamCity");
    markers.put("JENKINS_URL", "Jenkins");
    markers.put("BUILD_NUMBER", "a build server");
    // Generic, checked last: almost every CI product sets CI=true.
    markers.put("CI", "an unidentified CI runner");
    // LinkedHashMap, not Map.copyOf: iteration order is load-bearing here.
    return Collections.unmodifiableMap(markers);
  }
}
