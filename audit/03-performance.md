# 03 — Performance, Lifecycle & Memory (Audit Phase 3)

**Date:** 2026-09-04 · **Branch:** `claude-audit` @ `be0f508` · **Scope:** whole `:app` module.

## Method and its limits

- **No device or emulator was attached** and no Gradle task was run for this phase (baseline is in
  `audit/00-baseline.md`). Nothing was profiled, traced, or heap-dumped.
- Consequently **every claim about magnitude is unverified.** What *is* verified is the control flow:
  which thread a call runs on, whether a service can reach `stopSelf()`, whether a `remember` block
  allocates. Those are static certainties and are marked **verified (static)**.
- Every file listed in the task brief was read in full, plus all ViewModels, all repository impls, the
  DI modules, the DAO, the manifest, and the composables that consume icon state. 64 of 64 `src/main`
  Kotlin files were either read or confirmed irrelevant to this phase (theme, `Color.kt`, `Type.kt`,
  `Result`/`Error` primitives).
- `grep` for `GlobalScope`, `runBlocking`, `Handler(`, `Thread(`, `AsyncTask`, `collectAsState()`
  returned **zero hits in `src/main`**. Those classes of defect are genuinely absent.

## What is already good

- `Application.onCreate` is empty apart from `@HiltAndroidApp` (`AppSchedulerApp.kt:12`). No SDK init,
  no eager DB open, no disk read. Room opens lazily on first query. Startup cost from app code is
  effectively zero — do not "optimise" this.
- `AppSchedulerReceiver.onReceive` (`receiver/AppSchedulerReceiver.kt:18-43`) does **no** DB work, no
  coroutine launch, no `goAsync()` — it reads two intent extras and hands off to a service. This is the
  correct shape for a receiver and needs no change.
- `DeviceAppsRepositoryImpl` correctly puts the whole PackageManager enumeration on `Dispatchers.IO`
  via `flowOn` (`DeviceAppsRepositoryImpl.kt:40`). The expensive part is off the main thread.
- `SchedulerScreen.kt:116-129` registers a `LifecycleEventObserver` in a `DisposableEffect` **with** a
  matching `removeObserver` in `onDispose`. Correct.
- `ObserveAsEvents` (`core/presentation/util/ObserveAsEvents.kt:25-31`) uses `repeatOnLifecycle(STARTED)`.
  Correct — it is the other two effect collectors that are wrong (PERF-14).
- All Flow collection in composables uses `collectAsStateWithLifecycle()`. No `collectAsState()` anywhere.
- Room reads are `Flow`-based, so there are no one-shot main-thread queries.

---

## Findings

### PERF-1 — Critical — `RescheduleService` can never stop itself; foreground service runs forever after every boot

**`service/RescheduleService.kt:81-108`** (collect at `:84`, `finally { stopSelf() }` at `:105-107`)

`reschedulePendingApps()` calls `scheduleRepository.getScheduledAppsToReschedule(...)` which resolves to
`@Query("SELECT * FROM schedules WHERE status = :status") fun ...: Flow<List<ScheduleEntity>>`
(`data/local/ScheduleDao.kt:33-34`). A Room `Flow` query is an infinite observable — it never completes.
`collect { }` therefore never returns, the `try` block never exits, and the `finally` containing the only
`stopSelf()` in the class is unreachable.

Secondary effect: the loop body calls `scheduleRepository.updateScheduleStatus(...)` (`:88-91`), which
writes to the `schedules` table, which invalidates the very query being collected, which re-emits and
re-runs the body. The write set converges (rows flipped to `FAILED` stop matching `status = SCHEDULED`),
so it is not an unbounded write loop, but the collection still never ends.

**Impact:** after every device reboot the app holds a foreground service, with a permanent
"Rescheduling apps" ongoing notification, until the user force-stops the app or the system kills the
process. Continuous process retention and battery drain; also a visible, undismissable notification.
**Verified (static)** — control flow only; the wall-clock battery cost is unmeasured.
*Confirm with:* `adb shell dumpsys activity services com.peal.appscheduler` a minute after
`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.peal.appscheduler`; the service
will still be listed as foreground.

