package io.github.abdulalamer.selfhealing.demo.steps;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.abdulalamer.selfhealing.demo.pages.FramesPage;

/** Reading and writing inside an iframe. */
public class FrameSteps extends AbstractSteps {

  @Then("the editor contains {string}")
  public void theEditorContains(String expected) {
    assertThat(currentPage(FramesPage.class).getEditorText())
        .as("the text inside the editor frame")
        .contains(expected);
  }

  @When("I replace the editor content with {string}")
  public void iReplaceTheEditorContentWith(String text) {
    currentPage(FramesPage.class).replaceEditorText(text);
  }
}
