# 06 — Testing (Audit Phase 6)

**Date:** 2026-09-04 · **Branch:** `claude-audit` @ `be0f508` · **Scope:** whole repo (single `:app` module)

Method: read all 3 test files in full, all 3 ViewModels, all 4 use cases, both mapper files,
`DateTimeExtensions.kt`, both services, the receiver, `AlarmManagerWrapper`, `ScheduleDao`,
`DatabaseModule`, `app/build.gradle.kts` and the CI workflow. No Gradle task was run in this phase;
build facts are taken from `audit/00-baseline.md`. `connectedDebugAndroidTest` has never been run in
this audit — every claim about instrumented behaviour is marked accordingly.

**No Critical findings.** Nothing in the test suite itself would justify a weekend phone call. But the
absence of tests has already let two live defects through (TEST-4, TEST-5), and those are High.

---

## Summary of the suite

| | |
|---|---|
| JVM unit tests | 1 (`assertEquals(4, 2 + 2)`) |
| Instrumented tests | 13 (1 package-name stub + 12 DAO tests) |
| Compose UI tests | 0 |
| Production lines covered by JVM tests | **0** of 3,085 |
| `Thread.sleep` in tests | 0 (good) |
| `@Ignore`d tests | 0 (good) |
| `runTest` usage | 12 (all in `ScheduleDaoTest`, all instrumented) |

The test *shape* is inverted: 100% of the meaningful tests require a device, and CI has no device.
The one thing standing between a regression and production is a human.

## What is actually good

Worth stating so it doesn't get "fixed":

- **The layering is already test-friendly.** `ScheduleRepository`, `AlarmManagerRepository` and
  `DeviceAppsRepository` are narrow interfaces in `domain/repository/` with `@Binds` impls
  (`di/RepositoryModule.kt`). Three hand-written fakes — maybe 60 lines total — unlock unit tests for
  all four use cases and two of three ViewModels. Nothing needs to be re-architected to start testing;
  the cost of the first test is low and the cost of the second is near zero.
- **`ScheduleDaoTest` gets the Room mechanics right**: `Room.inMemoryDatabaseBuilder`
  (`ScheduleDaoTest.kt:65-68`), `db.close()` in `@After` (`:71-74`), `runTest` rather than
  `runBlocking`, no `Thread.sleep`, no `@Ignore`, no order dependence between tests, and no assertion
  on absolute wall-clock values. It is a competent DAO test; its problems (TEST-6, TEST-7) are about
  *what* it asserts, not *how*.
- No mock framework means no deep mock chains and no tests-of-mocks. Starting from zero with fakes is
  a better position than starting from a MockK swamp.

---

## Findings

### TEST-1 — High — Zero JVM unit tests of production code

`app/src/test/java/com/peal/appscheduler/ExampleUnitTest.kt:12-15` — the entire JVM suite is the IDE
template asserting `2 + 2 == 4`. `testDebugUnitTest` reports "1 test, 0 failures"
(`audit/00-baseline.md`), which is a green signal that measures nothing.

Untested production surfaces, in descending order of risk:

| Surface | Files | Why it matters |
|---|---|---|
| Reboot rescheduling | `service/RescheduleService.kt`, `receiver/AppSchedulerReceiver.kt` | The app's core promise; see TEST-4 |
| Alarm scheduling / cancellation | `data/wapper/AlarmManagerWrapper.kt` | See TEST-5 |
| Use cases | `domain/usecase/` ×4 | `ScheduleAppUseCase.kt:25-50` and `CancelScheduledAppUseCase.kt:19-44` hold the branch logic (edit vs insert, already-handled, DB failure → `ScheduleError`). Pure logic over two injectable interfaces. Cheapest tests in the repo, and none exist. |
| MVI reducers | `ui/screens/*/*ViewModel.kt` ×3 | See TEST-2 |
| Date/time | `domain/utils/DateTimeExtensions.kt` | See TEST-8, TEST-9 |
| Mappers | `data/mappers/ScheduleMappers.kt`, `domain/mappers/Mappers.kt` | Pure except for the icon lookup; see TEST-3 |

**Impact:** no automated regression net of any kind. Every refactor is a manual-QA event.

---

### TEST-2 — High — ViewModels are untestable as written: no injected dispatcher, and `kotlinx-coroutines-test` is not on any test classpath

This is the finding, not the coverage number. All three ViewModels launch straight into
`viewModelScope` (i.e. `Dispatchers.Main.immediate`) and inject no `CoroutineDispatcher`:

