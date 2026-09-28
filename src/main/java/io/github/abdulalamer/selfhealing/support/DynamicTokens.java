package io.github.abdulalamer.selfhealing.support;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code {{...}}} placeholders in feature files, test data and configuration values.
 *
 * <p>Relative dates are the classic reason a UI suite goes red overnight: someone hard-codes
 * tomorrow's date into a scenario and it expires. The reference framework solved this with a
 * hand-maintained map of several dozen literal keys such as {@code next-1-date-dd/M/yyyy}, which
 * meant every new format needed a code change. This is the same idea expressed as a grammar, so no
 * new format needs new code.
 *
 * <h2>Grammar</h2>
 *
 * <pre>
 *   {{now-&lt;pattern&gt;}}                        {{now-yyyy-MM-dd}}
 *   {{next-&lt;n&gt;-&lt;unit&gt;-&lt;pattern&gt;}}            {{next-1-day-yyyy-MM-dd}}
 *   {{previous-&lt;n&gt;-&lt;unit&gt;-&lt;pattern&gt;}}        {{previous-2-hour-HH:mm}}
 *   {{uuid}}                                  a random UUID
 *   {{timestamp}}                             epoch milliseconds
 *   {{random-&lt;n&gt;}}                            n random upper-case alphanumerics
 * </pre>
 *
 * <p>{@code unit} is one of {@code day}, {@code hour}, {@code minute}, {@code second},
 * {@code week}, {@code month} or {@code year}, singular or plural. {@code pattern} is any
 * {@link DateTimeFormatter} pattern, so it may itself contain {@code -}, {@code /} and {@code :}.
 *
 * <p>Unknown placeholders are left untouched and logged at debug level, so text that legitimately
 * contains braces passes through unharmed instead of failing a test.
 *
 * <p>Applications can register their own tokens once at start-up:
 *
 * <pre>{@code
 * DynamicTokens.register("run-id", () -> System.getenv("BUILD_ID"));
 * }</pre>
 */
public final class DynamicTokens {

  private static final Logger LOG = LoggerFactory.getLogger(DynamicTokens.class);
  private static final Pattern TOKEN = Pattern.compile("\\{\\{([^{}]+)}}");
  private static final Pattern RANDOM = Pattern.compile("random-(\\d+)");
  private static final Pattern OFFSET =
      Pattern.compile("(next|previous)-(\\d+)-([a-zA-Z]+)-(.+)", Pattern.DOTALL);
  private static final Pattern NOW = Pattern.compile("now-(.+)", Pattern.DOTALL);
  private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  private static final Map<String, Supplier<String>> CUSTOM = new ConcurrentHashMap<>();

  private DynamicTokens() {
  }

  /**
   * Registers an application-specific token.
   *
   * @param name  the token name as written between the braces, without them
   * @param value supplier evaluated on every resolution, so the value can change per scenario
   */
  public static void register(String name, Supplier<String> value) {
    CUSTOM.put(Objects.requireNonNull(name, "token name"), Objects.requireNonNull(value, "value"));
    LOG.debug("Registered dynamic token '{}'", name);
  }

  /** Removes a previously registered token. Mainly for tests. */
  public static void unregister(String name) {
    CUSTOM.remove(name);
  }

  /**
   * Replaces every recognised placeholder in {@code text}.
   *
   * @param text the text to resolve, may be null
   * @return the resolved text, or null when {@code text} was null
   */
  public static String resolve(String text) {
    if (text == null || !text.contains("{{")) {
      return text;
    }
    Matcher matcher = TOKEN.matcher(text);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String token = matcher.group(1).trim();
      String replacement = resolveToken(token);
      if (replacement == null) {
        LOG.debug("Unrecognised token '{{{}}}' left as-is", token);
        replacement = matcher.group();
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  /**
   * Resolves a single token.
   *
   * @param token the token body, without the braces
   * @return the replacement, or null when the token is not recognised
   */
  private static String resolveToken(String token) {
    Supplier<String> custom = CUSTOM.get(token);
    if (custom != null) {
      return custom.get();
    }
    if ("uuid".equals(token)) {
      return UUID.randomUUID().toString();
    }
    if ("timestamp".equals(token)) {
      return Long.toString(System.currentTimeMillis());
    }
    Matcher random = RANDOM.matcher(token);
    if (random.matches()) {
      return randomString(Integer.parseInt(random.group(1)));
    }
    Matcher now = NOW.matcher(token);
    if (now.matches()) {
      return format(LocalDateTime.now(), now.group(1));
    }
    Matcher offset = OFFSET.matcher(token);
    if (offset.matches()) {
      long amount = Long.parseLong(offset.group(2));
      long signed = "previous".equals(offset.group(1)) ? -amount : amount;
      LocalDateTime moment = shift(LocalDateTime.now(), signed, offset.group(3));
      return moment == null ? null : format(moment, offset.group(4));
    }
    return null;
  }

  private static LocalDateTime shift(LocalDateTime from, long amount, String unit) {
    String normalised = unit.toLowerCase(Locale.ROOT);
    if (normalised.endsWith("s")) {
      normalised = normalised.substring(0, normalised.length() - 1);
    }
    return switch (normalised) {
      case "second" -> from.plusSeconds(amount);
      case "minute" -> from.plusMinutes(amount);
      case "hour" -> from.plusHours(amount);
      case "day" -> from.plusDays(amount);
      case "week" -> from.plusWeeks(amount);
      case "month" -> from.plusMonths(amount);
      case "year" -> from.plusYears(amount);
      default -> null;
    };
  }

  private static String format(LocalDateTime moment, String pattern) {
    try {
      return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(moment);
    } catch (IllegalArgumentException | DateTimeException ex) {
      LOG.debug("'{}' is not a valid date pattern: {}", pattern, ex.getMessage());
      return null;
    }
  }

  private static String randomString(int length) {
    StringBuilder value = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      value.append(ALPHANUMERIC.charAt(ThreadLocalRandom.current().nextInt(ALPHANUMERIC.length())));
    }
    return value.toString();
  }
}
