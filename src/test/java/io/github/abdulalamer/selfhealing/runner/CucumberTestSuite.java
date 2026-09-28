package io.github.abdulalamer.selfhealing.runner;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * The one entry point Gradle runs.
 *
 * <p>Everything else - glue packages, plugins, parallelism - lives in
 * {@code src/test/resources/junit-platform.properties} rather than in annotations here, because JUnit
 * resolves configuration parameters from system properties first. That is what lets
 * {@code -Ptags="@smoke"} and {@code -Pthreads=4} work from the command line without a second runner
 * class or an edit to this file.
 *
 * <p>Scenarios are discovered from the {@code features} directory on the test classpath.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class CucumberTestSuite {
}
