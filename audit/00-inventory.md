# 00 — Inventory (Audit Phase 0)

**Date:** 2026-09-04
**Repo:** App-Scheduler — `com.peal.appscheduler`
**Branch:** `claude-audit` (HEAD `be0f508`)
**Scope:** whole repo (single module). **Goal:** broad health check, autonomous full audit.

---

## 1. What the app does

Android app that lets the user pick an installed app and schedule it to be launched at a
future date/time. Schedules persist in Room, fire via `AlarmManager`, and are re-armed after
device reboot via a `BOOT_COMPLETED` receiver. Launching happens from a foreground service.

## 2. Module graph

Single Gradle module. `settings.gradle.kts` includes only `:app`. No library modules, no
convention plugins, no `build-logic`.

```
:app   (com.android.application)
```

## 3. Size

| Source set | .kt files | Lines |
|---|---|---|
| `app/src/main` | 64 | 3,085 |
| `app/src/test` | 1 | ~14 |
| `app/src/androidTest` | 2 | ~258 |
| **Total** | **67** | **~3,357** |

Package breakdown of `app/src/main`:

| Package | Files | Lines | Purpose |
|---|---|---|---|
| `ui/` | 29 | 1,749 | Compose screens (home, deviceApps, schedule), shared components, Nav3 wiring, theme, UI utils |
| `domain/` | 14 | 378 | Models, repository interfaces, 4 use cases, date/SDK utils, enums |
| `service/` | 2 | 296 | `AppLaunchService` (foreground, launches target app), `RescheduleService` |
| `data/` | 7 | 277 | Room DAO/entity/DB, mappers, repository impls, `AlarmManagerWrapper` |
| `di/` | 3 | 94 | Hilt modules |
| `core/` | 5 | 93 | `Result`/`Error`/`Success` primitives, `ObserveAsEvents` |
| `receiver/` | 1 | 43 | `AppSchedulerReceiver` (BOOT_COMPLETED) |
| `utils/` | 1 | 17 | `AppConstant` |
| root | 2 | — | `AppSchedulerApp`, `MainActivity` |

Resources are minimal: 3 values XML files (46 lines total), 4 drawables, launcher mipmaps,
backup/data-extraction rules. No `values-night`, no other locales.

**Under 15k lines → read broadly. No sampling needed; every source file is in scope.**

## 4. SDK / toolchain

| | |
|---|---|
| applicationId / namespace | `com.peal.appscheduler` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| Gradle | 8.14.3 |
| AGP | 8.13.1 |
| Kotlin | 2.0.0 |
| JVM target | 17, core library desugaring enabled (`desugar_jdk_libs` 2.0.3) |
| Extra compiler args | `-Xjvm-default=all` |
| Annotation processing | **kapt** (Hilt) + **KSP** 2.0.21-1.0.25 (Room) — mixed |

## 5. Dependency versions (`gradle/libs.versions.toml`)

| Library | Version |
|---|---|
| Compose BOM | 2024.04.01 |
| compose material3 | 1.4.0 (pinned, overrides BOM) |
| material-icons-extended | 1.7.8 (pinned, overrides BOM) |
| androidx.core:core-ktx | 1.15.0 |
| lifecycle-runtime-ktx | 2.8.7 |
| activity-compose | 1.10.0 |
| navigation-compose (Nav2) | 2.8.7 — **declared but unused** |
| navigation3-runtime / -ui | 1.0.0 |
| lifecycle-viewmodel-navigation3 | 2.10.0 |
| Hilt | 2.52 (+ hilt-navigation-compose 1.2.0) |
| Room | 2.6.1 |
| kotlinx-serialization-json | 1.8.0 |
| JUnit4 / androidx.test.ext:junit / Espresso | 4.13.2 / 1.2.1 / 3.6.1 |
| arch core-testing | 2.1.0 |

Notably **absent**: `kotlinx-coroutines-core` and `kotlinx-coroutines-test` are not declared
anywhere — coroutines arrive transitively. No Turbine, MockK, Robolectric, no image loader,
no networking library, no logging library, no `kotlinx-collections-immutable`.