---

### PERF-2 — Critical — `RescheduleService` declares no `foregroundServiceType`; `startForeground` throws on Android 14+

**`AndroidManifest.xml:48-49`** (no `android:foregroundServiceType`) and **`service/RescheduleService.kt:61`**
(`startForeground(notificationId, notification)` from `onCreate` at `:42-45`)

`targetSdk` is 36 (`audit/00-inventory.md` §4). Since Android 14 (API 34), a service whose app targets
34+ must declare a `foregroundServiceType` in the manifest; calling `startForeground()` without one
raises `MissingForegroundServiceTypeException`. `AppLaunchService` declares `systemExempted`
(`AndroidManifest.xml:44-46`); `RescheduleService` declares nothing.

**Impact:** on any Android 14+ device, the `BOOT_COMPLETED` path
(`AppSchedulerReceiver.kt:20-25` → `startForegroundService`) crashes the app in
`RescheduleService.onCreate` on every boot, and no pending schedule is ever re-armed. The app's core
"survives reboot" promise is dead on modern devices.
**Unverified** only in the sense that it was not observed at runtime — the manifest and the
`startForeground` call site were both read.
*Confirm with:* install on an API 34+ device/emulator, then
`adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.peal.appscheduler` and watch
`adb logcat -s AndroidRuntime`. Also `./gradlew lintRelease` did **not** catch this (0 lint errors in
`audit/00-baseline.md`), so lint is not a safety net here.

---

### PERF-3 — High — `AppLaunchService` orphans itself on the two failure paths

**`service/AppLaunchService.kt:160-175`**, reached from `:65-70` and `:79-83`

`stopSelf()` lives only inside `updateScheduleStatus`, and only inside the
`if (scheduleId != null && scheduleId != -1L)` guard at `:161`. Both early-return paths in
`onStartCommand` (`packageName` null/empty at `:58-71`; app not installed at `:73-84`) call
`updateScheduleStatus` with whatever `scheduleId` arrived. If the extra is missing, `getLongExtra`
returns the `-1L` default (`:55`), the guard fails, nothing is launched, and the service returns
`START_NOT_STICKY` while still in the foreground.

**Impact:** a foreground service with an ongoing "Launching scheduled app" notification that never goes
away and never releases the process. Same class of user-visible symptom as PERF-1 but on a rarer path.
**Verified (static).**

---

### PERF-4 — High — `PackageManager.getApplicationIcon` runs on the main thread, once per scheduled item, on every database emission

**`ui/screens/home/HomeViewModel.kt:44-52`** → **`domain/mappers/Mappers.kt:16-27`** →
**`ui/utils/ContextExt.kt:30-38`**

`viewModelScope` is `Dispatchers.Main.immediate`. `fetchScheduledApps()` does
`getScheduledAppUseCase().collectLatest { scheduledApps -> ... scheduledApps.map { it.toScheduleAppInfoUi(context) } }`
with no `flowOn`. Room emits on its query executor but the coroutine resumes on Main, so the `map` body
runs on Main. `toScheduleAppInfoUi` calls `context.getAppIconDrawable(packageName)` →
`packageManager.getApplicationIcon(packageName)` — a synchronous binder round-trip to
`system_server` plus a cross-package resource load and full bitmap decode, **per row**.

This re-runs on every emission of `SELECT * FROM schedules`, i.e. after every insert, every status
update, and every cancel.

**Impact:** frame drops / ANR risk on the Home screen proportional to the number of schedules; worst on
first frame after cold start, when it is on the critical path. `HomeViewModel` also injects
`@ApplicationContext Context` (`:30`), which is the root cause and violates CLAUDE.md rule 6.
**Verified (static)** that this is main-thread binder + decode. The *duration* is unverified.
*Confirm with:* enable `StrictMode.ThreadPolicy` with `detectAll()` in debug — it will flag the resource
load; or a systrace/Perfetto capture of the Home screen showing `getApplicationIcon` on the main thread.

---

### PERF-5 — High — Synchronous icon load inside composition on the Scheduler screen

