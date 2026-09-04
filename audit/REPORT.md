# Android codebase audit — App Scheduler (`com.peal.appscheduler`) — 2026-09-04

Branch `claude-audit` @ `be0f508`. Single `:app` module, 3,085 lines of production Kotlin.

---

## Scope & method

**Read fully, no sampling.** At ~3.1k lines the whole repo fits under the skill's 15k threshold, so
every file in `app/src/main/java/com/peal/appscheduler/` was read, plus the manifest, resources,
Gradle files, and CI workflow.

**Tools actually run:**

| Tool | Ran? | Result |
|---|---|---|
| `assembleDebug` | ✅ | pass — 18.4 MB APK |
| `lintDebug` | ✅ | pass — 0 errors, 39 warnings |
| `testDebugUnitTest` | ✅ | pass — 1 test |
| `assembleRelease` | ✅ | pass — 12.1 MB unsigned APK, **R8 did not run** (`isMinifyEnabled = false`) |
| `:app:compileDebugAndroidTestKotlin` | ✅ | pass — run specifically to disprove one finding |
| `connectedDebugAndroidTest` | ❌ | **no device attached** |
| Compose compiler metrics | ❌ | not enabled |
| Profiler / macrobenchmark / LeakCanary | ❌ | no device |

Full baseline in `audit/00-baseline.md`; inventory in `audit/00-inventory.md`; per-phase detail in
`audit/01-architecture.md` … `audit/07-ux-a11y.md`.

**Process.** Seven phases ran in isolated subagent contexts. Every Critical and High was then
re-opened and checked against the source by the lead session. That pass **dropped one finding**
(`TEST-10`, "androidTest may not compile" — it compiles, exit 0) and **downgraded three** (see
*Corrections* at the end). All seven phases completed; none were skipped.

**What this audit cannot tell you:** nothing was measured on a device. Every performance and
runtime-crash claim below is derived from reading code and platform contracts, not from observation.
Findings that depend on a device are marked **unverified** with the measurement that would settle them.

---

## Verdict

This is a well-structured small app with a broken core. The layering, the MVI `*Contract` convention,
the type-safe Nav3 keys and the Hilt graph are genuinely clean — better than most 3k-line hobby
Android projects — and the build is green end to end. But **the three things the app exists to do are
each broken by a separate defect**: cancelling a schedule doesn't cancel the alarm, rescheduling after
reboot crashes on launch on Android 14+, and a schedule that fails to arm still reports success.
None of them would be caught by the current test suite, because there effectively isn't one — 3,085
production lines are covered by a single `assertEquals(4, 2 + 2)`.