**Note:** the `serialization` Gradle *plugin* is aliased to the `kotlinxSerialization`
version ref (`1.8.0`), i.e. the plugin version is being taken from the *library* version.
That plugin should track the Kotlin version (2.0.0). Flagged for the build phase.

## 6. Architecture at a glance

- **DI:** Hilt, rooted at `AppSchedulerApp` (`@HiltAndroidApp`). All bindings in
  `SingletonComponent`: `AppModule` (AlarmManagerWrapper, PackageManager),
  `DatabaseModule` (Room db + DAO, **unscoped** — no `@Singleton` on `provideDatabase`),
  `RepositoryModule` (`@Binds` × 3, `@Singleton`).
- **Navigation:** Navigation 3. `AppSchedulerNavHost.kt` builds an `entryProvider` and renders
  `NavDisplay`; backstack is a `NavBackStack<NavKey>` wrapped in `NavigationState`, mutated by
  a plain `Navigator` class. Routes are `@Serializable` `NavKey` types in `Screens.kt` —
  type-safe. Five unused Nav2 imports remain in `AppSchedulerNavHost.kt`.
- **State:** per-screen `*Contract.kt` holding State / Event / Effect (MVI-ish). ViewModels
  expose `StateFlow`. There is also a `SharedDeviceAppViewModel` for cross-screen state.
- **Layering:** ui → domain (use cases) → data (repository impls) → Room / AlarmManager /
  PackageManager. Interfaces live in `domain/repository`, impls in `data/repositoryImpl`.

## 7. Test inventory

| Test | Location | Substance |
|---|---|---|
| `ExampleUnitTest` | `src/test` | `assertEquals(4, 2 + 2)` — template stub |
| `ExampleInstrumentedTest` | `src/androidTest` | asserts package name — template stub |
| `ScheduleDaoTest` | `src/androidTest` | 233 lines — the only real test; covers the DAO |

**Effective coverage: 0 JVM unit tests of production code.** No ViewModel tests, no use-case
tests, no repository tests, no Compose UI tests. The only meaningful test requires a device.

## 8. Release / distribution posture

- `isMinifyEnabled = false` for release → **R8 does not run**; `proguard-rules.pro` is stock
  comment text.
- No `signingConfig` — release output is unsigned.
- No baseline profile, no `shrinkResources`, no ABI splits, no bundle config.
- No build flavors, no `debug` build type customisation (no `applicationIdSuffix`).

## 9. CI

`.github/workflows/main.yml`, triggered on push to `main` only:
1. checkout, JDK 17 (temurin)
2. `./gradlew assembleDebug`
3. upload debug APK artifact
4. POST an adaptive card to a Teams webhook (`secrets.TEAMS_WEBHOOK_URL`)

**CI runs no lint and no tests.** No PR trigger — pushes to branches other than `main` are
never built.

## 10. Manifest surface (for the security phase)

Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `RECEIVE_BOOT_COMPLETED`.

Components:
- `MainActivity` — exported (LAUNCHER)
- `AppSchedulerReceiver` — **exported = true**, filters `BOOT_COMPLETED`
- `AppLaunchService` — not exported, `foregroundServiceType="systemExempted"`
- `RescheduleService` — not exported
- `<queries>` for all LAUNCHER activities (package visibility)
- `allowBackup = true` with custom backup/data-extraction rules

## 11. What I could NOT determine in Phase 0

- **Runtime behaviour.** No device or emulator was attached; `connectedDebugAndroidTest` was
  not run, so `ScheduleDaoTest` is unverified in this audit.
- **Actual recomposition counts / jank.** Compose compiler metrics were not enabled in this
  build; any recomposition finding is static reasoning, not measurement.
- **APK size and method count.** R8 is off, so release size is not representative of a
  shippable build.
- **Whether `SYSTEM_ALERT_WINDOW` is actually needed** — requires reading the launch path.
  Deferred to the security phase.
- **Room schema export / migration story** — no `schemas/` directory found; whether
  `exportSchema` is set is deferred to the build phase.
- **Play Store listing / data-safety declarations** — out of repo.