**`ui/screens/schedule/SchedulerScreen.kt:79-81`**

```kotlin
val appInfo = remember(route, context) {
    route.toScheduleAppInfoUi(context)   // -> getApplicationIcon(packageName)
}
```

`remember { }` runs its calculation on the composing (main) thread during the first composition of the
screen. This is exactly the "no I/O, no expensive `remember`" rule in CLAUDE.md §3. Same binder +
resource-decode cost as PERF-4, now on the navigation transition.

**Impact:** a stall on every navigation into the scheduler, i.e. dropped frames during the enter
animation. **Verified (static)** that the call is synchronous, on main, inside composition.
*Confirm with:* Compose recomposition/composition timing in the Android Studio profiler, or StrictMode.

---

### PERF-6 — High — Bitmap allocation and `Drawable.draw()` on the main thread inside composition, per list row

**`ui/shared/components/AppIcon.kt:30`** → **`ui/utils/ImageUtils.kt:14-33`**

```kotlin
val bitmap = remember (icon) { icon?.let { drawableToBitmap(it) } }
```

`drawableToBitmap` takes the non-`BitmapDrawable` branch for adaptive icons (API 26+, i.e. everything
modern): it allocates `Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, ARGB_8888)`
(`ImageUtils.kt:19-23`), constructs a `Canvas`, and rasterises the drawable (`:24-26`). All of this runs
inside `remember`, i.e. on the main thread during composition.

`AppIcon` is used from `InstalledAppItem` (`ui/screens/deviceApps/DeviceAppItem.kt:39-42`) inside a
`LazyColumn` (`DeviceAppsListScreen.kt:78`) and from `ScheduledAppItem`
(`ui/screens/home/ScheduledAppItem.kt:51-56`) inside another `LazyColumn` (`HomeScreen.kt:81-91`).
`LazyColumn` disposes off-screen items, which discards the `remember` — so scrolling back re-rasterises.

Also note the icon is rendered at a fixed `48.dp` (`AppIcon.kt:35`, `:43`) but the bitmap is allocated at
the drawable's full intrinsic size, so the app pays for a large bitmap and then downscales it.

**Impact:** allocation churn and per-frame rasterisation during list scrolling → scroll jank and GC
pressure. **Verified (static)** that the allocation is on the main thread inside composition; the
resulting frame time is **unverified**.
*Confirm with:* `adb shell dumpsys gfxinfo com.peal.appscheduler framestats` while flinging the device
apps list, or a macrobenchmark `scrollAndWait` with `FrameTimingMetric`.

---

### PERF-7 — High (unverified magnitude) — Every installed app's icon is eagerly decoded and retained in ViewModel state

**`data/repositoryImpl/DeviceAppsRepositoryImpl.kt:24-40`**

```kotlin
val apps = packageManager.queryIntentActivities(intent, 0).map {
    ...
    icon = appInfo.loadIcon(packageManager)   // :35  — decodes now, for every app
}.sortedBy { it.name }
emit(apps)                                    // :39  — single emission, after all of them
```

Three separate problems in eight lines:

1. **Eager, all-or-nothing.** One `emit` after the whole list is built. The user stares at
   `CommonCircularProgressIndicator` (`DeviceAppsListScreen.kt:58`) until the last icon of the last app
   has been decoded. No incremental emission, no paging.
2. **Retained.** The resulting `List<DeviceAppInfo>` — each holding a live `Drawable`
   (`domain/model/DeviceAppInfo.kt:15`) — is stored in `DeviceAppsContract.State.deviceApps`
   (`ui/screens/deviceApps/DeviceAppsContract.kt:11`) and held for the ViewModel's whole lifetime.
   Every installed app's icon bitmap is resident simultaneously, not just the visible ones. On a phone
   with 150–250 launcher entries and adaptive icons at xxhdpi this is tens to hundreds of MB.
3. **Uncached.** `getDeviceApps()` is a cold `flow { }`. `DeviceAppsViewModel.init` calls it
   (`DeviceAppsViewModel.kt:35-37`), and a fresh ViewModel is created each time the screen is pushed —
   so the full enumeration and decode repeats on every visit.

