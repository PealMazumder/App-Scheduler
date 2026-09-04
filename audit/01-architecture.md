# 01 — Architecture (Audit Phase 1)

**Date:** 2026-09-04 · **Scope:** all 64 files under `app/src/main/java/com/peal/appscheduler/` — read, not sampled (3.1k lines).
**Not run:** no Gradle builds (baseline in `audit/00-baseline.md`), no device. Runtime claims are marked **unverified**.
**Deferred:** exported-receiver attack surface, `SYSTEM_ALERT_WINDOW`, backup rules → Phase 4. Recomposition/stability → Phase 2. Memory retention → Phase 3.

## Summary

The layer *names* are right (ui / domain / data, interfaces in `domain/repository`, impls in `data/repositoryImpl`, Hilt at the root)
but three of the arrows point the wrong way and the real business logic — cancel, launch, reschedule — lives in the
service/receiver layer where it bypasses the domain layer entirely and is where the serious defects are. The MVI contract
files are consistent and readable; the DI graph is small and mostly correct. The critical findings are all in the
alarm/service path, not in the UI.

What is good and should not be touched: `core/domain/util/Result.kt` (clean typed result), the `*Contract.kt` convention,
`Screens.kt` type-safe `@Serializable` NavKeys, `data/mappers`, `ScheduleDao`, `RepositoryModule`.

---

## ARCH-1 — Critical — Cancelling a schedule does not cancel the alarm; the app still launches

`app/src/main/java/com/peal/appscheduler/data/wapper/AlarmManagerWrapper.kt:50` (vs `:32`)

`scheduleApp` builds its `Intent` with `action = "com.peal.ACTION_SCHEDULE_APP"` (line 33). `cancelSchedule` builds an
otherwise-identical `Intent` **without any action** (lines 52-55). `PendingIntent` matching uses `Intent.filterEquals`,
which compares the action; a null action never matches `"com.peal.ACTION_SCHEDULE_APP"`. So
`PendingIntent.getBroadcast(...FLAG_UPDATE_CURRENT)` at line 57 creates a *new* PendingIntent instead of retrieving the
armed one, and `alarmManager.cancel(pendingIntent)` (line 62) cancels nothing.

The alarm still fires at the scheduled time, `AppSchedulerReceiver` starts `AppLaunchService`, and
`AppLaunchService.onStartCommand` (`service/AppLaunchService.kt:86`) launches the app with **no check of the schedule's
status** — it then overwrites the row's `CANCELLED` status with `EXECUTED` (line 88-91). The user cancels, is shown
"Schedule cancelled successfully" (`SchedulerScreen.kt:343`), and the app launches in their face anyway.

Edit still works by luck: `updateSchedule` calls the broken `cancelSchedule` and then `scheduleApp`, whose
`FLAG_UPDATE_CURRENT` + `setExact` on the same request code replaces the alarm.

**Impact:** the primary destructive user action silently does nothing. Unwanted app launch at an arbitrary time.
**Unverified at runtime** — confirm by scheduling, cancelling, and waiting for the alarm on a device.

## ARCH-2 — Critical — `RescheduleService` never stops; foreground service runs forever after every boot

`app/src/main/java/com/peal/appscheduler/service/RescheduleService.kt:81`

`getScheduledAppsToReschedule()` returns a Room `Flow` (`ScheduleDao.kt:34`) which never completes. Line 84 uses
`.collect { }`, so the coroutine suspends forever, the `finally { stopSelf() }` at line 105 is never reached, and the
foreground service — started from `BOOT_COMPLETED` — stays alive with its ongoing notification until the process is
killed. Compounding: `currentTime` is captured once at line 79, so every subsequent DB emission re-evaluates stale
schedules and re-arms `scheduleApp` for every future row.

Should be a one-shot read (`.first()`). **Impact:** permanent foreground notification and battery drain after every
reboot. **Unverified at runtime** — confirm with `adb shell dumpsys activity services com.peal.appscheduler` after boot.

## ARCH-3 — Critical — `RescheduleService` calls `startForeground` with no declared `foregroundServiceType`

`app/src/main/AndroidManifest.xml:48`, `app/src/main/java/com/peal/appscheduler/service/RescheduleService.kt:44,61`

