# CLAUDE.md

Native Android app: Kotlin + Jetpack Compose. This file is always in context, so it stays short.
Depth lives in `.claude/skills/`. Read the relevant skill before doing real work.

---

## Project facts

<!-- Run /prime-android once. It fills this block in from the actual repo. Do not guess these. -->

- **Package / applicationId:** _TBD_
- **minSdk / targetSdk / compileSdk:** _TBD_
- **Module layout:** _TBD_
- **DI:** _TBD_ (Hilt / Koin / manual)
- **Navigation:** _TBD_ (Nav 2 Compose / Nav3 / custom)
- **Async & state:** _TBD_ (Coroutines + Flow assumed)
- **Persistence / network:** _TBD_
- **Test stack:** _TBD_
- **CI:** _TBD_

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