- `ui/screens/home/HomeViewModel.kt:28-31` (ctor), `:44` (`viewModelScope.launch`)
- `ui/screens/deviceApps/DeviceAppsViewModel.kt:25-27` (ctor), `:40`
- `ui/screens/schedule/SchedulerViewModel.kt:40-44` (ctor), `:97`, `:148`, `:160`

The standard escape hatch — `Dispatchers.setMain(StandardTestDispatcher())` — needs
`kotlinx-coroutines-test`, which is not declared anywhere. `app/build.gradle.kts:77-86` lists exactly
five test dependencies: `junit`, `androidx.junit`, `espresso-core`, `ui-test-junit4`, `room-testing`,
`arch-core-testing`. No coroutines-test, no Turbine, no MockK, no Robolectric.

`SchedulerViewModel` compounds it with two hardcoded `delay(500)` calls (`:106`, `:161`) that exist
purely to make a spinner visible, and a bare `System.currentTimeMillis()` comparison at `:152` that
decides the past-date rule. With no `TestDispatcher` the delays cost real wall-clock seconds; with no
injected time source the past/future boundary cannot be pinned deterministically. `:143` also reads
`ZoneId.systemDefault()`, so any assertion on `scheduledTime` is machine-dependent.

**Impact:** "add ViewModel tests" is not a backlog task here — it is a dependency addition plus a
constructor refactor on three classes. Testability has been designed out. The MVI contract
(`*Contract.kt` State/Intent/Effect) is the ideal shape for cheap reducer tests and none of that value
is being collected.

**Secondary, testing-relevant:** effects are exposed as `MutableSharedFlow()` with default
replay = 0 (`HomeViewModel.kt:35`, `DeviceAppsViewModel.kt:32`, `SchedulerViewModel.kt:52`). `emit`
drops the value if no collector is attached. That makes every effect test a subscribe-before-emit
race, and is a real runtime hazard (a navigation effect emitted while the screen is not collecting is
silently lost). Owned by the architecture phase; noted here because it makes effect tests flaky by
construction.

---

### TEST-3 — High — `HomeViewModel` holds a `Context` and maps in the ViewModel, forcing any test onto a device

`ui/screens/home/HomeViewModel.kt:30` injects `@ApplicationContext private val context: Context`, and
`:49` calls `it.toScheduleAppInfoUi(context)` inside the state update. That mapper
(`domain/mappers/Mappers.kt:16-27`) calls `context.getAppIconDrawable(...)`
(`ui/utils/ContextExt.kt:30-38` → `PackageManager.getApplicationIcon`) and `formatScheduledTime()`.

Violates CLAUDE.md non-negotiable #6 directly, but the testing consequence is the concrete one:
`HomeViewModel` cannot be constructed in a JVM test at all, and the mapping of `AppSchedule` →
`ScheduleAppInfoUi` (icon resolution *and* time formatting, i.e. what the user actually reads on the
home screen) has no cheap test path. A `Drawable` in UI state also can't be asserted meaningfully.

**Impact:** the busiest screen's state derivation is locked behind instrumentation. Moving icon
resolution out of the ViewModel is a prerequisite for testing it, not a cleanup.

---

### TEST-4 — High — Reboot-rescheduling path has no test at any level, and contains a defect that a test would have caught

`service/RescheduleService.kt:81-108`: `reschedulePendingApps()` calls
`scheduleRepository.getScheduledAppsToReschedule(...)` — which is a Room `@Query` returning
`Flow<List<ScheduleEntity>>` (`data/local/ScheduleDao.kt:31-32`, mapped in
`ScheduleRepositoryImpl.kt:48-54`). A Room observable query **never completes**. Therefore:

1. `collect` at `:84` suspends forever, so the `finally { stopSelf() }` at `:105-107` is unreachable
   on the success path. The foreground service (with `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`) stays alive
   after boot until the system kills it.
2. `updateScheduleStatus(...)` at `:88` writes to the same table the flow observes, so Room's
   invalidation tracker re-emits and the `forEach` re-runs over the new snapshot.

`receiver/AppSchedulerReceiver.kt:19-27` (the `BOOT_COMPLETED` entry point) is likewise untested, and
so is `AppLaunchService.onStartCommand` (`service/AppLaunchService.kt:53-94`) with its four outcome
branches (null package → FAILED, not installed → FAILED, launch throws → FAILED, success → EXECUTED).

