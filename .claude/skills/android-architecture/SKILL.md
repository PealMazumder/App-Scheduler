---
name: android-architecture
description: Decide and enforce Android app architecture — layering (UI/domain/data), module boundaries, ViewModel and UI state design, unidirectional data flow, repositories, dependency injection scoping, error handling, and dispatcher injection. Use this whenever adding a feature, module, ViewModel, repository, or use case; whenever refactoring across layers; and whenever the user asks where code should live, how to structure state, how to wire DI, or how to split modules. Consult it before writing new non-trivial code, not just when something is already tangled.
---

# Android architecture

Full audit checklist: `../android-audit/references/architecture.md`. This file is for making decisions
while writing code.

## Layers

```
UI (Compose)  →  ViewModel  →  [Domain: UseCase]  →  Repository  →  DataSource (remote/local)
```

Dependencies point one direction only. The domain layer, if present, knows nothing about Android.

**Where does this code go?**

| It does this | It lives here |
|---|---|
| Renders, handles gestures | Composable |
| Holds screen state, survives rotation, handles UI events | ViewModel |
| Business rule reused across screens | UseCase (or repository if trivial) |
| Decides cache vs network, exposes app-shaped data | Repository |
| Talks to Retrofit/Room/DataStore | DataSource |

If a use case is a one-line pass-through to a repository, skip it. Ceremony has a maintenance cost and
buys nothing when there's no logic to hold.

## ViewModel and state

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ItemRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val query = savedStateHandle.getStateFlow("query", "")

    val uiState: StateFlow<HomeUiState> = query
        .flatMapLatest { repository.observeItems(it) }
        .map { HomeUiState.Content(it.toImmutableList()) }
        .catch { emit(HomeUiState.Error(it.toUserMessage())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )
}
```

Points worth understanding rather than copying:

- **`WhileSubscribed(5_000)`** keeps the upstream alive across configuration changes but stops it when the
  app is backgrounded. `Eagerly` leaks work; `Lazily` never stops.
- **`SavedStateHandle`** for anything that must survive process death. Users on low-memory devices hit this
  constantly and it's invisible in normal testing.
- **The ViewModel never imports Android UI types or holds a Context.** That's what makes it unit-testable
  on the JVM.
- **Inject dispatchers**, don't hardcode them, or tests can't control timing.

## One-shot events

Navigation and snackbars are not state. Putting them in state replays them on rotation.

```kotlin
private val _events = Channel<HomeEvent>(Channel.BUFFERED)
val events = _events.receiveAsFlow()
```

Consume in the UI with a lifecycle-aware collector, not a raw `LaunchedEffect` on state.

## Errors

Pick one model and apply it everywhere. Mixed models are worse than either choice.

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}
```

Always rethrow `CancellationException`. A blanket `catch (e: Exception)` that swallows it breaks structured
concurrency, and the symptom — a screen that silently stops updating — looks nothing like the cause.

## Modules

Start with `:app` + `:core:*` + `:feature:*`. Split further only when there's a reason: build time, a
genuine ownership boundary, or reuse across apps. Premature modularization buys a slower build and a
maze of Gradle files.

Rules that keep it healthy: features never depend on other features (route through `:core` or a shared
API module); no cycles; `:core:common` must not depend on everything (that's a monolith with extra
steps). Consider a dependency-analysis or module-graph check in CI once you're past a handful of modules.

## DI scoping

Wrong scoping is the most common DI bug and it presents as data leaking between users or sessions.
`@Singleton` is for genuinely app-wide, stateless-or-app-lifetime things. Anything holding per-user or
per-session state needs its own scope and needs to be cleared on logout — trace what survives sign-out.
