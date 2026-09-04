# Phase 6 — Testing

Goal: assess whether the tests would actually catch a regression. Coverage percentage is close to
meaningless on its own; what matters is what breaks the build when the app breaks.

## Fast signals

```bash
find . -path "*/src/test/*" -name "*.kt" | wc -l
find . -path "*/src/androidTest/*" -name "*.kt" | wc -l
grep -rn "Thread.sleep\|runBlocking" --include="*Test.kt" | head -20
grep -rn "@Ignore\|@Disabled" --include="*Test.kt"
grep -rn "createComposeRule\|createAndroidComposeRule" --include="*.kt" | wc -l
grep -rn "TestDispatcher\|runTest\|StandardTestDispatcher" --include="*Test.kt" | wc -l
grep -rn "assertTrue(true)\|assertNotNull" --include="*Test.kt" | head   # assertion theatre
```

## Checklist

**T1 — Are the important things tested at all?** Map the top 5 user flows and check each has some
coverage. A codebase with 400 tests and no test of the checkout flow has a testing problem regardless of
the number.

**T2 — Test shape.** ViewModel/domain logic in fast JVM tests; UI behavior in Compose tests; a small
number of end-to-end tests. Inverted pyramids (everything instrumented) mean slow, flaky CI that people
learn to ignore.

**T3 — Coroutine testing.** `runTest` with injected `TestDispatcher`, not `Thread.sleep` and not
`runBlocking`. `Thread.sleep` in tests is the leading cause of flakiness. *High where present.*

**T4 — Flakiness.** `@Ignore`d tests, retry wrappers, arbitrary waits, tests depending on execution order
or real network. Count them — a stack of ignored tests is a finding about the team's relationship with the
suite, and worth naming plainly.

**T5 — Test doubles.** Hand-written fakes implementing the real interface are usually healthier than deep
mock chains. Mocking types you don't own (Retrofit, Room) tests your mocks. Look for `mockk` setups longer
than the test.

**T6 — Compose UI tests.** Do they use semantics/content descriptions and stable matchers, or brittle
index/text matching? Do they use `waitUntil` over sleeps? Do they test behavior or re-assert the
implementation?

**T7 — Assertion quality.** Tests that assert nothing meaningful, or assert only that no exception was
thrown. These inflate coverage while catching nothing.

**T8 — Screenshot tests.** Present for the design system? Cheap regression protection for a Compose
codebase and commonly absent — reasonable to recommend if there's a component library.

**T9 — Migration tests.** Room migrations without tests will lose user data eventually. *High.*

**T10 — Reliability of the suite.** Can `./gradlew testDebugUnitTest` run twice in a row from a clean
checkout and pass both times? Run it. Report the actual result.
