package io.github.abdulalamer.selfhealing.demo.pages;

/**
 * The little that every demo page has in common: it can be opened, and it has a heading.
 *
 * <p>Exists so that navigation and heading assertions can be written once in
 * {@code NavigationSteps} instead of duplicated per page, and so that a page can be handed between
 * step classes through the scenario context without any of them knowing its concrete type.
 */
public interface DemoPage {

  /** Navigates to this page and waits for it to be ready. */
  void open();

  /** The page's visible heading, with whitespace normalised. */
  String getHeading();
}
