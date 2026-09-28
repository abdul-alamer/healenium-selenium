package io.github.abdulalamer.selfhealing.demo.steps;

import io.github.abdulalamer.selfhealing.demo.support.ScenarioContext;
import io.github.abdulalamer.selfhealing.driver.DriverManager;
import org.openqa.selenium.WebDriver;

/**
 * Shared plumbing for step definitions: the driver for this thread, and the page the scenario is
 * currently on.
 *
 * <p>Deliberately thin. Step definitions should read as a description of the behaviour under test,
 * so anything that is not behaviour belongs either here or, better, in a page object.
 */
public abstract class AbstractSteps {

  /** Key the current page object is stored under in the scenario context. */
  public static final String CURRENT_PAGE = "current-page";

  /** The browser bound to this scenario's thread. */
  protected WebDriver driver() {
    return DriverManager.get();
  }

  /** Data shared between the step classes of this scenario. */
  protected ScenarioContext context() {
    return ScenarioContext.current();
  }

  /**
   * The page the scenario navigated to, as its concrete type.
   *
   * @param <T>  the expected page type
   * @param type the expected page type
   * @return the current page
   */
  protected <T> T currentPage(Class<T> type) {
    return context().get(CURRENT_PAGE, type);
  }
}
