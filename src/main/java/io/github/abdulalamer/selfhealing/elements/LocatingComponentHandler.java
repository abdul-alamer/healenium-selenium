package io.github.abdulalamer.selfhealing.elements;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.ElementLocator;

/**
 * Invocation handler behind the lazily-resolved {@code WebElement} that a component is built on.
 *
 * <p>Every call except {@code toString} triggers a fresh {@link ElementLocator#findElement()}. Two
 * consequences matter:
 *
 * <ul>
 *   <li><b>Staleness cannot accumulate.</b> The element is never cached across calls, so a re-render
 *       between two interactions is invisible to the caller.
 *   <li><b>Every call is a healing opportunity.</b> The lookup goes through whatever
 *       {@code SearchContext} the locator was built with - the self-healing driver in a healing run -
 *       so a locator that has stopped matching gets another chance on each interaction rather than
 *       only on the first.
 * </ul>
 *
 * <p>{@code toString} is answered without resolving anything, so logging an element reference never
 * hits the browser and never throws. Debugging a page object should not change its behaviour.
 */
public class LocatingComponentHandler implements InvocationHandler {

  private static final String TO_STRING = "toString";
  private static final String GET_WRAPPED_ELEMENT = "getWrappedElement";

  private final ElementLocator locator;

  public LocatingComponentHandler(ElementLocator locator) {
    this.locator = Objects.requireNonNull(locator, "locator");
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (TO_STRING.equals(method.getName()) && isNoArg(args)) {
      return "Proxy element for: " + locator;
    }

    WebElement element = locator.findElement();

    if (GET_WRAPPED_ELEMENT.equals(method.getName()) && isNoArg(args)) {
      return element;
    }

    try {
      return method.invoke(element, args);
    } catch (InvocationTargetException ex) {
      // Unwrap, so callers see StaleElementReferenceException rather than a reflection wrapper.
      throw ex.getCause();
    }
  }

  private static boolean isNoArg(Object[] args) {
    return args == null || args.length == 0;
  }
}