`AppLaunchService` declares `android:foregroundServiceType="systemExempted"` (manifest line 45); `RescheduleService`
declares no type at all, yet `onCreate` calls `startForeground(...)`. With `targetSdk = 36` (≥34) the platform throws
`MissingForegroundServiceTypeException`. The crash path is device boot, so it is invisible in normal development.

Note the same permission posture is questionable for `AppLaunchService`: `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` is only
grantable to apps in an exempted category; if this app does not qualify, `startForeground` throws `SecurityException`
every time an alarm fires on Android 14+.

**Impact:** crash on boot on Android 14+ (and plausibly on every scheduled launch). **Unverified at runtime** — confirm
on an API 34+ emulator: `adb reboot`, then `adb logcat | grep -i foregroundservice`.

## ARCH-4 — High — ViewModels are not scoped to nav entries; they are Activity-scoped and leak state between screens

`app/src/main/java/com/peal/appscheduler/ui/shared/navigation/NavigationState.kt:31`

`rememberDecoratedNavEntries` is called with exactly one decorator, `rememberSaveableStateHolderNavEntryDecorator()`.
`rememberViewModelStoreNavEntryDecorator()` is never used anywhere in `app/src` — verified by grep — even though
`androidx.lifecycle:lifecycle-viewmodel-navigation3` is a declared dependency (`app/build.gradle.kts:65`). I unpacked
`navigation3-runtime-1.0.0.aar` and `navigation3-ui-1.0.0.aar`: neither `DecoratedNavEntriesKt` nor any class in
`navigation3-ui` references `ViewModelStore`. So `hiltViewModel()` inside an entry resolves `LocalViewModelStoreOwner`
to the Activity: every screen ViewModel is created once per Activity and never cleared on pop.

Concrete consequence, combined with ARCH-11: `SchedulerViewModel` keeps `selectedDate` / `selectedTime` in private
`var`s (`SchedulerViewModel.kt:49-50`) that `updateAppInfo` does not reset. Schedule app A for tomorrow 09:00, go back,
open the scheduler for app B, press Save without picking anything → `insertSchedule` reads `selectedDate ?: ...`
(`:135-136`), finds app A's leftovers, and silently schedules **app B for app A's time** instead of showing
"select both date and time".

**Impact:** wrong schedules created silently; ViewModels and their retained `Drawable` state never released.
**Unverified at runtime** — confirm by logging `this.hashCode()` in `SchedulerViewModel.init` across two visits.

## ARCH-5 — High — `ScheduleAppUseCase` swallows `CancellationException` and reports success when the alarm was never armed

`app/src/main/java/com/peal/appscheduler/domain/usecase/ScheduleAppUseCase.kt:25`

Three defects in one 25-line function:

1. `runCatching { ... }.getOrElse { Result.Failure(UNKNOWN_ERROR) }` (lines 25, 48) wraps suspend calls and catches
   `Throwable`, including `CancellationException`. Cancelling `viewModelScope` turns into a fake "unknown error" and
   breaks structured concurrency (CLAUDE.md rule 8).
2. `alarmManagerRepository.updateSchedule(...)` (line 29) and `alarmManagerRepository.scheduleApp(...)` (line 39) both
   return `Result`; both are discarded. The DB row is written and `Result.Success` returned even if the alarm failed —
   e.g. the `SecurityException` thrown at `AlarmManagerWrapper.kt:28` when `SCHEDULE_EXACT_ALARM` is not granted. The
   user sees "App scheduled successfully" and nothing ever fires.
3. `.onError { Result.Failure(it) }` (lines 44-46) is dead code: `onError` returns `this`; the lambda's value is
   discarded. Error handling that looks like error handling and isn't.

**Impact:** silent scheduling failures on the app's core path.

## ARCH-6 — High — Domain layer depends on the UI layer and on Android framework types

- `app/src/main/java/com/peal/appscheduler/domain/mappers/Mappers.kt:3,7,8,9` — the domain package imports
  `android.content.Context`, `ui.model.ScheduleAppInfoUi`, `ui.shared.navigation.AppSchedulerScreen`, and
  `ui.utils.getAppIconDrawable`. Line 17 does synchronous `PackageManager` I/O inside a mapper.
- `app/src/main/java/com/peal/appscheduler/domain/model/DeviceAppInfo.kt:3,4,17` — the domain model holds an
  `android.graphics.drawable.Drawable` and carries a `toScheduleAppInfoUI()` method returning a UI type.
