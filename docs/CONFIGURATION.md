# Configuration

Every knob in the framework resolves through one class, `FrameworkConfig`, and nothing reads a
system property or an environment variable behind its back. That single entry point is what makes
the precedence rules below true everywhere rather than mostly true.

## The four layers

Highest precedence first:

| # | Layer | Example | When to use it |
|---|-------|---------|----------------|
| 1 | System property | `-Dbrowser=firefox` | One-off overrides; what Gradle `-P` flags become |
| 2 | Environment variable | `BROWSER=firefox` | CI secrets and per-machine settings; `.env` |
| 3 | Per-environment overlay | `ci-config.properties` | Differences between environments, committed |
| 4 | Defaults | `default-config.properties` | The value that is right most of the time |

Layer 4 is the only file that has to be complete. An overlay defines just the keys that differ.

### Which overlay is active

`framework.env` (or `FRAMEWORK_ENV`) names it, and it defaults to `local`. The framework then looks
for `<env>-config.properties` on the classpath:

```bash
./gradlew test                        # framework.env=local  -> local-config.properties
./gradlew test -Penvironment=ci       # framework.env=ci     -> ci-config.properties
./gradlew test -Penvironment=staging  # framework.env=staging -> staging-config.properties
```

A missing overlay is not an error - that is what makes adding one a zero-risk change.

### Environment-variable names

The variable for any key is the key upper-cased with `.` and `-` replaced by `_`:

| Key | Variable |
|-----|----------|
| `base-url` | `BASE_URL` |
| `heal-enabled` | `HEAL_ENABLED` |
| `hlm.server.url` | `HLM_SERVER_URL` |
| `selenium-page-load-timeout-in-seconds` | `SELENIUM_PAGE_LOAD_TIMEOUT_IN_SECONDS` |

Copy `.env.example` to `.env` for local values. `.env` is git-ignored; nothing in this repository
reads it automatically, so export it however your shell or IDE prefers.

### Substitution inside properties files

Two forms are supported in layers 3 and 4:

```properties
remote-url  = ${env:SELENIUM_REMOTE_URL}          # environment variable, empty when unset
report-url  = ${hlm.server.url}/healenium/report   # another configuration key
```

An unset environment variable substitutes to the empty string and logs at debug. That is
deliberate: `remote-url = ${env:SELENIUM_REMOTE_URL}` then means "remote when the runner provides
an endpoint, local otherwise", and one overlay covers both.

A key that references itself, directly or through another key, fails at start-up with a
`ConfigurationException` naming the cycle.

## Keys

### Application under test

| Key | Default | Meaning |
|-----|---------|---------|
| `base-url` | `https://the-internet.herokuapp.com` | Root that `BasePage.openPath` resolves against. Page objects never hard-code a URL. |

### Browser

| Key | Default | Meaning |
|-----|---------|---------|
| `browser` | `chrome` | `chrome`, `firefox` or `edge`. Anything else fails at start-up with the supported list. |
| `headless` | `true` | Chrome and Edge use the new headless mode, which is the same binary as headed. |
| `remote-url` | *(empty)* | Grid or Selenium container endpoint. Empty starts a local browser via Selenium Manager. |
| `selenium-window-width` | `1366` | Window width; also passed as a browser argument so headless matches. |
| `selenium-window-height` | `768` | Window height. |
| `selenium-window-position-x` | `0` | Window position. |
| `selenium-window-position-y` | `0` | Window position. |

No driver binaries are downloaded or pinned. Selenium Manager, built into Selenium 4, resolves the
driver for the installed browser.

### Timeouts

| Key | Default | Meaning |
|-----|---------|---------|
| `selenium-implicit-wait-timeout-in-seconds` | `5` | Driver-level implicit wait. See the note below. |
| `selenium-web-driver-wait-timeout-in-seconds` | `30` | Default ceiling for explicit waits. |
| `selenium-page-load-timeout-in-seconds` | `30` | Navigation ceiling. |
| `selenium-script-timeout-in-seconds` | `30` | Asynchronous script ceiling. |

**Keep the implicit wait short.** Two reasons. It compounds with explicit waits in ways that are
hard to reason about, and with self-healing switched on it is also the delay before healing is even
attempted - recovery starts once the original lookup has failed, and the implicit wait is how long
that failure takes to arrive. Where a lookup should not wait at all, the framework zeroes it around
the call (`BasePage.withoutImplicitWait`, `isDisplayedFast`, `isPresentFast`).

### Retry and execution

| Key | Default | Meaning |
|-----|---------|---------|
| `retry-attempts` | `3` | Attempts for `Retry.run` and `BasePage.retry`. |
| `retry-backoff-in-millis` | `200` | Pause between attempts, multiplied by the attempt number. |
| `parallel-thread-count` | `1` | Informational; Cucumber's own parallelism is set by `-Pthreads`. |
| `screenshot-on-failure` | `true` | Whether the hooks attach a screenshot when a scenario fails. |