There is no image loading library in the project (`audit/00-inventory.md` §5 confirms no Coil/Glide),
so there is no cache, no size hint, and no eviction.

**Impact:** long unresponsive loading state on the app-picker screen, large sustained heap, and plausible
`OutOfMemoryError` on low-RAM devices with many apps.
**Unverified** — the thread (IO) is correct and the eagerness is certain, but heap size and load
duration were not measured.
*Confirm with:* `adb shell dumpsys meminfo com.peal.appscheduler` while the device apps list is open,
compared against the Home screen; and an Android Studio heap dump filtered on `android.graphics.Bitmap`
to get retained byte count. Time the load with a `Trace.beginSection` around `getDeviceApps`.

---

### PERF-8 — High — `setExact` instead of `setExactAndAllowWhileIdle`: alarms are deferred in Doze

**`data/wapper/AlarmManagerWrapper.kt:43`**

```kotlin
alarmManager.setExact(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent)
```

`setExact` is subject to Doze deferral: if the device is idle at the scheduled instant, delivery slips
to the next maintenance window (potentially many minutes, or longer on Doze deep idle). The app already
holds `SCHEDULE_EXACT_ALARM` (`AndroidManifest.xml:6`) and correctly gates on `canScheduleExactAlarms()`
(`AlarmManagerWrapper.kt:26-30`), so the stronger API is available.

**Impact:** for an app whose only function is "launch this app at exactly this time", a phone left idle
overnight will fire the schedule late. Directly defeats the product.
**Verified (static)** that `setExact` is used. Doze deferral behaviour is documented platform behaviour.
*Confirm with:* `adb shell dumpsys deviceidle force-idle`, then wait past the scheduled time and check
`adb shell dumpsys alarm | grep appscheduler`.

---

### PERF-9 — High — `cancelSchedule` builds a non-matching `PendingIntent`; the alarm is never cancelled

**`data/wapper/AlarmManagerWrapper.kt:50-67`** vs **`:32-43`**

`scheduleApp` sets `action = "com.peal.ACTION_SCHEDULE_APP"` on the Intent (`:33`).
`cancelSchedule` builds an Intent with the same component and extras but **no action** (`:52-55`).
`PendingIntent` equality uses `Intent.filterEquals`, which compares action, data, type, component and
categories — extras are ignored, action is not. The two Intents are therefore not equal, so
`PendingIntent.getBroadcast(...)` at `:57-60` does not retrieve the armed PendingIntent; with
`FLAG_UPDATE_CURRENT` (and no `FLAG_NO_CREATE`) it **creates a new one**, and `alarmManager.cancel()`
at `:62` cancels that new, never-armed intent. The original alarm stays armed.

The request code also differs subtly: `scheduleId.hashCode()` at `:39` vs `scheduleId.toInt()` at `:58`.
These coincide for small positive `Long`s but diverge above `Int.MAX_VALUE`.

`updateSchedule` (`:69-88`) is built on top of `cancelSchedule`, so edits inherit the same failure. Its
"rollback" branch (`:75-77`) also re-invokes `scheduleApp(newScheduleTime)` — the same call it is
supposedly rolling back — so the rollback is a no-op regardless.

**Impact:** a schedule the user cancels still fires; the target app launches unexpectedly, and the
foreground service starts. Battery and trust cost. Marked here rather than in the architecture phase
because the consequence is stale wakeup alarms.
**Verified (static)** — both call sites read; `filterEquals` semantics are documented.
*Confirm with:* schedule, cancel, then `adb shell dumpsys alarm | grep -A5 appscheduler` — the alarm
will still be listed.

---

### PERF-10 — High (unverified) — `foregroundServiceType="systemExempted"` is almost certainly not a type this app qualifies for

**`AndroidManifest.xml:9, 45`** and **`service/AppLaunchService.kt:50`**

`FOREGROUND_SERVICE_SYSTEM_EXEMPTED` is reserved for apps in a narrow set of platform-exempted
categories (device owner/profile owner, VPN, safety apps, etc.). A general-purpose app-scheduling app is
not in that set. On Android 14+ the platform rejects the start, and Play Console policy rejects the
declaration.

