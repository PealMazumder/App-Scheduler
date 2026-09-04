# 02 — Jetpack Compose correctness & recomposition (Audit Phase 2)

**Date:** 2026-09-04 · **Scope:** all 29 files under `app/src/main/java/com/peal/appscheduler/ui/`,
plus `core/presentation/util/ObserveAsEvents.kt`, `MainActivity.kt`, and the mappers/models the
composables consume. Every file in scope was read in full — no sampling.

**Measurement gap (important):** the Compose compiler metrics were **not** generated (baseline build
already ran; no `-Pkotlin.compose.reports`). Every stability/skippability claim below is therefore
static reasoning and is marked **unverified**. Adding
`composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }` and running
`./gradlew assembleRelease` would settle them with facts. No device was attached, so no recomposition
counts or frame timings either.

---

## What is already right

Worth stating, because the list below is long and most of it is Low:

- **`collectAsState()` appears nowhere.** All three screens use `collectAsStateWithLifecycle()`
  (`HomeScreen.kt:34`, `DeviceAppsListScreen.kt:31`, `SchedulerScreen.kt:76`). Checklist item C1 is clean.
- **Every `mutableStateOf` is remembered** (C2 clean), and the two dialog flags that need to survive
  rotation use `rememberSaveable` (`SchedulerScreen.kt:109-110`).
- **Screens are split into a stateful `*Route` and a stateless screen** taking `state` + `onIntent`.
  That is the right shape and makes the screens previewable.
- **`HomeScreen.kt:81-83` supplies a lazy-list `key`.**
- **The one `DisposableEffect` has a matching `onDispose`** (`SchedulerScreen.kt:116-129`) — C6 clean there.
- No nested scrollables, no unbounded-height lazy lists, no `derivedStateOf` cargo-culting.

---

## Findings

### CMP-1 — High — Drawable→Bitmap conversion runs on the composition/main thread, per list row, at launcher-icon resolution

`app/src/main/java/com/peal/appscheduler/ui/shared/components/AppIcon.kt:30`
`app/src/main/java/com/peal/appscheduler/ui/utils/ImageUtils.kt:14-33`

`AppIcon` does `remember(icon) { icon?.let { drawableToBitmap(it) } }`. `drawableToBitmap` allocates a
`Bitmap` and rasterises the drawable through a `Canvas` (`ImageUtils.kt:19-27`) **synchronously inside
composition**, i.e. on the main thread. It allocates at `drawable.intrinsicWidth/intrinsicHeight`
(`ImageUtils.kt:20-21`) with `Bitmap.Config.ARGB_8888` — for an adaptive launcher icon on an xxhdpi
device that is roughly 288–324 px square, ~330–420 KB per icon — and then renders it into a **48 dp**
target (`AppIcon.kt:37`, `ContentScale.Crop`). Classic P8: full-resolution decode into a small target.

`remember(icon)` is scoped to the individual item's composition slot, so there is no cache shared
between items or across LazyColumn item disposal — scrolling `InstalledAppsList`
(`DeviceAppsListScreen.kt:73-86`) back and forth re-rasterises every icon that re-enters composition.

Impact: main-thread raster work and large short-lived allocations on the scroll path of the device-apps
list, which on a typical phone holds 50–150 entries. Expect dropped frames and GC pressure while
scrolling. **The magnitude is unverified** — a memory profile or a macrobenchmark with
`FrameTimingMetric` over a scroll of the installed-apps list would quantify it.

