package io.github.abdulalamer.selfhealing.elements;

import io.github.abdulalamer.selfhealing.exception.ConfigurationException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Locatable;
import org.openqa.selenium.support.FindAll;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;
import org.openqa.selenium.support.pagefactory.ElementLocator;

/**
 * Lets {@code PageFactory} inject typed components, not just {@code WebElement}s.
 *
 * <p>This is the heart of the framework. Out of the box, Selenium's {@code PageFactory} can fill a
 * field only if it is a {@code WebElement} or a {@code List<WebElement>}. That pushes every piece of
 * widget behaviour - "select this option from that combo box", "read the Due column of that table" -
 * into the page object or, worse, into step definitions. With this decorator a field can be declared
 * as the widget it actually is:
 *
 * <pre>{@code
 * public class OrdersPage extends BasePage {
 *
 *   @FindBy(id = "orders")
 *   private DataTable orders;                  // typed component
 *
 *   @FindBy(css = ".filter-row select")
 *   private List<SelectDropdown> filters;      // list of typed components
 *
 *   @FindBy(css = "h1")
 *   private WebElement heading;                // still works
 * }
 * }</pre>
 *
 * <h2>How it works</h2>
 *
 * <ol>
 *   <li>A field is "decoratable" when its type declares a constructor taking
 *       {@code (WebDriver, WebElement)} or {@code (WebDriver, SearchContext, WebElement)}. Nothing is
 *       registered anywhere; a new component type is picked up because it has the constructor.
 *   <li>A JDK dynamic proxy standing in for the element is created from the field's
 *       {@code ElementLocator}, so nothing is looked up until the component is used
 *       ({@link LocatingComponentHandler}).
 *   <li>The component is constructed around that proxy. For a {@code List} field the whole list is a
 *       proxy that re-resolves and rebuilds its components on every call
 *       ({@link LocatingComponentListHandler}).
 *   <li>Anything that is not a component falls through to {@link DefaultFieldDecorator}, which
 *       handles plain {@code WebElement} and {@code List<WebElement>} fields.
 * </ol>
 *
 * <p>Point 4 is a deliberate change from the framework this was extracted from, which returned
 * {@code null} for non-component fields and therefore needed two {@code PageFactory} passes - one
 * bound to the driver for elements, one bound to the component root for components. The side effect
 * was that a plain {@code WebElement} field inside a component was searched from the whole document
 * instead of from the component. One pass with a fallback fixes that and halves the reflection work.
 *
 * <p>Also dropped: the original's third constructor shape, which threaded a {@code Class<?>} generic
 * type through the decorator to support one generically-typed table. It had a single usage pattern
 * and cost a Guava dependency for {@code TypeToken}; plain reflection covers everything this
 * framework needs.
 */
public class ComponentFieldDecorator extends DefaultFieldDecorator {

  private final WebDriver driver;
  private final SearchContext root;

  /** Decorates fields located from the driver. */
  public ComponentFieldDecorator(WebDriver driver) {
    this(driver, driver);
  }

  /**
   * Decorates fields located from a narrower context.
   *
   * @param driver the driver, passed to every component so it can execute scripts and wait
   * @param root   the search context annotated fields are resolved against; null means the driver
   */
  public ComponentFieldDecorator(WebDriver driver, SearchContext root) {
    super(new DefaultElementLocatorFactory(root == null ? driver : root));
    this.driver = Objects.requireNonNull(driver, "driver");
    this.root = root == null ? driver : root;
  }

  @Override
  public Object decorate(ClassLoader loader, Field field) {
    if (!hasFindAnnotation(field)) {
      return null;
    }

    if (isComponent(field.getType())) {
      ElementLocator locator = factory.createLocator(field);
      return locator == null ? null : createInstance(field.getType(), proxyElement(loader, locator));
    }

    Class<?> componentType = componentListType(field);
    if (componentType != null) {
      ElementLocator locator = factory.createLocator(field);
      return locator == null ? null : proxyComponentList(loader, locator, componentType);
    }

    // Plain WebElement / List<WebElement>: Selenium's own behaviour, same search root.
    return super.decorate(loader, field);
  }