**Impact:** if rejected, `AppLaunchService.onCreate` throws and no scheduled launch ever succeeds on
Android 14+ — the app's primary function, gone. Combined with PERF-2 this means both service paths are
suspect on modern devices.
**Unverified** — I could not reach `android docs` (knowledge-base download failed in this environment)
and had no device.
*Confirm with:* run on an API 34+ device, fire a schedule, and check logcat for
`ForegroundServiceStartNotAllowedException` / `SecurityException` from `AppLaunchService`. Note that the
declared type is also the reason `SYSTEM_ALERT_WINDOW` is being requested (`MainActivity.kt:56`) — the
whole background-activity-launch strategy should be re-examined together.

---

### PERF-11 — Medium — `provideDatabase` is unscoped: latent duplicate `RoomDatabase` instances

**`di/DatabaseModule.kt:18-25`** (and `provideScheduleDao` at `:27-30`, also unscoped)

Neither provider carries `@Singleton`, so each injection point gets a fresh
`Room.databaseBuilder(...).build()`. I traced consumers: the only one in `src/main` is
`ScheduleRepositoryImpl` (`data/repositoryImpl/ScheduleRepositoryImpl.kt:20`), which *is* `@Singleton`
via `RepositoryModule`'s `@Binds`. So today exactly one `SchedulerAppDatabase` is constructed per
process, and both services share it through the singleton repository.

**Impact today:** ~none. **Impact on the next change:** the moment a second class injects `ScheduleDao`
or `SchedulerAppDatabase` (a WorkManager worker, a second repository, a debug screen), it gets a
*second* `RoomDatabase` object over the same file — separate `InvalidationTracker`s, so `Flow` queries
on one instance will not be invalidated by writes through the other, and a second connection pool is
opened. That is a silent stale-UI bug that is very hard to diagnose. A one-word fix now.
**Verified (static)** — grep for all `ScheduleDao`/`SchedulerAppDatabase` references confirms the single
production consumer.

---

### PERF-12 — Medium — Lifecycle observer registered in `LaunchedEffect` with no matching removal

**`MainActivity.kt:53-59`**

```kotlin
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver { ... })
}
```

No `DisposableEffect`, no `removeObserver`. The anonymous observer captures `context` and the
`showDialog` setter, and is retained by the Activity's `Lifecycle` for as long as the Activity lives.

Because the `LifecycleOwner` here *is* the Activity, the retained graph does not outlive the Activity,
so this is not a classic leak — it is bounded. But it is unbalanced registration, it accumulates one
observer per composition entry, and the observer keeps mutating composition state after the composable
has left. The correct pattern is already used 70 lines away in `SchedulerScreen.kt:116-129`.

Also on this path: `Settings.canDrawOverlays(context)` (`MainActivity.kt:56`) is a binder call executed
on every `ON_RESUME` on the main thread — cheap, but worth knowing.

**Impact:** unbalanced registration; state writes from a disposed composition. Medium, not High, because
the retention is Activity-bounded.
**Verified (static).** *Confirm the leak question with:* add LeakCanary to the debug build (it is not in
the project — see PERF-16) and rotate the device repeatedly.

---

### PERF-13 — Medium — `entryProvider` rebuilt on every recomposition of the nav host

**`ui/shared/navigation/AppSchedulerNavHost.kt:35`**

```kotlin
val entryProvider = entryProvider { ... }   // not remembered
```

`navigationState` and `navigator` above it are correctly `remember`ed (`:31`, `:33`), but the
`entryProvider` builder — which allocates a map and three entry lambdas — is not. `AppSchedulerNavHost`
recomposes whenever the `NavBackStack` snapshot list read in `toEntries` (`NavigationState.kt:31-35`)
changes, i.e. on every navigation. `rememberDecoratedNavEntries` receives a new `entryProvider` lambda
identity each time.