### CMP-2 — High — `PackageManager.getApplicationIcon()` is called during composition (and on `Dispatchers.Main` in a ViewModel)

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt:79-81`
→ `domain/mappers/Mappers.kt:29-39` → `ui/utils/ContextExt.kt:30-37`

`SchedulerScreenRoute` runs `remember(route, context) { route.toScheduleAppInfoUi(context) }`, and that
mapper calls `context.getAppIconDrawable(packageName)` → `packageManager.getApplicationIcon(...)`. That
is a binder round-trip plus a resource load, executed in the composable body. Direct violation of
CLAUDE.md rule 3 ("no I/O inside a composable body"). It runs once per `route` change rather than per
recomposition, so it is a one-shot hit on screen entry, not a per-frame one — but it is still blocking
I/O on the composition thread and it is the wrong layer for it.

Same call, worse shape, in `ui/screens/home/HomeViewModel.kt:44-52`:
`viewModelScope.launch { getScheduledAppUseCase().collectLatest { ... scheduledApps.map { it.toScheduleAppInfoUi(context) } } }`
— no dispatcher is injected, so `viewModelScope` runs on `Dispatchers.Main.immediate` and the icon
lookup happens N times on the main thread on **every** Room emission. Cross-referenced to phases 1
(ViewModel holds a `Context`) and 3 (main-thread I/O); recorded here because it is the upstream half of
CMP-1.

### CMP-3 — Medium — `AppSchedulerNavHost` never applies its `modifier` to its root node

`app/src/main/java/com/peal/appscheduler/ui/shared/navigation/AppSchedulerNavHost.kt:29, 38, 45, 52, 58-62`

`AppSchedulerNavHost(modifier: Modifier = Modifier)` receives `Modifier.padding(innerPadding)` from
`MainActivity.kt:70` — i.e. the Scaffold's window insets. It then hands that same instance to
`HomeScreenRoute` (`:38`), `DeviceAppsListScreenRoute` (`:45`) and `SchedulerScreenRoute` (`:52`), and
calls `NavDisplay(...)` at `:58-62` **with no modifier at all**.

I verified against the artifact that `NavDisplay` does accept a `Modifier` as its first optional
parameter (`javap` on `androidx.navigation3.ui.NavDisplayKt`:
`NavDisplay(List<NavEntry<T>>, Modifier, Alignment, SceneStrategy<T>, ...)`), so there is a correct
target for it.

Sharing one immutable `Modifier` instance across siblings is not itself illegal — `Modifier.padding` is
stateless and a chain can legally be reused. The defects are consequential rather than fatal:

1. The contract is inverted. `AppSchedulerNavHost`'s own layout modifier is applied to its *children*
   instead of to itself, so any caller-supplied `size`/`background`/`weight` would be applied three
   times over rather than once to the host.
2. Insets become each screen's responsibility. Every new destination must remember to thread `modifier`
   to its own root or it silently loses the system-bar padding — there is no compiler help.
3. The `DialogSceneStrategy` at `:61` means a future dialog destination would receive the same
   inset padding, which is wrong for a dialog.

### CMP-4 — Medium — The same `modifier` is applied to two sibling nodes in one composition

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt:147` and `:230`

`SchedulerScreen` applies the incoming `modifier` to the root `Column` (`:146-149`) and then, when
`state.isLoading` is true, applies **the same** `modifier` again to `CommonCircularProgressIndicator`
(`:230`). Both nodes are in the composition simultaneously. Combined with CMP-3 the caller's inset
padding is applied twice on screen, and any caller passing a `background` or `clickable` would see it
duplicated. C7: "applied to the outermost node, and not applied twice."

(The comparable double-pass in `DeviceAppsListScreen.kt:58` vs `:60` is fine — those are mutually
exclusive `when` branches.)

### CMP-5 — Medium — Cluster: 8 composables with a missing or malformed `Modifier` parameter

No `modifier` parameter at all:
- `ui/screens/deviceApps/DeviceAppItem.kt:26` — `InstalledAppItem`
- `ui/screens/home/ScheduledAppItem.kt:33` — `ScheduledAppItem`
- `ui/screens/schedule/SchedulerScreen.kt:236` — `AppSection`
- `ui/screens/schedule/SchedulerScreen.kt:249` — `ActionButtons`
- `ui/shared/components/CommonAlertDialog.kt:17`
- `ui/shared/components/DatePickerDialog.kt:22`
- `ui/shared/components/TimePickerDialog.kt:19`
- `MainActivity.kt:92` — `OverlayPermissionDialog`

