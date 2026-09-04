---
name: android-testing
description: Write, fix, and evaluate tests for Kotlin/Compose Android code — JVM unit tests for ViewModels and domain logic, coroutine/Flow testing with TestDispatcher and Turbine, Compose UI tests, fakes over mocks, Room migration tests, and diagnosing flaky tests. Use this whenever adding or changing tests, whenever asked whether something is well tested, whenever a test is flaky or slow, and whenever writing new ViewModel or repository code that should ship with tests.
---

# Android testing

Full audit checklist: `../android-audit/references/testing.md`.

## What to test where

| Layer | Where | Speed |
|---|---|---|
| Domain logic, ViewModels, repositories | `src/test/` — JVM, no device | ms |
| Composable behavior | `src/androidTest/` with `createComposeRule` | seconds |
| Full flows, DB migrations, WorkManager | instrumented | slow — keep few |

Inverting this (everything instrumented) gives you a slow, flaky suite that people learn to ignore, which
is worse than fewer tests.

## ViewModel + Flow

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private val repository = FakeItemRepository()

    @Test
    fun `emits content after load`() = runTest {
        val viewModel = HomeViewModel(repository, SavedStateHandle())

        viewModel.uiState.test {                       // Turbine
            assertEquals(HomeUiState.Loading, awaitItem())
            repository.emit(listOf(item("a")))
            assertEquals(1, (awaitItem() as HomeUiState.Content).items.size)
        }
    }
}
```

`stateIn(WhileSubscribed(...))` produces nothing until collected — a test that reads `uiState.value`
without collecting sees only the initial value. This trips people up constantly.

Never use `Thread.sleep` in a test. It's the leading source of flakiness: it's simultaneously too slow on
fast machines and too fast on loaded CI. Use `runTest`'s virtual time, `advanceUntilIdle()`, or Turbine.

## Fakes over mocks

A fake implementing the real interface is usually better than a mock chain: it's reusable, it can't drift
from the interface without a compile error, and it doesn't encode implementation details into the test.

```kotlin
class FakeItemRepository : ItemRepository {
    private val flow = MutableSharedFlow<List<Item>>(replay = 1)
    override fun observeItems(query: String) = flow
    suspend fun emit(items: List<Item>) = flow.emit(items)
}
```

Mocking types you don't own (Retrofit services, Room DAOs, framework classes) tests your assumptions about
those libraries rather than your code. If a mock setup is longer than the test body, that's a signal.

## Compose UI tests

```kotlin
@get:Rule val composeRule = createComposeRule()

@Test
fun showsErrorAndRetries() {
    var retried = false
    composeRule.setContent {
        HomeScreen(HomeUiState.Error("offline"), onRefresh = { retried = true }, onItemClick = {})
    }
    composeRule.onNodeWithText("offline").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Retry").performClick()
    assertTrue(retried)
}
```

Test the stateless composable, not the route — no DI, no navigation, fast. Match on semantics
(`onNodeWithContentDescription`, test tags) rather than positional indices, which break on any layout
change. Use `composeRule.waitUntil { }` instead of sleeps.

## Things commonly missing

- **Room migration tests.** Untested migrations lose user data. `MigrationTestHelper` with the exported
  schema. Treat absence as a high-severity gap on any app with a shipped database.
- **Screenshot tests** for a design system — cheap Compose regression protection.
- **Process-death restoration**: pass a pre-populated `SavedStateHandle` and assert state is restored.

## Judging an existing suite

Ask what regression would actually break the build. A suite with high coverage that would let a broken
checkout flow ship has a coverage number and not much else. Count `@Ignore`s and sleeps — they say more
about the suite's health than the percentage does.