**Impact:** allocation and probable re-derivation of nav entries per navigation. Not per-frame, so not
High. **Unverified** whether this actually forces entry recreation in Navigation 3.
*Confirm with:* Compose compiler metrics (not currently enabled — see `audit/00-inventory.md` §11) plus
recomposition counts in the Layout Inspector while navigating back and forth.

---

### PERF-14 — Medium — Effect flows collected with a non-lifecycle-aware `LaunchedEffect`

**`ui/screens/home/HomeScreen.kt:36-48`** and **`ui/screens/deviceApps/DeviceAppsListScreen.kt:33-43`**

Both do `LaunchedEffect(Unit) { viewModel.effect.collect { ... } }`. `LaunchedEffect` is tied to
composition, not lifecycle — collection continues while the screen is `STOPPED`. Navigation effects
(`navigator.navigateToDeviceAppsList()`, `navigateToAppScheduler(...)`) can therefore be applied to the
back stack while the app is in the background.

The project already contains the correct utility — `ObserveAsEvents`
(`core/presentation/util/ObserveAsEvents.kt:25-31`, `repeatOnLifecycle(STARTED)`) — and `SchedulerScreen`
uses it (`SchedulerScreen.kt:131`). These two screens just don't.

**Impact:** back-stack mutation while stopped; wasted collection in the background. Medium.
**Verified (static).**

---

### PERF-15 — Medium — `SavedStateHandle` injected but never used; picker state lost on process death

**`ui/screens/schedule/SchedulerViewModel.kt:43`** (injected), **`:49-50`** (plain fields)

```kotlin
private val handle: SavedStateHandle,     // :43 — never read anywhere in the class
private var selectedDate: LocalDate? = null   // :49
private var selectedTime: LocalTime? = null   // :50
```

`_schedulerScreenState` (`:46`) is likewise a plain `MutableStateFlow`. The nav route arguments do
survive (`rememberNavBackStack` is saveable, and `AppSchedulerScreen` is `@Serializable` —
`Screens.kt:17-25`), so the *app being scheduled* is restored. What is lost is the date and time the
user has picked but not yet saved.

**Impact:** on low-memory process death, a user mid-way through picking a date and time returns to an
empty form with no indication anything was lost. Classic invisible-in-testing defect (checklist L7).
**Verified (static)** — grep confirms `handle` has no read site.
*Confirm with:* Developer Options → "Don't keep activities", or `adb shell am kill com.peal.appscheduler`
while backgrounded on the scheduler screen.

---

### PERF-16 — Medium — No baseline profile, no LeakCanary, no StrictMode

No `:baselineprofile` module and no `src/main/baseline-prof.txt` exist. (`find` hits under
`app/build/intermediates/.../baseline-prof.txt` are AndroidX library profiles merged by AGP, not an app
profile.) `grep` for `leakcanary`, `StrictMode`, `profileable` across `app/build.gradle.kts`,
`gradle/libs.versions.toml` and the manifest returns nothing.

Compounding: `isMinifyEnabled = false` for release (`audit/00-inventory.md` §8), so R8 does not run and
there is no dex layout optimisation either.

**Impact:** cold start and first-run scroll are slower than they need to be (the usual figure quoted for
baseline profiles is 20–30%, unverified for this app); and there is no cheap instrument in the project
for converting the leak hypotheses above into confirmed findings.
**Unverified.** *Confirm with:* a startup macrobenchmark (`StartupTimingMetric`, `COLD`) before/after
adding a baseline profile; `adb shell am start -W` for a crude number.

---

### PERF-17 — Medium — Dispatchers are hardcoded, not injected

**`data/repositoryImpl/DeviceAppsRepositoryImpl.kt:40`** (`flowOn(Dispatchers.IO)`),
**`service/AppLaunchService.kt:35`** and **`service/RescheduleService.kt:37`**
(`CoroutineScope(Dispatchers.IO + SupervisorJob())`)

The dispatcher choice is correct in all three places. The problem is that it is not swappable, which
violates CLAUDE.md rule 7 and is a direct cause of the zero unit-test coverage of these classes
(`audit/00-inventory.md` §7).