**Impact:** the app's headline promise — "your schedule survives a reboot" — is verified by nobody. A
single Robolectric/instrumented test driving `RescheduleService` against an in-memory DB and a fake
`AlarmManagerRepository` would have caught item 1 immediately. Item 2's re-processing loop is
**unverified** at runtime; confirming it needs an instrumented run of the service with logging on
`AlarmManagerRepository.scheduleApp`.

---

### TEST-5 — High — Untested `AlarmManagerWrapper` builds mismatched PendingIntents: cancel almost certainly cancels nothing

`data/wapper/AlarmManagerWrapper.kt`:

- `scheduleApp` (`:32-43`) builds the Intent **with** `action = "com.peal.ACTION_SCHEDULE_APP"`
  (`:33`) and request code `scheduleId.hashCode()` (`:39`).
- `cancelSchedule` (`:52-62`) builds the Intent **without any action** (`:52-55`) and request code
  `scheduleId.toInt()` (`:58`).

`PendingIntent` equivalence uses `Intent.filterEquals`, which compares action, data, type, identity,
component and categories — extras are ignored, the action is not. The cancel-side PendingIntent
therefore does not match the scheduled one, so `alarmManager.cancel(pendingIntent)` at `:62` targets a
different (freshly created) PendingIntent and the original alarm survives.
`CancelScheduledAppUseCase.kt:29-37` still sees `Result.success` and marks the row `CANCELLED`, so the
UI reports success while the alarm remains armed.

(The two request-code formulas coincide for `Long` ids below 2^31 — `Long.hashCode()` is
`(value xor (value ushr 32)).toInt()` — so the request code is not the active defect, but it is a
latent divergence that only a test pins down.)

**Impact:** cancelling a schedule likely does not cancel the launch. The user's app opens anyway. This
is the single most valuable thing in the repo to have a test for, and it is the single most obviously
untested class. **Unverified at runtime** — confirm with an instrumented test that schedules an alarm
~5 s out, cancels it, and asserts no launch; or `adb shell dumpsys alarm | grep com.peal.appscheduler`
before and after a cancel. Fix ownership belongs to the architecture/logic phase; it is recorded here
as the payoff evidence for TEST-1.

---

### TEST-6 — High — No Room schema export and no migration test; a v2 schema change will crash existing installs

- `data/local/SchedulerAppDatabase.kt:12` — `@Database(entities = [ScheduleEntity::class], version = 1)`,
  `exportSchema` not set (defaults to `true`).
- `app/build.gradle.kts:11-44` — no `room.schemaLocation` / `ksp` arg, hence the build warning recorded
  in `audit/00-baseline.md:25`: *"Schema export directory was not provided … Room cannot export the schema"*.
- No `app/schemas/` directory exists.
- `di/DatabaseModule.kt:20-24` — plain `Room.databaseBuilder(...).build()`: no `addMigrations`, and no
  `fallbackToDestructiveMigration`.

**Impact:** there is no committed baseline schema, so `MigrationTestHelper` cannot be used and no
migration can ever be tested. When `version` goes to 2 without a migration, Room throws
`IllegalStateException: A migration from 1 to 2 was required but not found` on first launch after
update — a hard crash for every existing user, in a database that holds the only copy of their
schedules. Checklist item T9; cheap to fix now, expensive to fix later.

---

### TEST-7 — Medium — `ScheduleDaoTest` asserts against its own private copy of `ScheduleStatus`

`app/src/androidTest/java/com/peal/appscheduler/data/local/ScheduleDaoTest.kt:26-31` declares a
top-level `enum class ScheduleStatus { SCHEDULED, EXECUTED, CANCELLED, FAILED }` in the test source
set. The production enum is `domain/enums/ScheduleStatus.kt:8-13` and is never imported by the test.

Status is persisted as a `String` (`ScheduleEntity.kt:17`, `ScheduleDao.kt:28-29`), so the two tests
that exist specifically to verify status persistence — `updateStatus_andVerifyChange` (`:126-133`) and
`insertSchedulesWithAllStatuses_andVerifyRetrieval` (`:206-231`) — are comparing the test's own
constant names to themselves. Rename `EXECUTED` in production and both tests stay green while the app
writes a value the rest of the code no longer recognises.

**Impact:** checklist item T7. These are the only status assertions in the repo and they are
structurally incapable of failing for the reason they were written.

---

### TEST-8 — Medium — The two DAO queries that carry business rules are the two the DAO test doesn't cover