- `DeviceAppInfo` is then used directly as UI state (`DeviceAppsContract.kt:11`) and rendered
  (`ui/screens/deviceApps/DeviceAppItem.kt:27`), so the "domain model" is really a view model.

**Impact:** the domain layer is untestable on the JVM and cannot be extracted; every UI change can force a domain change.

## ARCH-7 — High — `HomeViewModel` holds a `Context` and does PackageManager I/O on the main dispatcher per emission

`app/src/main/java/com/peal/appscheduler/ui/screens/home/HomeViewModel.kt:30,49`

`@ApplicationContext private val context: Context` is a constructor dependency (CLAUDE.md rule 6), used at line 49 to
call `toScheduleAppInfoUi(context)` for every schedule on every DB emission. That mapper calls
`context.getAppIconDrawable(packageName)` → `PackageManager.getApplicationIcon` (`ui/utils/ContextExt.kt:33`), which
reads and inflates a drawable from another app's APK. `viewModelScope.launch` defaults to `Dispatchers.Main.immediate`,
so this happens on the main thread, N times, every time the schedules table changes. No dispatcher is injected anywhere
in the codebase (CLAUDE.md rule 7).

**Impact:** main-thread I/O proportional to schedule count; jank/ANR risk as the list grows. **Unverified magnitude** —
measure with a Systrace/main-thread StrictMode `detectDiskReads`.

## ARCH-8 — High — `AppLaunchService` can never call `stopSelf()`

`app/src/main/java/com/peal/appscheduler/service/AppLaunchService.kt:160`

`stopSelf()` exists only in the `finally` of the coroutine launched inside `updateScheduleStatus`, which is guarded by
`if (scheduleId != null && scheduleId != -1L)` (line 161). Every early return in `onStartCommand` (lines 70, 83) and
the normal path (line 88) routes through that method, so if the extra is missing or `-1L` — exactly the failure cases
the code is trying to handle at lines 58 and 65 — the foreground service is started, shows a notification, and never
stops.

**Impact:** stuck foreground service + notification on the malformed-intent path.

## ARCH-9 — Medium — Swallowed exceptions leave the UI stuck with no error state

- `ui/screens/deviceApps/DeviceAppsViewModel.kt:45` — `.catch { e -> e.printStackTrace() }` before `.collect`. If the
  app-list flow throws, nothing is ever emitted, `isLoading` stays `true`, and `DeviceAppsListScreen.kt:58` shows a
  spinner forever with no way out.
- `domain/usecase/CancelScheduledAppUseCase.kt:34` — `catch (e: Exception)` around a suspend call swallows
  `CancellationException`.
- Same pattern in `ui/utils/ContextExt.kt:34`, `ui/utils/ImageUtils.kt:28`,
  `domain/utils/DateTimeExtensions.kt:36,51,61,71` — all `printStackTrace()` and continue.
- No `Contract.State` has an error field (`HomeContract.kt:8`, `DeviceAppsContract.kt:9`, `ScheduleContract.kt:11`);
  failures can only be represented as "loading forever" or "empty".

## ARCH-10 — Medium — Three competing result types across one 3k-line app

- `core/domain/util/Result.kt:7` — the intended typed result, used by `ScheduleRepository` and the use cases.
- `domain/repository/AlarmManagerRepository.kt:8-10` — returns unqualified `Result<Unit>`, i.e. **`kotlin.Result`**,
  with a raw `Exception` payload the callers then discard (see ARCH-5).
- `domain/utils/ScheduleResult.kt:8` — a third sealed result type, entirely unused.

Also `ScheduleRepository.updateSchedule` / `updateScheduleStatus` (`domain/repository/ScheduleRepository.kt:18,20`)
return `Unit` and communicate failure by throwing, unlike `addSchedule` next to them.

**Impact:** every call site has to know which error convention it is in; errors get dropped at the seams.