Both service scopes *are* cancelled in `onDestroy` (`AppLaunchService.kt:141`,
`RescheduleService.kt:117`) — that part is correct and is not a leak. (It only helps if `onDestroy` is
reached, which PERF-1 and PERF-3 prevent.)

**Impact:** untestable async code. No runtime cost. **Verified (static).**

---

### PERF-18 — Low — Artificial `delay(500)` on both user-facing write paths

**`ui/screens/schedule/SchedulerViewModel.kt:106`** (cancel) and **`:161`** (save)

Half a second of deliberate latency inserted before the repository call, apparently to make the loading
spinner visible. There is also a `debounce(waitMs = 500)` wrapper on both buttons
(`SchedulerScreen.kt:255-256` → `ui/utils/ExtensionsUI.kt:13-26`), so the two stack.

**Impact:** every save and cancel is ≥500 ms slower than it needs to be, by construction.
**Verified (static).**

---

### PERF-19 — Low — Cluster: minor composition-cost and correctness-of-annotation issues

| Where | What |
|---|---|
| `ui/screens/schedule/SchedulerScreen.kt:83-89` | The **same** `LaunchedEffect(appInfo) { schedulerViewModel.updateAppInfo(appInfo) }` is written twice, back to back. Two coroutines, two identical state updates on every entry. Almost certainly a copy-paste slip. |
| `ui/screens/schedule/SchedulerScreen.kt:111` | `context.getSystemService(Context.ALARM_SERVICE) as AlarmManager` in a composable body, not `remember`ed — re-fetched on every recomposition. Cheap, but free to fix. |
| `ui/screens/deviceApps/DeviceAppsListScreen.kt:78` | `items(installedApps)` with no `key`. `HomeScreen.kt:81-83` gets this right (`key = { it.id }`). |
| `ui/screens/deviceApps/DeviceAppsContract.kt:9-13` and `ui/screens/home/HomeContract.kt:7-11` | `@Immutable` on a state whose element type holds a `Drawable` (`DeviceAppInfo.kt:15`, `ScheduleAppInfoUi.kt:13`) — a mutable object that `ImageUtils.kt:25` actively mutates via `setBounds`. The annotation is a promise to the compiler that the code does not keep. It will suppress recomposition that may be needed. |
| `data/local/ScheduleDao.kt:30, 33` | `status` is used in `WHERE` on two queries with no index. Irrelevant at current table sizes (a handful of rows) — recorded for completeness, not action. |

**Impact:** individually negligible. Listed as one cluster so they can be swept in a single commit.
**Verified (static)** for each.

---

## Coverage statement

Read in full for this phase: `DeviceAppsRepositoryImpl.kt`, `ScheduleRepositoryImpl.kt`,
`AlarmManagerWrapper.kt`, `ScheduleDao.kt`, `SchedulerAppDatabase.kt`, `ScheduleEntity.kt`,
`ImageUtils.kt`, `ContextExt.kt`, `ExtensionsUI.kt`, `AppLaunchService.kt`, `RescheduleService.kt`,
`AppSchedulerReceiver.kt`, `AndroidManifest.xml`, `MainActivity.kt`, `AppSchedulerApp.kt`, all three DI
modules, all four use cases, `Mappers.kt`, all four ViewModels, all three `*Contract.kt`, `AppIcon.kt`,
`DeviceAppItem.kt`, `ScheduledAppItem.kt`, `HomeScreen.kt`, `DeviceAppsListScreen.kt`,
`SchedulerScreen.kt`, `ObserveAsEvents.kt`, and the four navigation files.

Not examined (judged irrelevant to performance/lifecycle): `ui/theme/*`, `core/domain/util/*`,
`domain/utils/DateTimeExtensions.kt`, `domain/enums`, `CommonAlertDialog.kt`,
`CommonCircularProgressIndicator.kt`, `DatePickerDialog.kt`, `TimePickerDialog.kt`.

**Nothing in this phase was measured.** The severity ordering above rests on control-flow analysis. If
only one measurement is possible, make it the `BOOT_COMPLETED` path on an API 34+ device — PERF-1,
PERF-2 and PERF-10 all resolve there.