  /**
   * Whether a type can be built around a located element.
   *
   * @param type candidate component type
   * @return true when it declares one of the two recognised constructors
   */
  protected boolean isComponent(Class<?> type) {
    return findConstructor(type, WebDriver.class, WebElement.class) != null
        || findConstructor(type, WebDriver.class, SearchContext.class, WebElement.class) != null;
  }

  /**
   * The component type of a {@code List<SomeComponent>} field.
   *
   * @param field the field to inspect
   * @return the component class, or null when the field is not a list of components
   */
  protected Class<?> componentListType(Field field) {
    if (!List.class.isAssignableFrom(field.getType())) {
      return null;
    }
    Type generic = field.getGenericType();
    if (!(generic instanceof ParameterizedType parameterized)) {
      return null;
    }
    Type argument = parameterized.getActualTypeArguments()[0];
    if (!(argument instanceof Class<?> candidate)) {
      // Wildcards and nested generics such as List<List<Row>> are not component lists.
      return null;
    }
    return isComponent(candidate) ? candidate : null;
  }

  /** Builds the lazily-resolving element proxy a component is constructed around. */
  protected WebElement proxyElement(ClassLoader loader, ElementLocator locator) {
    InvocationHandler handler = new LocatingComponentHandler(locator);
    return (WebElement) Proxy.newProxyInstance(loader,
        new Class<?>[]{WebElement.class, WrapsElement.class, Locatable.class}, handler);
  }

  /** Builds the proxy list that rebuilds its components on every call. */
  @SuppressWarnings("unchecked")
  protected <T> List<T> proxyComponentList(ClassLoader loader, ElementLocator locator,
      Class<T> componentType) {
    InvocationHandler handler =
        new LocatingComponentListHandler(locator, componentType, this::createInstance);
    return (List<T>) Proxy.newProxyInstance(loader, new Class<?>[]{List.class}, handler);
  }

  /**
   * Constructs one component around one element.
   *
   * <p>The three-argument constructor is preferred when this decorator has a real parent context, so
   * a nested component can still reach the context it was found in. Otherwise the two-argument form
   * is used.
   *
   * @param componentType the component class
   * @param element       the element - usually a proxy - the component wraps
   * @return the constructed component
   * @throws ConfigurationException when the type cannot be constructed
   */
  protected Object createInstance(Class<?> componentType, WebElement element) {
    boolean hasParentContext = root != driver;
    try {
      if (hasParentContext) {
        Constructor<?> withContext =
            findConstructor(componentType, WebDriver.class, SearchContext.class, WebElement.class);
        if (withContext != null) {
          return withContext.newInstance(driver, root, element);
        }
      }
      Constructor<?> simple = findConstructor(componentType, WebDriver.class, WebElement.class);
      if (simple != null) {
        return simple.newInstance(driver, element);
      }
      Constructor<?> withContext =
          findConstructor(componentType, WebDriver.class, SearchContext.class, WebElement.class);
      if (withContext != null) {
        return withContext.newInstance(driver, root, element);
      }
      throw new ConfigurationException(componentType.getName()
          + " cannot be injected by @FindBy: it needs a public constructor taking "
          + "(WebDriver, WebElement) or (WebDriver, SearchContext, WebElement).");
    } catch (ReflectiveOperationException ex) {
      throw new ConfigurationException(
          "Could not construct " + componentType.getName() + " around a located element", ex);
    }
  }

  private static boolean hasFindAnnotation(Field field) {
    return field.getAnnotation(FindBy.class) != null
        || field.getAnnotation(FindBys.class) != null
        || field.getAnnotation(FindAll.class) != null;
  }

  private static Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) {
    try {
      return type.getConstructor(parameterTypes);
    } catch (NoSuchMethodException ex) {
      return null;
    }
  }

  /** Builds one component instance from one element. */
  @FunctionalInterface
  public interface ComponentInstantiator {

    /**
     * @param componentType the component class to build
     * @param element       the element the component wraps
     * @return the constructed component
     */
    Object create(Class<?> componentType, WebElement element);
  }
}