Declared but not defaulted, so callers are forced to supply one:
- `ui/screens/deviceApps/DeviceAppsListScreen.kt:68-71` — `InstalledAppsList(modifier: Modifier, ...)`

The two list items are the ones that actually bite: `ScheduledAppItem` and `InstalledAppItem` hardcode
`Modifier.fillMaxWidth().padding(...)` and `.clickable` on their root, so a caller cannot change
padding, add a background, or make the row non-interactive. That is exactly what forces the workaround
in CMP-16.

`AppIcon.kt:25-28` is compliant (modifier is the first *optional* param) — noting it so a fix pass
does not "correct" it.

Also: `OverlayPermissionDialog` (`MainActivity.kt:91-113`) is a `@Composable` declared as a **member
function of the Activity**, so it implicitly captures `this` (a `ComponentActivity`). It also takes a
`Context` as its first parameter while `LocalContext` is available. Move it to a top-level function.

### CMP-6 — Medium — Lifecycle observer registered in `LaunchedEffect` with no removal

`app/src/main/java/com/peal/appscheduler/MainActivity.kt:53-59`

```kotlin
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            showDialog = !Settings.canDrawOverlays(context)
        }
    })
}
```

There is no `removeObserver`. C6 violation: registration belongs in a `DisposableEffect` with an
`onDispose`. Because the key is `lifecycleOwner`, every time the effect restarts a *new* anonymous
observer is added and the old one is never detached, so observers accumulate on the Activity's
lifecycle registry. The leak is bounded by the Activity's own lifetime, which is why this is Medium and
not High — but it is the wrong primitive, and the correct pattern is used six files away
(`SchedulerScreen.kt:116-129`), so this is inconsistency rather than a considered tradeoff.

### CMP-7 — Medium — `ObserveAsEvents` wraps the wrong value in `rememberUpdatedState`

`app/src/main/java/com/peal/appscheduler/core/presentation/util/ObserveAsEvents.kt:19-31`

```kotlin
val lifecycleOwner by rememberUpdatedState(LocalLifecycleOwner.current)
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        events.collectLatest { event -> onEvent(event) }
    }
}
```

This is backwards. `lifecycleOwner` is already the effect's *key*, so wrapping it in
`rememberUpdatedState` accomplishes nothing. The value that actually needs it — `onEvent` — is captured
directly by the `LaunchedEffect` block and is therefore **frozen at the composition that started the
effect**. `HandleSchedulerEvents` (`SchedulerScreen.kt:289-360`) passes a fresh trailing lambda on every
recomposition; only the first one is ever invoked.

Today this is benign: the captured lambda closes over `context`, which does not change. It is filed as
Medium because it is a live trap — the moment anyone makes the handler depend on changing state (a
snackbar host, a nav callback), events will silently act on stale values, and the bug will look like a
Compose bug rather than a two-line one here.

Second, smaller point: `events` itself is not a key, so if the caller ever swaps the flow instance the
effect will not restart. And `collectLatest` cancels the previous handler when a new event arrives —
safe only because the handlers here are synchronous `Toast` calls. With any suspending handler, events
would be dropped.

### CMP-8 — Medium — One-shot effects are delivered over a zero-buffer `MutableSharedFlow`, so events with no live collector are dropped

`ui/screens/schedule/SchedulerViewModel.kt:52-53`
`ui/screens/home/HomeViewModel.kt:35-36`
`ui/screens/deviceApps/DeviceAppsViewModel.kt:32-33`

All three use `MutableSharedFlow<Effect>()` — default `replay = 0`, `extraBufferCapacity = 0`. With zero
subscribers and zero replay, `emit` returns immediately and the value is discarded.

