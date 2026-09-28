package io.github.abdulalamer.selfhealing.demo.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Data shared between step definitions within one scenario.
 *
 * <p>Cucumber constructs a fresh instance of every glue class per scenario, so two step classes
 * cannot see each other's fields. The usual answer is a dependency-injection module - the framework
 * this was extracted from used Guice with a {@code @Singleton} scenario context. This does the same
 * job with a thread-local map and no container: one fewer dependency, one fewer thing to configure,
 * and no {@code @Inject} ceremony in a codebase whose only shared state is a handful of values.
 *
 * <p>Thread-local rather than static, so parallel scenarios cannot read each other's data. The hooks
 * call {@link #clear()} after every scenario; without that, a pooled thread would hand leftovers to
 * the next scenario and produce a failure that only reproduces when the suite runs in a particular
 * order.
 *
 * <p>If your project already runs Guice or PicoContainer, delete this and inject a scenario-scoped
 * object instead - the rest of the framework does not depend on it.
 */
public final class ScenarioContext {

  private static final ThreadLocal<ScenarioContext> CURRENT =
      ThreadLocal.withInitial(ScenarioContext::new);

  private final Map<String, Object> values = new HashMap<>();

  private ScenarioContext() {
  }

  /** The context belonging to the scenario running on this thread. */
  public static ScenarioContext current() {
    return CURRENT.get();
  }

  /** Discards this thread's context. Called from the {@code @After} hook. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Stores a value. */
  public void put(String key, Object value) {
    values.put(key, value);
  }

  /** Whether a key has been stored. */
  public boolean has(String key) {
    return values.containsKey(key);
  }

  /** A stored value, if present. */
  public Optional<Object> find(String key) {
    return Optional.ofNullable(values.get(key));
  }

  /**
   * A stored value with its type checked.
   *
   * @param <T>  the expected type
   * @param key  what it was stored under
   * @param type the expected type
   * @return the value
   * @throws IllegalStateException when nothing was stored under that key, which is nearly always a
   *     missing Given step rather than a genuine bug in the code under test
   */
  public <T> T get(String key, Class<T> type) {
    Object value = values.get(key);
    if (value == null) {
      throw new IllegalStateException("Nothing was stored under '" + key
          + "'. A previous step in this scenario was expected to put it there. Stored keys: "
          + values.keySet());
    }
    if (!type.isInstance(value)) {
      throw new IllegalStateException("'" + key + "' holds a " + value.getClass().getSimpleName()
          + ", not a " + type.getSimpleName());
    }
    return type.cast(value);
  }
}