## ARCH-11 — Medium — `SchedulerViewModel` state is split between the StateFlow and private mutable vars

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerViewModel.kt:43,49-50,55`

`selectedDate`, `selectedTime` and `previousScheduleTimeInMilli` live outside `ScheduleContract.State`, and
`insertSchedule` (`:135-136`) prefers them over the state — two sources of truth for the same value, with the
non-observable one winning. `SavedStateHandle` is injected at line 43 and never read, so nothing survives process
death. `updateAppInfo` (`:57`) is a public setter called directly from the composable
(`SchedulerScreen.kt:84`), bypassing the `Intent` channel the file otherwise defines.

## ARCH-12 — Medium — `DatabaseModule` is unscoped: a new Room database per injection point

`app/src/main/java/com/peal/appscheduler/di/DatabaseModule.kt:18,27`

Neither `provideDatabase` nor `provideScheduleDao` is `@Singleton`, so each requester gets a fresh `RoomDatabase` over
the same file, with its own connection pool and its own `InvalidationTracker` — writes through one instance do not
invalidate `Flow` queries on the other. Today only `ScheduleRepositoryImpl` (bound `@Singleton` in
`RepositoryModule.kt:27`) injects the DAO, so this is latent. The next injection point silently breaks live updates.

## ARCH-13 — Medium — One-shot effects use `MutableSharedFlow` with no buffer; events emitted with no subscriber are dropped

`HomeViewModel.kt:35`, `DeviceAppsViewModel.kt:32`, `SchedulerViewModel.kt:52`

All three effect streams are `MutableSharedFlow()` — `replay = 0`, no extra buffer. `SchedulerScreen` consumes them via
`ObserveAsEvents` (`core/presentation/util/ObserveAsEvents.kt:26`), which uses `repeatOnLifecycle(STARTED)`, so any
effect emitted while the screen is stopped is lost. `SchedulerViewModel` deliberately `delay(500)`s before emitting
(`:106,161`), which widens the window. All three ViewModels import `kotlinx.coroutines.channels.Channel` and
`receiveAsFlow` and use neither — the correct mechanism was started and abandoned.

Current blast radius is limited to lost Toasts; it becomes a navigation bug the moment an effect drives navigation.

## ARCH-14 — Medium — The service layer bypasses the domain layer it duplicates

`service/AppLaunchService.kt:33`, `service/RescheduleService.kt:31-35`

Both services inject `ScheduleRepository` / `AlarmManagerRepository` directly and implement the status-transition rules
inline (`AppLaunchService.kt:88-91`, `RescheduleService.kt:87-98`), while the UI path goes through use cases. So the
rules for "when does a schedule become FAILED/EXECUTED" exist in two places with no shared owner, and the domain layer
does not actually own the domain. This is also why ARCH-1's missing status check has nowhere obvious to live.

## ARCH-15 — Medium — Use cases that do not earn their existence, next to paths with none

`domain/usecase/GetScheduledAppUseCase.kt:16` and `domain/usecase/GetDeviceAppsUseCase.kt:16` are single-expression
pass-throughs to the repository. `ScheduleAppUseCase` and `CancelScheduledAppUseCase` do real orchestration and are
worth keeping. Combined with ARCH-14 the layer is applied inconsistently: ceremony where it isn't needed, absent where
it is.

## ARCH-16 — Medium — Framework work inside composables

`app/src/main/java/com/peal/appscheduler/ui/screens/schedule/SchedulerScreen.kt`

- `:79-81` — `remember(route, context) { route.toScheduleAppInfoUi(context) }` performs `PackageManager` icon loading
  during composition (CLAUDE.md rule 3).
- `:111` — `context.getSystemService(Context.ALARM_SERVICE) as AlarmManager` in the composable body, unremembered.
- `:83-89` — the identical `LaunchedEffect(appInfo) { schedulerViewModel.updateAppInfo(appInfo) }` block appears
  **twice** in a row; a copy-paste duplicate, harmless only because the call is idempotent.

## ARCH-17 — Medium — Exact-alarm permission gate uses the wrong API level

`ui/screens/schedule/SchedulerScreen.kt:119` gates the "grant exact alarm permission" dialog behind
`isAndroidTIRAMISUOrLater()` (API 33), but `canScheduleExactAlarms()` and the permission requirement start at API 31
(`AlarmManagerWrapper.kt:26` correctly checks `>= S`). On API 31–32 the user is never prompted, so scheduling throws
`SecurityException` at `AlarmManagerWrapper.kt:28` and surfaces as a generic "unexpected error" Toast (via ARCH-5's
`runCatching`). minSdk is 24, so these devices are in the supported range.

## ARCH-18 — Medium — Navigation routes carry denormalized schedule data

`ui/shared/navigation/Screens.kt:18-25` — `AppSchedulerScreen` carries `id`, `name`, `packageName`, `time`,
`utcScheduleTime`, `status`. `Mappers.kt:29` reconstitutes a UI model from it. The scheduler screen therefore renders a
snapshot of the DB taken at navigation time and never observes the row; anything that changes the schedule while the
screen is open (`AppLaunchService` firing, for instance) is invisible. Passing the id and observing the row is the
single-source-of-truth version.

## ARCH-19 — Low — Dead code and stale wiring

- `ui/shared/viewModel/SharedDeviceAppViewModel.kt:13` — never referenced anywhere except an unused import at
  `AppSchedulerNavHost.kt:20`. It also lacks `@HiltViewModel`, so `hiltViewModel()` would fail if anyone tried.
- `AppSchedulerNavHost.kt:6-11` — six Navigation-2 imports (`NavHostController`, `NavHost`, `composable`,
  `rememberNavController`, `toRoute`, `hiltViewModel`) plus `NavKey`/`rememberNavBackStack` are unused; the
  `navigation-compose` dependency itself is unused (see inventory §5).
- `AppSchedulerNavHost.kt:35` — `entryProvider { }` is rebuilt on every recomposition (not `remember`ed), unlike the
  `DialogSceneStrategy` on line 61 which is.
- `ScheduleContract.kt:16` — `message: String?` is never written or read.
- `domain/utils/ScheduleResult.kt` — unused (see ARCH-10).

## ARCH-20 — Low — `@Immutable` promises the compiler cannot keep

`HomeContract.kt:7`, `DeviceAppsContract.kt:8`, `ScheduleContract.kt:10` annotate state classes that hold `List<...>`
(unstable type) and, transitively, `android.graphics.drawable.Drawable` (`ScheduleAppInfoUi.kt:13`,
`DeviceAppInfo.kt:15`) — a mutable, non-equals-comparable object. `@Immutable` is an assertion the compiler trusts
without checking. Detail in Phase 2; recorded here because it is a state-modelling decision.

## ARCH-21 — Low — Cluster: small correctness/consistency defects

- `data/wapper/AlarmManagerWrapper.kt:69-88` — `updateSchedule`'s rollback is unreachable and wrong: `scheduleApp`
  returns a `Result` rather than throwing, so `catch (scheduleError: Exception)` can never run, and if it did the
  "rollback" re-applies `newScheduleTime`, not the old one.
- `data/wapper/AlarmManagerWrapper.kt:39` vs `:58` — PendingIntent request code is `scheduleId.hashCode()` when
  scheduling and `scheduleId.toInt()` when cancelling. Equal for ids below 2^31, divergent above.
- `domain/utils/DateTimeExtensions.kt:77-82` — `toUtcEpochMillis()` is an identity function (epoch millis are already
  absolute) yet is applied on every write in `data/mappers/ScheduleMappers.kt:16`.
- `data/wapper/AlarmManagerWrapper.kt:19` — `@Inject constructor(private val context: Context)` takes an unqualified
  `Context` with no such binding in the graph; it only compiles because `AppModule.kt:21` provides the type explicitly
  and Dagger prefers `@Provides` over the just-in-time binding. Deleting that `@Provides` yields a confusing error.
- `MainActivity.kt:53-59` — a `DefaultLifecycleObserver` is registered inside a `LaunchedEffect` and never removed;
  also `requestNotificationPermission()` (`:82`) uses `ActivityCompat.requestPermissions` with no result handling while
  the rest of the app uses the Activity Result API. Lifecycle detail → Phase 3.

## ARCH-22 — Info — Background activity start depends on `SYSTEM_ALERT_WINDOW`

`AppLaunchService.kt:107-108` starts the target app with `FLAG_ACTIVITY_NEW_TASK` from a background service, which
Android 10+ blocks unless the app holds "draw over other apps" — hence the modal permission prompt at
`MainActivity.kt:56-66`. That is a deliberate, working design for this app's purpose, not a defect, but it is the
app's most fragile platform assumption and it is what the aggressive permission dialog exists for. Attack-surface
implications → Phase 4.
