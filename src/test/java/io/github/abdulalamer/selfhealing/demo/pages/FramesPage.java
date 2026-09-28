package io.github.abdulalamer.selfhealing.demo.pages;

import io.github.abdulalamer.selfhealing.elements.BaseComponent;
import io.github.abdulalamer.selfhealing.elements.IFrame;
import io.github.abdulalamer.selfhealing.pages.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * The rich-text-editor-in-an-iframe example.
 *
 * <p>Both interactions go through {@link IFrame#inFrame}, so the driver is always switched back to
 * the top-level document - including when an assertion inside the frame fails. Leaving the driver
 * inside a frame is the classic way one failing scenario poisons every scenario after it.
 */
public class FramesPage extends BasePage implements DemoPage {

  public static final String PATH = "/iframe";

  private static final By EDITOR_BODY = By.id("tinymce");

  @FindBy(css = "h3")
  private BaseComponent heading;

  @FindBy(css = "iframe[id^='mce_']")
  private IFrame editorFrame;

  public FramesPage(WebDriver driver) {
    super(driver);
  }

  @Override
  public void open() {
    openPath(PATH);
  }

  @Override
  public void waitPageLoading() {
    waitUntilPageLoaded();
    editorFrame.waitUntilAvailable();
  }

  @Override
  public String getHeading() {
    return heading.getNormalizedText();
  }

  /** The editor's current text, read from inside the frame. */
  public String getEditorText() {
    return editorFrame.inFrame(() -> findElement(EDITOR_BODY).getText().trim());
  }

  /** Replaces the editor's content. */
  public void replaceEditorText(String text) {
    editorFrame.inFrame(() -> {
      WebElement body = findElement(EDITOR_BODY);
      body.click();
      body.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE);
      body.sendKeys(text);
    });
  }
}
