package io.github.abdulalamer.selfhealing.elements;

import io.github.abdulalamer.selfhealing.elements.ComponentFieldDecorator.ComponentInstantiator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.pagefactory.ElementLocator;

/**
 * Invocation handler behind a {@code List<SomeComponent>} field.
 *
 * <p>The list is re-resolved on every call, and a fresh component instance is constructed for each
 * matched element. That is more work than caching, and it is the right trade: a table that re-renders
 * between {@code rows.size()} and {@code rows.get(2)} would otherwise hand back a component wrapping
 * a detached node. Here the second call simply sees the new DOM.
 *
 * <p>It follows that a component obtained from such a list must not be held across an interaction
 * that re-renders the container. Read what you need from it, or re-index.
 *
 * <p>{@code toString} is answered without resolving the list, so logging a component list costs
 * nothing and cannot fail.
 */
public class LocatingComponentListHandler implements InvocationHandler {

  private static final String TO_STRING = "toString";

  private final ElementLocator locator;
  private final Class<?> componentType;
  private final ComponentInstantiator instantiator;

  /**
   * @param locator      locates the elements the list is built from
   * @param componentType the component class to instantiate per element
   * @param instantiator  builds one component from one element
   */
  public LocatingComponentListHandler(ElementLocator locator, Class<?> componentType,
      ComponentInstantiator instantiator) {
    this.locator = Objects.requireNonNull(locator, "locator");
    this.componentType = Objects.requireNonNull(componentType, "componentType");
    this.instantiator = Objects.requireNonNull(instantiator, "instantiator");
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (TO_STRING.equals(method.getName()) && (args == null || args.length == 0)) {
      return "Proxy " + componentType.getSimpleName() + " list for: " + locator;
    }

    List<WebElement> elements = locator.findElements();
    // A mutable ArrayList, matching what Selenium's own list proxy hands back, so callers can sort
    // or reverse the result without hitting UnsupportedOperationException.
    List<Object> components = new ArrayList<>(elements.size());
    for (WebElement element : elements) {
      components.add(instantiator.create(componentType, element));
    }

    try {
      return method.invoke(components, args);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }
}