`ObserveAsEvents` only subscribes while the lifecycle is `STARTED`. So: user taps Save →
`SchedulerViewModel.insertSchedule` launches, `delay(500)` (`SchedulerViewModel.kt:161`), then emits
`AppScheduled`. If the user backgrounds the app inside that 500 ms window, `repeatOnLifecycle` has
already torn down the collector, the effect is dropped, and the confirmation toast never appears.
The consequence today is a lost `Toast`, which is why this is Medium, not High — but the same channel
carries `ScheduleCancelled` and every error branch.

Telling detail: all three ViewModels **import `kotlinx.coroutines.channels.Channel` and
`kotlinx.coroutines.flow.receiveAsFlow`** (`SchedulerViewModel.kt:20,26`; `HomeViewModel.kt:11,17`;
`DeviceAppsViewModel.kt:7,14`) and use neither. The buffered-`Channel` pattern — the one that does not
drop — was there and was replaced.

### CMP-9 — Medium — Two screens collect effects with a lifecycle-unaware `LaunchedEffect(Unit)`

`ui/screens/home/HomeScreen.kt:36-48`
`ui/screens/deviceApps/DeviceAppsListScreen.kt:33-43`

Both do `LaunchedEffect(Unit) { viewModel.effect.collect { ... navigator.navigate(...) } }`. Unlike
`SchedulerScreen`, which routes through `ObserveAsEvents`, these keep collecting for the whole lifetime
of the composition regardless of lifecycle state — so a navigation effect emitted while the screen is
`STOPPED` will mutate the back stack while the app is in the background. Three screens, two different
event-plumbing mechanisms, in a codebase with a purpose-built `ObserveAsEvents` helper that only one of
them uses.

### CMP-10 — Medium — Keyless `remember` permanently captures the first `onSave`/`onCancel` lambda

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt:254-256`

```kotlin
val coroutineScope = rememberCoroutineScope()
val debouncedSave = remember { onSave.debounce(coroutineScope) }
val debouncedCancel = remember { onCancel.debounce(coroutineScope) }
```

`onSave` and `onCancel` are fresh lambda instances on every recomposition of `SchedulerScreen`
(`:199-204`), but the `remember` has no keys, so the wrappers close over the very first pair forever.
Same class of bug as CMP-7 and currently benign for the same reason — the captured lambdas transitively
close over `schedulerViewModel`, which is stable. Should be keyed on the callbacks or built on
`rememberUpdatedState`.

Related, `ui/utils/ExtensionsUI.kt:13-26`: `debounce` invokes the action *first* and then delays, and
drops calls while the job is active. That is a throttle / leading-edge guard, not a debounce. It
behaves correctly for the double-tap protection it is used for; the name is just wrong. (Low, noted
here rather than filed separately.)

### CMP-11 — Medium — `@Immutable` is asserted on state whose object graph contains a mutable `Drawable`

`ui/screens/home/HomeContract.kt:7-11` · `ui/screens/deviceApps/DeviceAppsContract.kt:8-12` ·
`ui/screens/schedule/ScheduleContract.kt:10-18`
→ `ui/model/ScheduleAppInfoUi.kt:9-17` (`val icon: Drawable?`)
→ `domain/model/DeviceAppInfo.kt:11-16` (`val icon: Drawable?`)

All three `State` classes are annotated `@Immutable`, and all three transitively hold
`android.graphics.drawable.Drawable`, which is a mutable platform type with no `equals` override.
`@Immutable` is a promise to the compiler that the value's public properties will never change after
construction and that `equals` is a valid substitute for identity. That promise is false here. Per the
phase reference: annotating a lying class is worse than the original problem — Compose will skip
recomposition on the strength of an assertion the code does not honour. Concretely, an animated or
stateful `Drawable` mutating in place would never repaint.

Consequence today is small (launcher icons are effectively static), which is why this is Medium.

Separately, and **unverified**: because `Drawable` is unstable, `ScheduleAppInfoUi` and `DeviceAppInfo`
are inferred unstable, which should make `ScheduledAppItem` (`ScheduledAppItem.kt:33`) and
`InstalledAppItem` (`DeviceAppItem.kt:26`) **restartable but not skippable** — every list row
recomposes whenever its parent does. `AppSection` compounds it by constructing a brand-new
`DeviceAppInfo` in the composable body on each pass (`SchedulerScreen.kt:244`,
`scheduleAppInfo.toDeviceAppInfo()`). The clean fix is to stop putting `Drawable` in UI models at all
(carry the package name, resolve the icon in a painter/image loader). Enabling
`-Pkotlin.compose.reports` and reading `*-classes.txt` / `*-composables.txt` would confirm the
unstable/unskippable classification in one build.

Note the state classes also expose bare `List<T>` (`HomeContract.kt:10`, `DeviceAppsContract.kt:11`),
which contradicts CLAUDE.md rule 5. The `@Immutable` on the wrapper masks it, but
`kotlinx-collections-immutable` is not on the dependency list, so there is currently no `ImmutableList`
available — flagging for the build phase.

### CMP-12 — Medium — Platform lookup and permission policy live in the composable body

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt:111, 116-129`

