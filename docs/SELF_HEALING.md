# Self-healing

## The problem

Locator churn is the single largest maintenance cost in UI automation. A front-end team renames a
container, swaps a CSS module for another, or upgrades a component library, and a suite that tested
nothing different yesterday is red this morning. The application still works. The tests are still
correct about what they assert. Only the path to the element changed.

Most teams pay for this twice: once in the morning spent re-pointing locators, and again in the
credibility the suite loses every time it cries wolf.

## What Healenium does

[Healenium](https://healenium.io) is an open-source library that wraps your WebDriver and does two
things:

1. **On every successful `findElement`**, it stores a description of the element - its tag,
   attributes, text, position among its siblings, and the chain of ancestors above it - in a
   Postgres database behind its backend service. This is the *node tree*.
2. **On every failed `findElement`**, it retrieves the node tree previously stored for that locator
   and sends it to the *selector-imitator*, which walks the current DOM and scores each candidate
   node for similarity. If the best candidate scores above `score-cap`, that element is returned and
   the call succeeds as though the locator had matched.

The important consequence, and the one that trips everybody up first:

> **Healing can only repair a locator that has worked at least once.**

There is nothing to compare against otherwise. A locator that was wrong the day it was written will
never heal; it will just fail, which is correct.

```
first run  ──► locator matches ──► node tree stored ──────────────┐
                                                                  │
front-end change ──► locator matches nothing ──► stored tree ◄────┘
                                        │
                                        ▼
                          selector-imitator scores candidates
                                        │
                          best score > score-cap ? ──► element returned, step passes
                                        │
                                        no ──► ElementNotHealedException
```

### The two tuning knobs

| Key | What it controls |
|-----|------------------|
| `score-cap` | How similar a candidate must be, 0.0 to 1.0. Default `0.5`. Lower it and healing succeeds more often but is more likely to grab the wrong element; raise it and healing becomes conservative. |
| `recovery-tries` | How many stored candidate node trees to try per broken locator. Default `1`. Raise it when a locator has several historical shapes worth attempting. |

`score-cap` is the honest trade-off in this whole idea. A healed locator that picked the wrong
element produces a passing test that asserts nothing, which is worse than a failing one. Start at
the default and move it up, not down.

## How this framework wires it in

The integration is one line, in `SelfHealingDriverFactory`:

```java
WebDriver delegate = new ChromeDriver(options);
SelfHealingDriver driver = SelfHealingDriver.create(delegate);
```

Everything else follows from it. Page objects resolve their `@FindBy` fields through
`PageFactory` bound to this driver, so every locator in the suite goes through the healing engine
with nothing to annotate and nothing to remember.

Two details are worth knowing:

**Ordering.** Healenium resolves its configuration into a `static` field when its engine class first
loads, and system properties override `healenium.properties`. So `HealingConfig.applySystemProperties()`
runs before anything references `SelfHealingDriver` - once in `SelfHealingDriverFactory`, and again
at test-run start in `SuiteLifecycleListener`, which removes the dependency on class-loading order
entirely.

**Every poll is another chance.** The component waits in `BaseComponent` are polled predicates
rather than Selenium `ExpectedCondition`s over an already-resolved element. Each poll performs a
fresh lookup, and a fresh lookup is a fresh opportunity to heal. Resolving once and waiting on the
result would give healing exactly one attempt, at the least convenient moment.

## Locator retention: why this framework does not parse `toString()`

The framework this one was extracted from recovered stale elements by parsing
`WebElement.toString()`. It searched the string for markers such as `"-> xpath:"` and
`"cssSelector:"`, sliced out the locator value with index arithmetic, and re-ran the lookup. There
were two near-identical parsers, because a real element and a `PageFactory` proxy print differently.

It worked. It was also fragile by construction:

- `toString()` is a debugging convenience. No driver promises its format across versions.
- Any proxy layer between the test and the driver changes the text - and a self-healing driver is
  exactly such a layer.
- When the format did not match, the parser fell through to `retWebEl = null` and the caller got a
  `NullPointerException` from somewhere unrelated.
- Every new locator type needed a new `else if`.

`BaseComponent` keeps what it was built from instead of reconstructing it afterwards: the `By` and
the `SearchContext` it was resolved against. A stale element is re-located exactly, deterministically
and from the same parent. No parsing, no guessing, no per-locator-type branch, and locator types
added to Selenium in future work unchanged.

The same idea extends to children. `BaseComponent.asSearchContext()` returns a context that resolves
through `getWebElement()`, so a component built from it inherits its parent's recovery rather than
caching a node that may already be detached.

Components injected by the field decorator need no locator at all - their element is a proxy that
re-resolves on every call, which is a stronger guarantee still.

## Making healing visible

Healing that nobody sees is worse than no healing. A suite quietly repairing twenty locators a night
has converted a maintenance problem into a hidden one, and the day one of those heals picks the
wrong element you will have no idea how long it has been doing so.

So every recovery is reported three ways:

| Where | What | Produced by |
|-------|------|-------------|
| Allure, per scenario | The healing events for that scenario, flagged as debt | `HealingReportCollector.attachScenarioSummary` |
| `build/healenium/summary.txt` | The whole run's healing activity | `HealingReportCollector.publish` |
| `http://localhost:7878/healenium/report` | The backend's own report, with before/after screenshots | The Healenium backend |

### How the framework knows a heal happened

Healenium OSS exposes no listener interface and no programmatic callback for "a locator was just
healed". The only in-process announcement is its SLF4J logger. `HealingLogBridge` therefore attaches
a capture appender to `com.epam.healenium` and feeds matching events into `HealingMetrics`.

That is a real trade-off and worth stating plainly: the summary is coupled to Healenium's log text.
If Healenium changes its wording, the summary under-reports. Nothing else breaks - no test outcome
depends on it, and healing itself is unaffected. The alternative, reading the backend's database
directly, would couple the framework to an internal schema instead, which is worse. A slightly stale
report beat no visibility.

Two consequences follow:

- `com.epam.healenium` must stay at `INFO` or lower in `logback.xml`. Raising it silently empties the
  report.
- One recovery can emit more than one log line, so the count is described as "healing log events",
  not as an exact tally of healed locators.

## Running the demonstration

```bash
docker compose -f docker/docker-compose.yml up -d
./gradlew test -Ptags="@self-healing"
```

`features/self_healing.feature` does this:

1. **Train.** `DynamicControlsPage.getSwapControlLabel()` resolves `#checkbox-example button`
   against the page as shipped. It succeeds; Healenium stores the button's node tree.
2. **Break.** `simulateFrontEndRefactor()` renames the container from `checkbox-example` to
   `checkbox-example-v2`. The button is untouched - same tag, same text, same position, same
   ancestors. Only the path to it changed.
3. **Heal.** The same method resolves the same locator again. Selenium finds nothing, Healenium
   scores the stored tree against the current DOM, the button wins comfortably, and the step passes.

Both lookups go through the same page-object method deliberately: Healenium keys a stored tree by
the locator together with the context it was first resolved from, so training and healing through
one method keeps that key stable.

The DOM is mutated with JavaScript because the demo site is public and cannot be edited. That is the
only artificial part - everything after the mutation is the real path, with a real backend and a
real scoring round-trip.

There is one further artefact of compressing train-and-heal into a single scenario: Healenium gives
no signal when a node tree has finished being stored, so the step pauses briefly before breaking the
page. A real pipeline never needs that, because training happened on the previous green run.

### What a heal looks like in the log

```
14:22:07.881 INFO  c.e.h.h.BaseHealingService - Trying to heal locator: By.cssSelector: #checkbox-example button
14:22:08.104 INFO  c.e.h.h.BaseHealingService - Score: 0.94 for candidate: //form[@id='checkbox-example-v2']/button
14:22:08.311 WARN  c.e.h.SelfHealingDriver   - Healing completed. New locator: //form[@id='checkbox-example-v2']/button
14:22:08.402 INFO  i.g.a.s.h.HealingReportCollector - Self-healing: 3 log event(s) captured. Backend report: http://localhost:7878/healenium/report
```

Wording and exact scores vary between Healenium versions; the shape does not.

### Watching it fail, which is just as instructive

```bash
./gradlew test -Ptags="@self-healing" -PhealEnabled=false
```

The same code now raises `ElementNotHealedException`, whose message says healing was disabled. That
is the honest baseline: this is what the suite would do without any of this.

## When self-healing is the wrong answer

This section matters more than the rest of the document.

**Healing masks regressions.** Its entire job is to make a broken locator behave as though it were
not broken. Most of the time the locator is stale and the application is fine. But sometimes the
element really did move, or change meaning, or get replaced by a different control that happens to
look similar - and healing will paper over that just as cheerfully. A healed locator is a test that
did not check what you think it checked.

So:

1. **Keep `heal-enabled=false` on the pipeline that gates releases.** The gate exists to tell you
   whether the application changed. Healing exists to tell you the locator did. Mixing them means
   the gate can no longer answer its own question. Run healing on the nightly or the pre-merge
   suite, where a red build costs you an hour rather than a release.
2. **Treat every healed locator as debt, not as a fix.** Healing bought you one run. The page object
   is still wrong. The Allure summary says so in those words on purpose.
3. **Never let the healing count sit above zero for long.** A suite with persistent heals is a suite
   slowly detaching from the application it tests. Fix the locators; the count should return to
   zero.
4. **Do not lower `score-cap` to make a stubborn locator heal.** If nothing scores above 0.5, the
   element probably is not there any more. That is information, not an obstacle.
5. **Healing is not a substitute for good locators.** It buys time for the ones that churn despite
   your best efforts. It is not a reason to write brittle selectors on purpose, and a suite built on
   `div > div > span:nth-child(3)` will not be rescued by it.

Used this way - as a shock absorber on the fast feedback loop, never on the gate - self-healing
turns a class of overnight failure into a morning's tidying. Used as a way to stop thinking about
locators, it turns a noisy suite into a quiet one that no longer tests anything.
