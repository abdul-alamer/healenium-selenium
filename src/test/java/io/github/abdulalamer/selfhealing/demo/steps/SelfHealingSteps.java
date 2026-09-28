package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.abdulalamer.selfhealing.config.FrameworkConfig;
import io.github.abdulalamer.selfhealing.demo.pages.DynamicControlsPage;
import io.github.abdulalamer.selfhealing.healing.HealingMetrics;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The self-healing demonstration: train a locator, break the page under it, watch it recover.
 *
 * <p>See {@link DynamicControlsPage} for why the demonstration changes the page rather than the
 * locator, and {@code docs/SELF_HEALING.md} for what Healenium is doing between the two lookups.
 *
 * <p>These steps require the Healenium backend. Without it, the "learned" lookup has nowhere to
 * store the node tree and the healed lookup has nothing to score against, so the scenario fails -
 * correctly, because a healing suite with no backend is not healing. Start the stack with
 * {@code docker compose -f docker/docker-compose.yml up -d}, or exclude the tag with
 * {@code -Ptags="not @self-healing"}.
 */
public class SelfHealingSteps extends AbstractSteps {

  private static final Logger LOG = LoggerFactory.getLogger(SelfHealingSteps.class);
  private static final String LABEL_BEFORE_REFACTOR = "swap-control-label-before";

  /**
   * How long to let the backend persist a node tree before the page is broken.
   *
   * <p>A frank compromise. Healenium reports nothing when a node tree has finished being stored, so
   * there is no signal to wait on - only elapsed time. In a real pipeline this problem does not
   * exist, because training happened on the previous green run, hours earlier; it exists only
   * because this demonstration compresses train-and-heal into a single scenario.
   */
  private static final long PERSIST_SETTLE_MILLIS = 1500L;

  @Given("the swap control has been located once, so its node tree is stored")
  public void theSwapControlHasBeenLocatedOnce() {
    assertThat(FrameworkConfig.get().healing().enabled())
        .as("self-healing must be enabled for this scenario; it is switched off by "
            + "heal-enabled=false")
        .isTrue();

    String label = page().getSwapControlLabel();
    assertThat(label).as("the swap control's label before the refactor").isNotBlank();
    context().put(LABEL_BEFORE_REFACTOR, label);
    LOG.info("Trained the locator for the swap control; it currently reads '{}'", label);

    allowBackendToPersist();
  }

  @When("the page is refactored so the learned locator no longer matches")
  public void thePageIsRefactored() {
    String newId = page().simulateFrontEndRefactor();
    assertThat(newId)
        .as("the container's id after the simulated refactor")
        .isEqualTo(DynamicControlsPage.REFACTORED_FORM_ID);
    assertThat(page().hasOriginalContainerId())
        .as("the original container id must be gone, otherwise the locator would still match and "
            + "the scenario would prove nothing")
        .isFalse();
    LOG.info("Renamed '#{}' to '#{}'; the locator '#{} button' now matches nothing",
        DynamicControlsPage.CHECKBOX_FORM_ID, DynamicControlsPage.REFACTORED_FORM_ID,
        DynamicControlsPage.CHECKBOX_FORM_ID);
  }

  @Then("the swap control is found again and still reads the same label")
  public void theSwapControlIsFoundAgain() {
    String before = context().get(LABEL_BEFORE_REFACTOR, String.class);
    String after = page().getSwapControlLabel();
    assertThat(after)
        .as("the swap control's label after healing recovered it")
        .isEqualTo(before);
    LOG.info("The locator was recovered; the swap control still reads '{}'", after);
  }

  @Then("the healing report records at least one recovered locator")
  public void theHealingReportRecordsARecovery() {
    assertThat(HealingMetrics.hasHealingInCurrentScenario())
        .as("Healenium reported no healing for this scenario. The element was still found, so "
            + "either the locator was never really broken, or the healing events were not "
            + "captured - check that the 'com.epam.healenium' logger is at INFO or lower "
            + "(see logback.xml and HealingLogBridge).")
        .isTrue();
  }

  private DynamicControlsPage page() {
    return currentPage(DynamicControlsPage.class);
  }

  private void allowBackendToPersist() {
    try {
      TimeUnit.MILLISECONDS.sleep(PERSIST_SETTLE_MILLIS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the healing backend", ex);
    }
  }
}
