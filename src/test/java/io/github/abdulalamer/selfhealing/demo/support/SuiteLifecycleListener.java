package io.github.abdulalamer.selfhealing.demo.support;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.config.HealingConfig;
import io.github.abdulalamer.selfhealing.healing.HealingLogBridge;
import io.github.abdulalamer.selfhealing.healing.HealingMetrics;
import io.github.abdulalamer.selfhealing.healing.HealingReportCollector;
import io.github.abdulalamer.selfhealing.support.RuntimeEnvironment;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suite-level setup and teardown, registered as a Cucumber plugin.
 *
 * <p>Cucumber has per-scenario hooks but no "run once for the whole suite" hook, and a static flag
 * in a {@code @Before} is a race as soon as scenarios run in parallel. A
 * {@link ConcurrentEventListener} bound to {@code TestRunStarted} and {@code TestRunFinished} is the
 * supported way to get one, and it is what the reference framework used too.
 *
 * <p>What happens here matters for the run as a whole:
 *
 * <ul>
 *   <li>The resolved configuration is logged once, so a failed run can be reproduced from its log
 *       rather than from a guess about which overlay was active.
 *   <li>Healenium's system properties are published before any driver exists, which removes the
 *       dependency on when its engine class happens to load.
 *   <li>The backend is probed. An unreachable backend does not fail the run - most scenarios do not
 *       need it - but it does produce one clear warning instead of a hundred confusing ones.
 *   <li>At the end the healing report is published to the log, the Allure results and a file.
 * </ul>
 */
public class SuiteLifecycleListener implements ConcurrentEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(SuiteLifecycleListener.class);
  private static final int BACKEND_PROBE_TIMEOUT_MILLIS = 2000;

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    publisher.registerHandlerFor(TestRunStarted.class, event -> beforeAll());
    publisher.registerHandlerFor(TestRunFinished.class, event -> afterAll());
  }

  private void beforeAll() {
    HealingMetrics.reset();

    FrameworkConfig config = FrameworkConfig.get();
    HealingConfig healing = config.healing();

    LOG.info("================ Test run starting ================");
    LOG.info("Environment      : {} ({})", config.environment(), RuntimeEnvironment.describe());
    LOG.info("Application      : {}", config.baseUrl());
    LOG.info("Browser          : {} (headless={}, {})", config.browser().configName(),
        config.headless(),
        config.remoteUrl().map(url -> "remote " + url).orElse("local"));
    LOG.info("Timeouts         : implicit={}s, explicit={}s, pageLoad={}s",
        config.timeouts().implicitWait().toSeconds(),
        config.timeouts().explicitWait().toSeconds(),
        config.timeouts().pageLoad().toSeconds());
    LOG.info("Self-healing     : {}", healing.enabled() ? "enabled" : "DISABLED");

    if (healing.enabled()) {
      // Before any SelfHealingDriver exists, so Healenium cannot have cached stale configuration.
      healing.applySystemProperties();
      HealingLogBridge.install();
      LOG.info("Healing backend  : {} (report: {})", healing.serverUrl(), healing.reportUrl());
      LOG.info("Healing tuning   : recovery-tries={}, score-cap={}", healing.recoveryTries(),
          healing.scoreCap());
      probeBackend(healing);
    }
    LOG.info("===================================================");
  }

  private void afterAll() {
    LOG.info("================ Test run finished ================");
    HealingReportCollector.publish();
    LOG.info("===================================================");
  }

  /**
   * Checks that something is listening on the backend port.
   *
   * <p>A TCP connect, not an HTTP request: it answers the only question worth asking at start-up
   * ("is the stack up?") in milliseconds and without assuming anything about the backend's routes.
   */
  private void probeBackend(HealingConfig healing) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(healing.backendHost(), healing.backendPort()),
          BACKEND_PROBE_TIMEOUT_MILLIS);
      LOG.info("Healing backend  : reachable");
    } catch (IOException | RuntimeException ex) {
      LOG.warn("Healing backend at {}:{} is NOT reachable. Locators will not be healed and every "
              + "lookup will pay for a failed call to it. Start the stack with "
              + "'docker compose -f docker/docker-compose.yml up -d', or run with "
              + "-PhealEnabled=false.",
          healing.backendHost(), healing.backendPort());
    }
  }
}