The top three risks, in order: **(1)** the alarm cancel/arm correctness cluster (C1, C3, H1, H7) —
users are told their schedules changed when they didn't; **(2)** the reboot path is dead on modern
Android (C2) and untested at every level; **(3)** the app asks for `SYSTEM_ALERT_WINDOW` and
`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, neither of which it qualifies for, and hard-blocks the UI behind
an undismissable dialog until the first is granted (C4, H5, H6) — that combination will not survive
Play review.

**What should happen first:** fix C1 and C2 — both are a handful of lines — then write the first JVM
tests around `AlarmManagerWrapper` and `ScheduleAppUseCase` so the fixes stay fixed. Do not start the
dependency upgrade or the R8 work until then.

---

## What's working

Real strengths, worth not churning:

- **`core/domain/util/Result.kt`** — a clean typed `Result<D, E>` sealed interface with correct
  `map`/`onSuccess`/`onError`. The problem is that callers ignore it, not the type.
- **The `*Contract.kt` convention** (State / Intent / Effect per screen) is applied consistently
  across all three screens. Keep it.
- **`Screens.kt`** — `@Serializable` `NavKey` data objects/class give genuinely type-safe routes.
- **The Hilt graph** — `@Binds` in `RepositoryModule`, interfaces in `domain/repository`, impls in
  `data/repositoryImpl`. This layering is why the first tests will be cheap: ~60 lines of fakes
  unlock all four use cases and two ViewModels.
- **`ScheduleDao`** and `data/mappers/ScheduleMappers.kt` are correct and readable.
- **`ScheduleDaoTest`** gets the Room mechanics right — `inMemoryDatabaseBuilder`, `close()` in
  `@After`, `runTest`. Its weakness is what it asserts, not how it's built.
- **Compose hygiene basics**: zero `collectAsState()` (all lifecycle-aware), zero `GlobalScope`,
  zero `runBlocking` in `src/main`, all `mutableStateOf` remembered, no nested scrollables.
- **Security negatives worth stating**: no networking dependency and no `INTERNET` permission at all,
  no WebView, no secrets, both `PendingIntent`s are `FLAG_IMMUTABLE` with explicit components, and
  the `<queries>` MAIN/LAUNCHER declaration is the policy-correct narrow alternative to
  `QUERY_ALL_PACKAGES`. Nothing to fix there.
- **RTL, `sp` text sizing, `enableEdgeToEdge()`, and dynamic color with a real dark scheme** are all
  correctly wired.

---

## Findings

### Critical

| ID | Finding | Location | Impact |
|---|---|---|---|
| C1 | `cancelSchedule` builds its `PendingIntent` without the `action` used to arm it, so `AlarmManager.cancel()` matches nothing | `data/wapper/AlarmManagerWrapper.kt:52` (vs `:32`) | Cancelling a schedule is a no-op. The DB row says `CANCELLED`, the UI says success, and the app still launches at the scheduled time. `filterEquals` compares action, not extras — action is `null` on cancel vs `"com.peal.ACTION_SCHEDULE_APP"` on arm |
| C2 | `RescheduleService` calls `startForeground()` but declares no `foregroundServiceType` | `AndroidManifest.xml:48` + `service/RescheduleService.kt:61` | With `targetSdk = 36`, Android 14+ throws `MissingForegroundServiceTypeException`. The service is started from `BOOT_COMPLETED`, so **reboot re-arming — the app's headline feature — crashes on every boot**. Lint did not catch this |
| C3 | `RescheduleService` collects a never-completing Room `Flow`, so `finally { stopSelf() }` is unreachable | `service/RescheduleService.kt:84`, `:106` | `getScheduledAppsToReschedule()` is a Room observable `Flow` — `collect` never returns. Foreground service and its notification run forever after boot, and each status write re-emits the flow against a stale `currentTime` captured at `:79` |
| C4 | Overlay-permission dialog cannot be dismissed and re-arms on every `ON_RESUME` | `MainActivity.kt:53-66` + `ui/shared/components/CommonAlertDialog.kt:21-23` | `CommonAlertDialog` defaults to `showDismissButton = false`, `dismissOnBackPress = false`, `dismissOnClickOutside = false`, and `MainActivity` overrides none of them. The only button sends the user to Settings; declining there triggers `onResume`, which re-shows the dialog. A user who won't grant `SYSTEM_ALERT_WINDOW` cannot use the app at all — and per H6 the app never draws an overlay anyway |

### High

| ID | Finding | Location | Impact |
|---|---|---|---|
| H1 | `ScheduleAppUseCase` discards both alarm-scheduling results and swallows `CancellationException` | `domain/usecase/ScheduleAppUseCase.kt:25,29,39,48` | `updateSchedule(...)` and `scheduleApp(...)` both return `kotlin.Result`; both return values are dropped, then `Result.Success` is returned unconditionally. A schedule that failed to arm (revoked `SCHEDULE_EXACT_ALARM`, see H11) is reported to the user as scheduled. `runCatching{}.getOrElse{}` also swallows cancellation — violates CLAUDE.md rule 8 |
| H2 | No `rememberViewModelStoreNavEntryDecorator` — every ViewModel is Activity-scoped, never cleared | `ui/shared/navigation/NavigationState.kt:33` | Only `rememberSaveableStateHolderNavEntryDecorator()` is passed. `lifecycle-viewmodel-navigation3` is declared in the build file but never used. `SchedulerViewModel` survives back-navigation, so a date/time picked for app A is still set when the user opens the scheduler for app B |
| H3 | `AppLaunchService.stopSelf()` is unreachable on the malformed-intent path | `service/AppLaunchService.kt:161` | `stopSelf()` sits inside `if (scheduleId != null && scheduleId != -1L)`. An intent with a missing/`-1` schedule id leaves a foreground service and its ongoing notification running indefinitely |
| H4 | Exported receiver honours the app-private action `com.peal.ACTION_SCHEDULE_APP` with no sender validation | `receiver/AppSchedulerReceiver.kt:28` + `AndroidManifest.xml:36` | Any zero-permission app can send an explicit broadcast and make App Scheduler launch an arbitrary installed package to the foreground (a background-activity-launch proxy) and write `EXECUTED`/`FAILED` onto any schedule row. The `BOOT_COMPLETED` branch is *not* spoofable (protected broadcast); `exported="true"` is required for that branch, so the fix is action-scoped, not a flag flip |
| H5 | `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` / `systemExempted` used with no qualifying exemption | `AndroidManifest.xml:9,44-46` | Play only approves this type for device-owner/VPN/safety-role apps; none apply. Expect Play rejection, plus `ForegroundServiceStartNotAllowedException` risk on 14+. `shortService` or `specialUse` is the honest type |
| H6 | `SYSTEM_ALERT_WINDOW` demanded on every resume, but no overlay is ever drawn | `AndroidManifest.xml:7`, `MainActivity.kt:53-66`, `service/AppLaunchService.kt:107` | The permission is acquired purely as a background-activity-launch loophole for `startActivity` from a service. Restricted Play permission, nagged for on every resume (see C4), with a user-facing string that does not describe the real use |
| H7 | `setExact` used instead of `setExactAndAllowWhileIdle` | `data/wapper/AlarmManagerWrapper.kt:43` | `setExact` alarms are deferred out of Doze to the next maintenance window. For an app whose only job is firing at an exact time, schedules silently fire late on an idle device |
| H8 | `PackageManager.getApplicationIcon()` called on the main thread, per schedule, on every DB emission | `ui/screens/home/HomeViewModel.kt:44-49` → `domain/mappers/Mappers.kt:17` → `ui/utils/ContextExt.kt:33` | `viewModelScope` defaults to `Dispatchers.Main.immediate` and no dispatcher is specified, so N binder + resource loads run on the UI thread every time the schedules table changes. Magnitude **unverified** — StrictMode or systrace would size it |
| H9 | `Drawable` → `Bitmap` rasterised on the main thread for every list row | `ui/shared/components/AppIcon.kt:30` → `ui/utils/ImageUtils.kt:19-26` | Most launcher icons are `AdaptiveIconDrawable`, so the `BitmapDrawable` fast path is skipped and a full `Canvas` draw runs during composition. ~150 KB per icon at 192², with no cross-item cache. Correctly wrapped in `remember(icon)`, so this is once per row, not per recomposition. Scroll-jank magnitude **unverified** — `dumpsys gfxinfo framestats` |
| H10 | Device-apps screen: infinite spinner on error, blank screen when empty | `ui/screens/deviceApps/DeviceAppsViewModel.kt:45` + `DeviceAppsListScreen.kt:57-64` | `.catch { e -> e.printStackTrace() }` swallows the failure and never clears `isLoading`; the `when` block has no `else`. This is the only entry point to the app's core task, and both failure modes are dead ends |
| H11 | Exact-alarm permission prompt gated on API 33; the permission exists from API 31 | `ui/screens/schedule/SchedulerScreen.kt:119` | `AlarmManagerWrapper.kt:26` and `ContextExt.kt:18` both correctly use `>= S` (31). On API 31–32 with the permission revoked, the user is never prompted, `scheduleApp` throws `SecurityException` → `Result.failure` → discarded by H1 → "scheduled successfully" with no alarm |
| H12 | All installed apps' icons eagerly decoded and retained in ViewModel state | `data/repositoryImpl/DeviceAppsRepositoryImpl.kt:30-37`, `DeviceAppsContract.State` | `loadIcon()` for every launcher activity before the first emission. Correctly on `Dispatchers.IO`, so the UI thread is spared, but the user waits behind one long spinner and the heap holds N drawables for the screen's life. OOM risk **unverified** — heap dump / `dumpsys meminfo` |
| H13 | Zero JVM tests of production code, and the ViewModels are untestable by construction | `app/src/test/.../ExampleUnitTest.kt:12`; `SchedulerViewModel.kt:152,161` | The whole JVM suite is `assertEquals(4, 2 + 2)`. No dispatcher is injected anywhere and `kotlinx-coroutines-test` isn't a declared dependency, so `Dispatchers.setMain` isn't available; `System.currentTimeMillis()` is read directly in the past-date rule and `delay(500)` is hardcoded. Every Critical above would have been caught by a unit test |
| H14 | No Room schema export, no migrations, no destructive fallback | `data/local/SchedulerAppDatabase.kt:13`; `di/DatabaseModule.kt:20-24` | KSP warns twice per build. The first entity change ships `IllegalStateException: A migration from 1 to 2 was required but not found` to every existing user. No `app/schemas/`, so migration tests are impossible today |
| H15 | Release path has never been exercised end to end | `app/build.gradle.kts:27` (`isMinifyEnabled = false`), no `signingConfig`, no AAB | R8 has never run against Room, Hilt and `@Serializable` NavKeys, so keep rules are unvalidated; the release APK is unsigned; there is no bundle target. 27,306 classes / 143k method ids un-shrunk |
| H16 | `material-icons-extended` ships ~11,400 classes (~42% of the APK) for two icons already in `material-icons-core` | `app/build.gradle.kts:58` | The single largest size win available, and a one-line deletion |
| H17 | The Compose BOM is inert — the stack actually resolves via the separately pinned `material3` | `gradle/libs.versions.toml:10,21`; `app/build.gradle.kts:53,57` | BOM 2024.04.01 is overridden by `material3:1.4.0`, dragging ui/runtime to 1.9.5 alongside ripple 1.8.1 and icons 1.7.8. The pinning is fiction; any material3 bump silently moves the whole stack |
| H18 | The domain layer depends on the UI layer and on Android types | `domain/mappers/Mappers.kt:3,7,8,9`; `domain/model/DeviceAppInfo.kt:3,4` | `domain` imports `android.content.Context`, `android.graphics.drawable.Drawable`, `ui.model.ScheduleAppInfoUi`, `ui.shared.navigation.AppSchedulerScreen`. Inverts the dependency rule, and is a direct cause of H13 — domain code can't be tested on the JVM |
| H19 | Past dates and times are freely selectable; rejected only after Save, via a Toast | `ui/shared/components/DatePickerDialog.kt:26`, `TimePickerDialog.kt:24`; `SchedulerViewModel.kt:152` | `rememberDatePickerState()` is created with no `selectableDates` constraint. A scheduling app lets you pick yesterday and only complains afterwards |

### Medium / Low — clustered

Full detail in the phase files. Counts, not prose:

- **MED-A — Effects can be silently dropped (3 sites).** All three ViewModels use
  `MutableSharedFlow` with replay 0 and no buffer for one-shot effects, while importing
  `Channel`/`receiveAsFlow` and using neither. `SchedulerViewModel.kt:52`, `HomeViewModel.kt:35`,
  `DeviceAppsViewModel.kt:32`. Backgrounding the app during the hardcoded `delay(500)` loses the
  success/error message.
- **MED-B — Two screens collect effects lifecycle-unaware.** `HomeScreen.kt:36`,
  `DeviceAppsListScreen.kt:33` use raw `LaunchedEffect(Unit)`; the third screen uses the correct
  `ObserveAsEvents`. Back-stack mutation is possible while STOPPED.
- **MED-C — `ObserveAsEvents` wraps the wrong value.** `core/presentation/util/ObserveAsEvents.kt:23`
  applies `rememberUpdatedState` to the (already stable) lifecycle owner while capturing `onEvent`
  directly. Latent stale-lambda trap; benign today.
- **MED-D — Modifier contract violations (9 composables).** 8 missing the parameter, 1 declared
  without a default: `DeviceAppItem.kt:26`, `ScheduledAppItem.kt:33`, `SchedulerScreen.kt:236,249`,
  `CommonAlertDialog.kt:17`, `DatePickerDialog.kt:22`, `TimePickerDialog.kt:19`, `MainActivity.kt:92`,
  `DeviceAppsListScreen.kt:68`. Plus the same `modifier` instance applied to two siblings at
  `SchedulerScreen.kt:147` and `:230`, and never applied at all in `AppSchedulerNavHost.kt:58`.
- **MED-E — Accessibility (5 sites).** Clickable rows carry no `role`/`onClickLabel`/
  `mergeDescendants` (`ScheduledAppItem.kt:41`, `DeviceAppItem.kt:34`, `SchedulerScreen.kt:164,183`);
  date/time picker rows are ~24dp tall against a 48dp minimum (`SchedulerScreen.kt:161,180`);
  `Color.Gray` on light surface measures ~3.5:1 against a 4.5:1 requirement
  (`DeviceAppItem.kt:48`). Note `contentDescription` coverage is 5/5 — the defect is
  over-description, not omission.
- **MED-H — Date picker OK button is inert on first open.** `DatePickerDialog.kt:31` —
  `rememberDatePickerState()` has no `initialSelectedDateMillis`, so `selectedDateMillis` is null
  until a date is tapped and the confirm button's `?.let` does nothing. Because dismissal happens
  inside the caller's `onDateSelected`, the dialog doesn't close either. In edit mode the existing
  date is not pre-selected.
- **MED-F — State and lifecycle hygiene.** `SavedStateHandle` injected but never read
  (`SchedulerViewModel.kt:43`); picker state duplicated between `StateFlow` and private vars
  (`:49-50`); lifecycle observer added in `LaunchedEffect` with no removal (`MainActivity.kt:53`);
  `provideDatabase`/`provideScheduleDao` unscoped (`di/DatabaseModule.kt:18,27` — latent, one
  consumer today).
- **MED-G — Build/CI.** No `lint { }` block at all; CI runs only `assembleDebug` on push to `main`
  with no PR trigger, no lint, no tests (`.github/workflows/main.yml:5,26`); the Teams webhook step
  interpolates `${{ github.ref_name }}` into an unquoted heredoc — safe only while the trigger stays
  main-only (`:34-87`); kapt forces annotation processing to language version 1.9 though Hilt 2.52
  supports KSP; KSP 2.0.21 is pinned against Kotlin 2.0.0 and prints "too new" ×10 per configuration.
- **LOW —** dead `SharedDeviceAppViewModel` (missing `@HiltViewModel`, would crash if used) and 5
  stale Nav2 imports pulling in a 473-class unused dependency (`AppSchedulerNavHost.kt:7-11`);
  `toUtcEpochMillis()` is a provable identity function (`DateTimeExtensions.kt:77`); duplicated
  `LaunchedEffect` (`SchedulerScreen.kt:83-89`); `items()` without `key`
  (`DeviceAppsListScreen.kt:78`); hardcoded `"OK"`/`"Cancel"` in `TimePickerDialog.kt:35,40` while
  the sibling `DatePickerDialog` uses `stringResource` correctly; artificial `delay(500)` on both
  save and cancel; 8 unused template resources; no debug `applicationIdSuffix`.

---

## Fix plan

**Batch 1 — Make the core feature actually work (est. 1–2 days)**
- **C1** — give `cancelSchedule` the same action and request-code derivation as `scheduleApp`; extract
  one private `buildPendingIntent()` so they cannot drift again.
- **C2** — add `android:foregroundServiceType` to `RescheduleService` in the manifest (and pass the
  matching type to `startForeground`); pick `shortService` or `specialUse`, not `systemExempted`.
- **C3** — replace `.collect {}` with a one-shot read (`.first()`), then `stopSelf()`.
- **H1** — propagate the `kotlin.Result` from both alarm calls into the returned `Result`; stop
  swallowing `CancellationException`.
- **H7** — `setExactAndAllowWhileIdle`.
- **H3** — move `stopSelf()` out of the id guard.
- *Verification:* new JVM tests for `AlarmManagerWrapper` intent construction and `ScheduleAppUseCase`
  failure propagation; then an API 34+ emulator, `adb reboot`, and `adb shell dumpsys alarm | grep
  com.peal.appscheduler` before and after a cancel. **This device run is what actually closes C1, C2
  and C3** — the code fix alone does not.

**Batch 2 — Permissions and Play-review survival (est. 1 day)**
- **C4** — pass `showDismissButton = true` / `dismissOnBackPress = true`, and stop re-arming the
  dialog on every resume.
- **H6, H5** — decide whether background activity launch is really required. If it is, keep
  `SYSTEM_ALERT_WINDOW` but ask once with an honest rationale and a working decline path; if it is
  not, delete both it and `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` and use a notification instead.
- **H4** — validate the action and reject `com.peal.ACTION_SCHEDULE_APP` from external senders.
- **H11** — change the API-33 gate to API 31.
- *Verification:* install on API 31, 33 and 34+ devices; decline every permission and confirm the app
  stays usable.

**Batch 3 — The safety net (est. 2–3 days)**
- **H13** — add `kotlinx-coroutines-test` and Turbine; inject dispatchers and a `Clock`/time provider;
  write tests for the four use cases, `DateTimeExtensions`, and the three ViewModels. The layering
  already supports this — roughly 60 lines of fakes.
- **H14** — set `exportSchema` + `room.schemaLocation`, commit `app/schemas/`, add a migration test.
- **MED-G** — add a PR trigger and run `lintDebug testDebugUnitTest` in CI; quote the webhook
  interpolation at the same time.
- *Verification:* CI red on a deliberately broken commit.

**Batch 4 — UX and correctness polish (est. 2 days)**
- **H10, H19, H2**, then MED-A through MED-F.
- *Verification:* Compose UI tests for the empty/error/loading states; TalkBack and 200% font-scale
  sweep.

**Batch 5 — Build health (est. 2–3 days, do last)**
- **H16** (one line), **H15**, **H17**, then the Kotlin/AGP upgrade that clears the KSP and kapt
  issues together.
- *Verification:* `./gradlew :app:assembleRelease` with `isMinifyEnabled = true`, then launch the
  minified build and exercise scheduling — R8 breakage on Room/Hilt/serialization shows up at runtime,
  not at build time.

---

## Unverified hypotheses

Everything here is code-reading, not observation. To settle each:

| Claim | Measurement that would confirm |
|---|---|
| C2 crashes on boot on API 34+ | API 34+ emulator, `adb reboot`, `adb logcat \| grep MissingForegroundServiceType` |
| C1 leaves the alarm armed | `adb shell dumpsys alarm \| grep com.peal.appscheduler` before/after a cancel |
| H8 causes visible jank on Home | StrictMode `detectDiskReads`/`penaltyLog`, or a systrace of the Home DB emission |
| H9 causes scroll jank | `adb shell dumpsys gfxinfo com.peal.appscheduler framestats` while scrolling |
| H12 heap cost / OOM risk | Heap dump filtered on `Bitmap`, on a device with 150+ launcher apps |
| H5 fails Play review or throws at runtime | Play Console declaration attempt; API 34+ device for `ForegroundServiceStartNotAllowedException` |
| UX: scheduler form unreachable at 200% font | `adb shell settings put system font_scale 2.0` on a small screen — the *code* fact (no `verticalScroll` at `SchedulerScreen.kt:146`) is confirmed; the overflow is not |
| Compose stability / skippability (`@Immutable` on state holding a `Drawable`) | `./gradlew assembleRelease -Pkotlin.compose.reports=...` and read the metrics |
| Configuration-cache compatibility | `./gradlew --configuration-cache assembleDebug` |

**`ScheduleDaoTest` was never executed** — no device was attached, so `connectedDebugAndroidTest`
did not run. The repo's only substantive test is unverified in this audit.

---

## Corrections made during verification

Stated so the report's reliability can be judged:

1. **Dropped** `TEST-10` ("the only substantive test may not compile — it imports
   `kotlinx-coroutines-test`, which is undeclared"). I ran
   `./gradlew :app:compileDebugAndroidTestKotlin` — exit 0. The dependency arrives transitively.
2. **Downgraded** "Home has no empty state" from High to Medium — the screen is blank on first run,
   but the `+` FAB is present and is the correct affordance, so it is not a dead end.
3. **Downgraded** "scheduler form doesn't scroll" from High to Medium and marked it unverified — the
   missing `verticalScroll` is real, the 200%-font overflow is unmeasured, and the form is short.
4. **Corrected** the claim that the `PendingIntent` *request code* mismatch
   (`hashCode()` vs `toInt()`) contributes to C1. For Room `autoGenerate` ids these are equal; the
   **action** mismatch is the sole cause.
5. **Corrected** the claim that `.onError { Result.Failure(it) }` drops conflict errors. `onError`
   returns `this`, so the `Failure` does propagate — the block is dead code, not a data-loss bug.
   H1's real defects (discarded alarm results, swallowed cancellation) stand.
6. **Corrected** the framing of H8: the ViewModel holds an `@ApplicationContext`, so this is **not**
   a memory leak. The defects are main-thread I/O and untestability.
7. **Corrected** H9's magnitude: the conversion *is* wrapped in `remember(icon)`, so it runs once per
   row, not once per recomposition, and the bitmap is ~150 KB (192²×4), not ~350 KB.

---

## Appendices

- **A — Phase files:** `audit/01-architecture.md` (22 findings), `audit/02-compose.md` (18),
  `audit/03-performance.md` (19), `audit/04-security.md` (9), `audit/05-build.md` (19),
  `audit/06-testing.md` (13), `audit/07-ux-a11y.md` (22).
- **B — Machine-readable findings:** `audit/findings.json`.
- **C — Raw tool output:** build log `audit/.build-log.txt`; lint report
  `app/build/reports/lint-results-debug.{html,xml,txt}`; unit test results
  `app/build/test-results/testDebugUnitTest/`.
- **D — Baseline and inventory:** `audit/00-baseline.md`, `audit/00-inventory.md`.