`ScheduleDao.kt` declares six methods. `ScheduleDaoTest` exercises `insert`, `getAllScheduledApps`,
`update` and `updateStatus`. Not covered:

- `isScheduleConflicting(scheduledTime, status)` — `ScheduleDao.kt:28-29`. Drives the only business
  rule in the write path (`ScheduleRepositoryImpl.kt:22-31` → `ScheduleError.TIME_CONFLICT`). Note it
  matches on **exact millisecond equality** of `scheduledTime`; whether that is the intended semantic
  (vs. a window) is unstated and untested, and `strings.xml:23` carries a conflict message that lint
  reports as unused (`audit/00-baseline.md:63-64`) — a hint the rule may never actually fire in the UI.
- `getScheduledAppsToReschedule(status)` — `ScheduleDao.kt:31-32`. The reboot path (TEST-4).

Two further gaps in what *is* covered: `insertMultipleSchedules_andVerifyOrder` (`:92-107`) sorts the
result itself (`result.sortedBy { it.id }`, `:102`) before asserting order, so it asserts nothing about
the query's ordering — and indeed `SELECT * FROM schedules` has no `ORDER BY`, meaning home-screen
ordering is unspecified. And there is no delete path in the DAO at all, so cancelled rows accumulate
forever (untested, and arguably by design).

**Impact:** the covered methods are Room-generated boilerplate that rarely breaks; the uncovered ones
hold the logic. Coverage is being spent where it earns least.

---

### TEST-9 — Medium — CI compiles nothing but `assembleDebug`; tests are never built or run

`.github/workflows/main.yml:5` — trigger is `push` to `main` only, no `pull_request`.
`.github/workflows/main.yml:26` — the only Gradle invocation is `./gradlew assembleDebug`.

No `testDebugUnitTest`, no `lintDebug`, no `assembleAndroidTest`, no emulator job.

**Impact:** three compounding effects. (1) A branch can never be verified before merge. (2) Even if
unit tests were added, nothing would gate on them. (3) Because `assembleAndroidTest` never runs,
`src/androidTest` can stop compiling and nobody finds out — which is exactly the risk in TEST-10.

---

### TEST-10 — Medium (unverified) — `ScheduleDaoTest` may not compile: it imports `kotlinx-coroutines-test`, which is not declared

`ScheduleDaoTest.kt:11` — `import kotlinx.coroutines.test.runTest`, used by all 12 tests.
`app/build.gradle.kts:77-86` — `kotlinx-coroutines-test` is not declared for `testImplementation` or
`androidTestImplementation`; per `audit/00-inventory.md:85-86` it is absent from
`gradle/libs.versions.toml` entirely. It would only be on the classpath if `androidx.room:room-testing`
2.6.1 or `androidx.arch.core:core-testing` 2.1.0 leak it transitively.

Nothing in this repo has compiled `src/androidTest` on this branch: the baseline run was
`assembleDebug lintDebug testDebugUnitTest assembleRelease`, none of which compiles androidTest, and
the stale classes under `app/build/intermediates/built_in_kotlinc/debugAndroidTest/` (dated Aug 19)
carry method names (`countConflictingMatchesInsideTheWindowOnly`, `deleteByIdRemovesTheRow`,
`getByStatusFiltersAndSortsAscending`) that exist in neither the current test file nor the current DAO
— i.e. they are from a different source state, not from this commit.

**Impact:** the repo's only substantive test may be dead code. Because CI never builds it (TEST-9),
this could have been true for months.

**Measurement that would confirm:** `./gradlew :app:compileDebugAndroidTestKotlin`, or
`./gradlew :app:dependencies --configuration debugAndroidTestCompileClasspath | grep coroutines-test`.
Both were skipped in this phase (no Gradle runs) and offline (no network to read the room-testing POM).

---

### TEST-11 — Medium — `toUtcEpochMillis()` is a provable no-op sitting on the DB write path, and one 3-line test would say so

`domain/utils/DateTimeExtensions.kt:78-83`:

```kotlin
fun Long.toUtcEpochMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
```

`Instant → atZone → toInstant` is the identity on the instant; the function returns its input for
every value. It is the only transformation applied when writing a schedule to the database
(`data/mappers/ScheduleMappers.kt:16`), and the field it produces is later read back as the source of
truth for both the alarm time and the displayed time (`SchedulerViewModel.kt:65`, `:72-73`).