`Retry` deliberately retries only `RuntimeException`. `AssertionError` is an `Error`, so a failing
assertion fails on the first attempt and keeps its original message instead of being reported three
attempts later as a timeout.

### Self-healing

These five keys are spelled exactly as Healenium spells them, so the framework forwards them to
Healenium verbatim with no translation table to drift out of date.

| Key | Default | Meaning |
|-----|---------|---------|
| `heal-enabled` | `true` | Whether broken locators are recovered at all. |
| `hlm.server.url` | `http://localhost:7878` | Healenium backend. |
| `hlm.imitator.url` | `http://localhost:8000` | Selector-imitator, which does the scoring. |
| `recovery-tries` | `1` | Stored candidate node trees to try per broken locator. |
| `score-cap` | `0.5` | Minimum similarity, 0.0 to 1.0, for a candidate to be accepted. |
| `healing-report-dir` | `build/healenium/report` | Local directory scanned for artefacts to attach to Allure. |

`src/test/resources/healenium.properties` carries the same five keys for Healenium's own reader.
Both are kept in step; system properties override either, which is why
`./gradlew test -PhealEnabled=false` reliably switches healing off.

See [SELF_HEALING.md](SELF_HEALING.md) for what `score-cap` and `recovery-tries` actually do, and
for why `heal-enabled=false` belongs on the pipeline that gates releases.

### Reporting and IO

| Key | Default | Meaning |
|-----|---------|---------|
| `allure-results-dir` | `build/allure-results` | Where Allure results are written. |
| `download-dir` | `build/downloads` | Browser download directory; created if missing. |
| `tmp-dir` | `build/tmp` | Scratch space for generated test data. |

## Gradle command-line flags

`build.gradle` forwards these `-P` properties to the system properties above, so anything in the
table is reachable from the command line:

| Flag | Becomes | Example |
|------|---------|---------|
| `-Penvironment` | `framework.env` | `-Penvironment=ci` |
| `-Pbrowser` | `browser` | `-Pbrowser=firefox` |
| `-Pheadless` | `headless` | `-Pheadless=false` |
| `-PbaseUrl` | `base-url` | `-PbaseUrl=https://staging.example.test` |
| `-PremoteUrl` | `remote-url` | `-PremoteUrl=http://localhost:4444` |
| `-PwindowWidth` / `-PwindowHeight` | window size | `-PwindowWidth=1920` |
| `-PretryAttempts` | `retry-attempts` | `-PretryAttempts=1` |
| `-PhealEnabled` | `heal-enabled` | `-PhealEnabled=false` |
| `-PhealServerUrl` | `hlm.server.url` | `-PhealServerUrl=http://healenium.internal:7878` |
| `-PhealImitatorUrl` | `hlm.imitator.url` | `-PhealImitatorUrl=http://imitator.internal:8000` |
| `-PrecoveryTries` | `recovery-tries` | `-PrecoveryTries=2` |
| `-PscoreCap` | `score-cap` | `-PscoreCap=0.6` |
| `-Ptags` | `cucumber.filter.tags` | `-Ptags="@smoke and not @wip"` |
| `-Pthreads` | Cucumber parallelism | `-Pthreads=4` |
| `-PallureResultsDir` | Allure output directory | `-PallureResultsDir=out/allure` |

`-Pthreads` greater than 1 also switches `cucumber.execution.parallel.enabled` on, so raising
parallelism takes one flag rather than two.

```bash
./gradlew clean test allureReport \
  -Penvironment=ci \
  -Ptags="@smoke and not @wip" \
  -Pbrowser=chrome \
  -Pheadless=true \
  -Pthreads=4
```

## Adding a key

1. Add it to `src/main/resources/default-config.properties` with a comment and a sane default.
2. If it deserves a typed accessor, add one to `FrameworkConfig` next to the others.
3. If it should be settable from the command line, add a line to the `cliBridge` map in
   `build.gradle`.
4. Add a row to the table above.

Steps 1 and 4 are not optional. A key that only exists in code is a key nobody else will ever find.

## Logging

`src/main/resources/logback.xml` sets per-package levels. One of them is load-bearing:
`com.epam.healenium` must stay at `INFO` or lower, because `HealingLogBridge` captures healing
events from that logger to build the healing summary. Raising it does not affect healing itself - it
silently empties the report.

If you consume this project as a library from another build, that `logback.xml` is on your
classpath and Logback uses the first one it finds. Either exclude the resource or point Logback at
your own file with `-Dlogback.configurationFile=...`.