```kotlin
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
```

Unremembered, so the service lookup and unchecked cast re-run on every recomposition of
`SchedulerScreen`. Cheap individually, but it is an unnecessary per-recomposition platform call and the
value should be `remember(context) { ... }` at minimum.

More substantively, the `DisposableEffect` at `:116-129` puts exact-alarm permission policy — an SDK
version check plus `alarmManager.canScheduleExactAlarms()` — in the UI layer. Cleanup is correct, so
this is a layering finding, not a leak. The observer also closes over the first `alarmManager` instance
(the effect keys only on `lifecycleOwner`); harmless since it is the same system service, but it is the
same stale-capture shape as CMP-7 and CMP-10 appearing a third time.

### CMP-13 — Low — Identical `LaunchedEffect(appInfo)` duplicated verbatim

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt:83-89`

```kotlin
LaunchedEffect(appInfo) { schedulerViewModel.updateAppInfo(appInfo) }
LaunchedEffect(appInfo) { schedulerViewModel.updateAppInfo(appInfo) }
```

Two effects, same key, same body. `updateAppInfo` (`SchedulerViewModel.kt:57-66`) is idempotent and
`MutableStateFlow` conflates equal values, so the second run produces no emission and there is no
user-visible symptom — hence Low, not Medium. It is an unambiguous copy-paste defect and should just be
deleted.

### CMP-14 — Low — `LazyColumn` item without a `key`

`app/src/main/java/com/peal/appscheduler/ui/screens/deviceApps/DeviceAppsListScreen.kt:78`

`items(installedApps) { app -> ... }`. The installed-apps list is loaded once
(`DeviceAppsViewModel.kt:39-54`) and never reordered or filtered, so per the phase reference this is Low
rather than High. Adding `key = { it.packageName }` costs nothing and protects against a future search
or sort feature silently reusing the wrong item state. `HomeScreen.kt:83` already does this correctly.

### CMP-15 — Low — `asImageBitmap()` is called outside `remember`

`app/src/main/java/com/peal/appscheduler/ui/shared/components/AppIcon.kt:32`

`bitmap?.asImageBitmap()?.let { ... }` sits outside the `remember` on line 30. I disassembled
`androidx.compose.ui.graphics.AndroidImageBitmap_androidKt.asImageBitmap` and confirmed it is an
unconditional `new AndroidImageBitmap(bitmap)` — no caching. So a fresh, non-equal wrapper is produced
on every recomposition and handed to `Image`, whose internal `remember(bitmap) { BitmapPainter(...) }`
key therefore changes every time, allocating a new painter per pass. Small, but it is inside the
per-row path of CMP-1. Fold the conversion into the existing `remember(icon)`.

### CMP-16 — Low — `InstalledAppItem` is unconditionally clickable with a no-op default handler

`app/src/main/java/com/peal/appscheduler/ui/screens/deviceApps/DeviceAppItem.kt:26-36` (used at
`ui/screens/schedule/SchedulerScreen.kt:244`)

`InstalledAppItem` hardcodes `.clickable { onClick.invoke(app) }` on its root and defaults
`onClick: (DeviceAppInfo) -> Unit = {}`. `AppSection` reuses it read-only and supplies no handler, so
the scheduler screen renders a row that ripples on touch, is focusable, and is announced to TalkBack as
a button, but does nothing. Direct consequence of the missing `Modifier` parameter (CMP-5) — with one,
the caller could simply omit `clickable`. The accessibility half is cross-referenced to phase 7.

### CMP-17 — Low — Wall-clock read in a composable body

`app/src/main/java/com/peal/appscheduler/ui/shared/components/TimePickerDialog.kt:23`

`val currentTime = LocalTime.now()` is unremembered. It only seeds `rememberTimePickerState`
(`:24-27`), whose own `remember` ignores changed initial values, so there is no behavioural bug — but a
non-deterministic value read during composition is the kind of thing that becomes one. Wrap in
`remember`.

### CMP-18 — Low — Dead navigation code and stale Navigation-2 imports

`app/src/main/java/com/peal/appscheduler/ui/shared/navigation/AppSchedulerNavHost.kt:6-11, 20`

Seven unused imports survive the Nav2→Nav3 migration: `hiltViewModel`, `NavHostController`, `NavHost`,
`composable`, `rememberNavController`, `toRoute`, and `SharedDeviceAppViewModel` (`NavKey` on `:12` is
also unused in this file).

`ui/shared/viewModel/SharedDeviceAppViewModel.kt:13` is dead: it is `@Inject constructor` but **not**
`@HiltViewModel`, and its only reference in the entire source tree is that unused import. Anyone who
later tries `hiltViewModel<SharedDeviceAppViewModel>()` will get a runtime failure, because the
annotation needed to make it constructible is absent. Delete it, or annotate it, but do not leave it.

Related and deliberately not filed as a finding: `AppSchedulerNavHost.kt:35` rebuilds the
`entryProvider` map on every recomposition rather than wrapping it in `remember`. Google's own Nav3
samples build it inline, `AppSchedulerNavHost` has no state that recomposes it frequently, and I did not
measure any cost — noting it as an observation, not a defect.

---

## Deferred to other phases

- `HomeViewModel` holding an injected `@ApplicationContext` (`HomeViewModel.kt:30`) — CLAUDE.md rule 6.
  → phase 1.
- No dispatcher injection in any ViewModel — CLAUDE.md rule 7. → phase 1 / 3.
- `catch { e -> e.printStackTrace() }` swallowing (`DeviceAppsViewModel.kt:45`, `ContextExt.kt:34`,
  `ImageUtils.kt:28-30`) — CLAUDE.md rule 8, and `ImageUtils` catches bare `Exception`. → phase 3.
- Hardcoded `"OK"` / `"Cancel"` strings (`TimePickerDialog.kt:35, 40`) and content descriptions
  supplied as literals (`HomeScreen.kt:71`, `ScheduledAppItem.kt:90`). → phase 7.
- Whether `SchedulerScreen`'s non-scrollable `Column` (`:146`) clips at large font scales. → phase 7.
- Zero Compose UI tests. → phase 6.

## Coverage and honesty

All 29 files under `ui/`, plus `ObserveAsEvents.kt` and `MainActivity.kt`, were read end to end. Two
API-level claims were verified against the actual artifacts rather than from memory (`NavDisplay`'s
`Modifier` parameter and `asImageBitmap`'s allocation behaviour) by disassembling the AARs in the Gradle
cache. Everything concerning recomposition counts, skippability, and the real cost of CMP-1 is
**unverified static reasoning**; compiler metrics plus a scroll macrobenchmark are what would settle it.