The name promises a timezone normalisation that does not happen. Nothing currently *breaks* — the
value was already UTC epoch millis, produced at `SchedulerViewModel.kt:143` — so this is a correctness
trap rather than a live bug: the next person who trusts the name and passes it a non-epoch value, or
who "fixes" it to actually shift by the zone offset, moves every stored schedule.

The same file has three more untested, timezone/locale-sensitive functions with silent fallbacks:
`formatScheduledTime` (`:31-44`) and `toFormattedPattern` (`:46-59`) both swallow every exception and
return `this.toString()`, so a bad pattern renders a raw epoch number in the UI rather than failing.
All are pure, dependency-free and cost minutes to test.

**Impact:** exactly the class of code that breaks silently and is discovered by a user in another
timezone. Zero tests on 82 lines of date math.

---

### TEST-12 — Medium — The parse-failure branches of `toLocalTime`/`toLocalDate` cannot be unit-tested on the JVM at all

`domain/utils/DateTimeExtensions.kt:63` and `:73` call `android.util.Log.e` from their `catch` blocks.
`app/build.gradle.kts` has no `testOptions { unitTests.isReturnDefaultValues = true }` (grepped:
absent), and Robolectric is not a dependency, so under a plain JVM test those lines throw
`RuntimeException: Method e in android.util.Log not mocked`.

That matters because the `null` return from those branches is load-bearing:
`SchedulerViewModel.insertSchedule` (`:135-138`) falls back to re-parsing the formatted strings held in
state, and a `null` there routes to `Effect.MissingDateTime` (`:191-196`). The happy path is testable;
the failure path — the one with the branching — is not.

**Impact:** a pure-Kotlin utility file has an `android.*` dependency for logging only, and it costs the
testability of its error handling. Also a CLAUDE.md #6-adjacent smell (`android.util.Log` in domain).

---

### TEST-13 — Medium — Zero Compose UI tests, and no `testTag` anywhere to write them against

`androidTestImplementation(libs.androidx.ui.test.junit4)` (`app/build.gradle.kts:81`) and
`debugImplementation(libs.androidx.ui.test.manifest)` (`:89`) are declared and entirely unused —
`createComposeRule` / `createAndroidComposeRule` appear 0 times in the repo. 1,749 lines of Compose
across 29 files (`audit/00-inventory.md`) have no UI test.

`grep -rn "testTag" app/src/main` returns nothing, so the first UI test written will have to match on
displayed text or index — brittle by construction, and coupled to the strings that lint already flags
as partly unused.

**Impact:** medium, not high — UI tests are the expensive tier and are the right thing to skip when the
cheap tier is also empty. Recorded mainly because the dependencies are already paid for, and because
the Nav3 wiring (`AppSchedulerNavHost.kt`) plus the effect-collection races noted in TEST-2 are
precisely what a couple of Compose tests would pin. Screenshot tests (checklist T8) are not
recommended here: there is no component library, just 5 shared components.

---

## Checklist coverage

| Item | Result |
|---|---|
| T1 important things tested | **No.** Schedule → alarm → launch → reboot: 0 tests. See TEST-1, TEST-4, TEST-5. |
| T2 test shape | **Inverted.** 100% of meaningful tests need a device; CI has none. TEST-9. |
| T3 coroutine testing | `runTest` used correctly where present; no `Thread.sleep`, no `runBlocking`. But no `TestDispatcher` is possible at all. TEST-2. |
| T4 flakiness | **Clean.** 0 `@Ignore`, 0 sleeps, 0 retry wrappers, no order dependence, no network. |
| T5 test doubles | No fakes, no mocks — nothing to criticise, nothing to build on. Interfaces are ready. |
| T6 Compose UI tests | None. TEST-13. |
| T7 assertion quality | TEST-7 (shadowed enum), TEST-8 (self-sorted order assertion). |
| T8 screenshot tests | Absent; not recommended at this size. |
| T9 migration tests | **Impossible today** — no exported schema. TEST-6. |
| T10 suite reliability | `testDebugUnitTest` passes deterministically (it asserts arithmetic). Instrumented suite unrun; TEST-10 questions whether it compiles. |

## Not determined in this phase

- Whether `src/androidTest` compiles on this commit (TEST-10) — no Gradle run, no network.
- Whether `ScheduleDaoTest`'s 12 tests pass — no device attached.
- Runtime confirmation of the `RescheduleService` re-emission loop (TEST-4) and the
  `PendingIntent` cancel mismatch (TEST-5). Both are static reads; both need a device to prove.
