package io.github.abdulalamer.selfhealing.driver;

import com.epam.healenium.SelfHealingDriver;
import io.github.abdulalamer.selfhealing.config.BrowserType;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.config.HealingConfig;
import io.github.abdulalamer.selfhealing.healing.HealingLogBridge;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a plain driver in Healenium's {@link SelfHealingDriver} when healing is enabled.
 *
 * <p>The wrapping is the whole integration. Healenium intercepts {@code findElement} and
 * {@code findElements} on the driver it delegates to: on success it stores the element's node tree in
 * the backend, and on failure it asks the selector-imitator to score the stored tree against the
 * current DOM and returns the best candidate above {@code score-cap}. Because every locator in this
 * framework is resolved through {@code PageFactory} bound to this driver, all of them get that
 * treatment - there is nothing to annotate and nothing to remember.
 *
 * <p><b>Ordering.</b> {@link HealingConfig#applySystemProperties()} runs before
 * {@code SelfHealingDriver} is first referenced. Healenium resolves its configuration into a static
 * field when its engine class initialises, and system properties override
 * {@code healenium.properties}, so publishing them afterwards would have no effect. The suite
 * lifecycle listener applies them again at test-run start; applying them twice is harmless and
 * removes the dependency on class-loading order.
 *
 * <p>When healing is disabled the plain driver from {@link DriverFactory} is returned untouched, so
 * {@code -Dheal-enabled=false} gives a genuinely healing-free run rather than a wrapped driver with
 * recovery switched off inside it.
 */
public class SelfHealingDriverFactory extends DriverFactory {

  private static final Logger LOG = LoggerFactory.getLogger(SelfHealingDriverFactory.class);

  public SelfHealingDriverFactory() {
    super();
  }

  public SelfHealingDriverFactory(FrameworkConfig config, DriverOptionsBuilder optionsBuilder) {
    super(config, optionsBuilder);
  }

  @Override
  public WebDriver create(BrowserType browser) {
    HealingConfig healing = config.healing();
    if (!healing.enabled()) {
      LOG.info("Self-healing is disabled ({}=false); using a plain WebDriver",
          HealingConfig.KEY_ENABLED);
      return super.create(browser);
    }

    // Must happen before the SelfHealingDriver class initialises - see the class javadoc.
    healing.applySystemProperties();
    HealingLogBridge.install();

    WebDriver delegate = super.create(browser);
    SelfHealingDriver driver = SelfHealingDriver.create(delegate);
    LOG.info("Self-healing enabled: backend={}, imitator={}, recovery-tries={}, score-cap={}",
        healing.serverUrl(), healing.imitatorUrl(), healing.recoveryTries(), healing.scoreCap());
    return driver;
  }
}
