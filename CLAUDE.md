# CLAUDE.md

Native Android app: Kotlin + Jetpack Compose. This file is always in context, so it stays short.
Depth lives in `.claude/skills/`. Read the relevant skill before doing real work.

---

## Project facts

- **Package / applicationId:** `com.peal.appscheduler` (namespace identical)
- **minSdk / targetSdk / compileSdk:** 24 / 36 / 36
- **Module layout:** single module `:app` (`settings.gradle.kts` includes only `:app`). Internal packages:
  `core/` (Result/Error primitives, `ObserveAsEvents`), `data/` (Room local, mappers, repositoryImpl,
  `wapper/AlarmManagerWrapper` — sic), `di/`, `domain/` (model, repository interfaces, usecase, utils),
  `receiver/`, `service/`, `ui/` (screens, shared components, navigation, theme), `utils/`.
- **DI:** Hilt 2.52, rooted at `AppSchedulerApp` (`@HiltAndroidApp`). Three `SingletonComponent` modules:
  `AppModule` (AlarmManagerWrapper, PackageManager), `DatabaseModule` (Room db + DAO), `RepositoryModule`
  (`@Binds` for the three repositories). Hilt compiler runs through **kapt**, Room through **KSP**.
- **Navigation:** Navigation 3 (`androidx.navigation3` 1.0.0) — `NavDisplay` + `entryProvider` in
  `ui/shared/navigation/AppSchedulerNavHost.kt`, backstack held by `NavigationState`/`Navigator`. Routes are
  type-safe: `@Serializable` `NavKey` data objects/class in `Screens.kt`. Legacy `navigation-compose` 2.8.7
  is still declared in `app/build.gradle.kts` and imported in `AppSchedulerNavHost.kt` but unused.
- **Async & state:** Coroutines + `StateFlow`. MVI-ish contract pattern per screen
  (`*Contract.kt` = State / Event / Effect). No explicit `kotlinx-coroutines-core` entry in the version
  catalog — it arrives transitively via Lifecycle/Room.
- **Persistence / network:** Room 2.6.1 (`SchedulerAppDatabase`, `ScheduleDao`), no declared migrations.
  **No networking layer and no image-loading library** — app icons come from `PackageManager` drawables via
  `ui/utils/ImageUtils.kt`. Serialization: `kotlinx-serialization-json` 1.8.0 (used for nav keys).
- **Test stack:** JUnit4 only. 1 JVM unit test (`ExampleUnitTest` — asserts 2+2), 2 instrumented tests
  (`ExampleInstrumentedTest` — package name; `ScheduleDaoTest` — 233 lines, the only real test).
  Available but largely unused: `androidx.arch.core:core-testing`, `room-testing`,
  `compose-ui-test-junit4`, Espresso. **No** `kotlinx-coroutines-test`, Turbine, MockK, or Robolectric.
- **CI:** GitHub Actions, `.github/workflows/main.yml`, on push to `main`. Runs **only**
  `./gradlew assembleDebug`, uploads the debug APK, and posts an adaptive card to a Teams webhook.
  No lint, no unit tests, no release build in CI.
- **Build:** Gradle 8.14.3, AGP 8.13.1, Kotlin 2.0.0, JVM target 17, core library desugaring enabled,
  `-Xjvm-default=all`. **R8/minification is disabled for release** (`isMinifyEnabled = false`); the
  proguard file is stock comments. No baseline profile, no convention plugins, no build flavors,
  no signing config, no configuration cache / parallel flags in `gradle.properties`.

---

## Build & verify

Never claim something works without running it. Use the wrapper, never a global `gradle`.

```bash
./gradlew assembleDebug              # compiles
./gradlew lintDebug                  # Android Lint
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented (needs a device)
./gradlew :app:assembleRelease       # R8/minified build — catches keep-rule breakage
```

`android` CLI (Android CLI) is available and preferred for docs and devices:

```bash
android docs "<keywords>"     # authoritative, current Android docs — use this over memory
android run                   # build + install + launch
android emulator list|start
android screenshot out.png
```

**Before finishing any task:** `./gradlew assembleDebug lintDebug testDebugUnitTest` must pass, or say
explicitly what failed and why. A change that compiles in your head is not a change that compiles.

---

## Non-negotiables

These are the rules that get broken most often in this codebase's domain. Violating one is a bug, not a
style preference.

1. **Never hardcode dependency versions from memory.** Read `gradle/libs.versions.toml`. If a version needs
   to change, check the current stable release first (`android docs`, or the library's release page).
   Your training data is stale by construction.
2. **Composables take `Modifier` as the first optional param, defaulted, and applied to the root node.**
3. **No business logic, no I/O, no `remember { }` of expensive objects inside a composable body.** State
   hoists up; events flow down.
4. **Never collect a Flow with `collectAsState()`** — use `collectAsStateWithLifecycle()` so collection
   stops in the background.
5. **UI state is a single immutable type** exposed as `StateFlow<UiState>` from the ViewModel. No mutable
   collections in state — use `ImmutableList`/`persistentListOf` or a wrapper, so Compose can skip.
6. **ViewModels never import `android.*` UI types**, never hold a `Context`, never reference `Composable`.
7. **No `GlobalScope`, no `Dispatchers.Main` hardcoded in ViewModels.** Inject dispatchers so tests can
   swap them.
8. **Don't `try/catch` and swallow.** Coroutine cancellation (`CancellationException`) must rethrow.
9. **New public API in a shared module needs a KDoc line** saying what it's for.
10. **No new dependency without asking.** Adding a library is a decision, not an implementation detail.

---

## How to work in this repo

- **Read before writing.** Find two existing examples of the pattern you're about to add and match them.
  Consistency with the codebase beats consistency with a blog post.
- **Scope discipline.** Fix the thing asked for. If you spot three other problems, list them at the end;
  don't fold them into the diff. Large mixed diffs are unreviewable and get reverted wholesale.
- **Prefer the smallest change that is actually correct.** Not the smallest change that appears to work.
- **When Android behavior is uncertain, look it up** (`android docs`) rather than reasoning from priors.
  Platform behavior changes per API level and your intuition about it is often a version or two behind.
- **Say when you're unsure.** "I think this is a recomposition problem but I haven't measured it" is a
  useful sentence. Confident wrong answers cost more than hedged right ones.

## Skills

Load these when the task matches; they hold the detail this file omits.

| Skill | Use when |
|---|---|
| `android-audit` | Auditing the codebase, reviewing health, producing findings |
| `android-architecture` | Layering, modules, DI, state, data flow decisions |
| `compose-quality` | Writing/reviewing composables, recomposition, performance |
| `android-testing` | Adding or fixing tests, test strategy |
| `gradle-build-health` | Build config, version catalog, R8, build times |

Google's official Android skills are also installed (edge-to-edge, Nav3, R8 analyzer, intent security,
AGP upgrade, profiler). They are authoritative for those topics — prefer them over improvising.
