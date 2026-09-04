# 05 — Build, dependencies, release (Audit Phase 5)

**Date:** 2026-09-04 · **Branch:** `claude-audit` @ `be0f508` · **Module:** `:app` (single module)

Inputs reused, not re-derived: `audit/00-inventory.md`, `audit/00-baseline.md`.
No recompiling task was run. New evidence in this phase came from read-only Gradle tasks
(`:app:dependencies`, `:app:dependencyInsight`, `buildEnvironment`, `:app:help`), the lint XML already
on disk, and static inspection of the release APK already on disk with `dexdump` / `aapt2`.

**No Critical findings.** The build is not broken and nothing is on fire. What it is, is a build that has
never been exercised on the release path: R8 has never run, the artifact is unsigned, CI never sees it,
and roughly half the download is a library used for two icons.

---

## Findings

### BUILD-1 — High — R8 and resource shrinking disabled; keep rules never validated

`app/build.gradle.kts:25-33`, `app/proguard-rules.pro:1-23`

```kotlin
release {
    isMinifyEnabled = false
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

`isMinifyEnabled = false`, no `isShrinkResources`, and `proguard-rules.pro` contains nothing but the
template comments — so the `proguardFiles(...)` line is inert configuration.

Measured on `app/build/outputs/apk/release/app-release-unsigned.apk` with `dexdump`:

| dex | uncompressed | compressed | classes | method ids |
|---|---|---|---|---|
| classes.dex | 32,037,724 | 7,453,194 | 17,825 | 65,492 |
| classes2.dex | 2,440 | 987 | 14 | 15 |
| classes3.dex | 319,480 | 139,810 | 172 | 2,139 |
| classes4.dex | 10,925,748 | 3,794,481 | 7,823 | 65,377 |
| classes5.dex | 1,904,324 | 671,454 | 1,472 | 10,418 |
| **total** | **45.2 MB** | **11.5 MB** | **27,306** | **143,441** |

27,306 classes and 5 dex files for ~3,357 lines of app source.

**Impact.** Two distinct costs. (a) Size: the download is roughly double what it needs to be. (b) Risk:
the first time anyone enables minification, the three things in this app that R8 most commonly breaks are
all present and all untested — Room's generated DAO/database implementations, Hilt's generated components,
and the `@Serializable` `NavKey` route classes in `Screens.kt` that kotlinx.serialization resolves
reflectively. That entire class of release-only crash is 100% unretired. Enabling R8 is not a config
tweak here; it needs a minified build plus a manual smoke test of schedule → alarm → launch.

---

### BUILD-2 — High — `material-icons-extended` ships 11,400 unused classes for 2 icons that are in `material-icons-core`

`app/build.gradle.kts:58`, `gradle/libs.versions.toml:11,24`

Every icon reference in `app/src/main` (exhaustive):

```
Icons.Filled.Add          (HomeScreen)
Icons.Default.DateRange   (SchedulerScreen)
```

Both are in `material-icons-core`, which `material3` already pulls in transitively — confirmed by
unzipping `material-icons-core-1.7.8.aar`, which contains `androidx/compose/material/icons/filled/AddKt.class`
and `.../DateRangeKt.class`.

Cost, measured:
- `material-icons-extended-android-1.7.8.aar` = **35.7 MB**; its `classes.jar` alone is 37.4 MB compressed / 85.5 MB uncompressed across 11,123 entries.
- In the release APK, **11,400 of 27,306 classes (42%)** are under `androidx/compose/material/icons`, all of them in `classes.dex` (11,400 of that dex's 17,825 classes).
- `classes.dex` is 7.45 MB of the 12.1 MB APK.

**Impact.** The large majority of a 7.45 MB dex, in a 12.1 MB app, for zero used icons. This is the single
cheapest win in the repo: delete one dependency line and one version-catalog entry, no source change.
Exact byte saving is measurable with one rebuild.

Note this compounds with BUILD-1 — with R8 on, the unused icons would be shrunk away anyway. Removing the
dependency is still correct because it also stops paying the compile-time cost.

---

### BUILD-3 — High — Compose BOM is inert; the whole Compose stack silently resolves to 1.9.5

`gradle/libs.versions.toml:10,21,38`, `app/build.gradle.kts:53,57`

The catalog pins `composeBom = "2024.04.01"` (Compose 1.6.6) and separately pins `material3 = "1.4.0"`
outside the BOM. material3 1.4.0 depends on Compose 1.9.x, and Gradle's highest-wins conflict resolution
beats the BOM's constraint. Actual `releaseRuntimeClasspath` resolution:

```
androidx.compose.ui:ui:1.6.6            -> 1.9.5
androidx.compose.runtime:runtime:1.6.6  -> 1.9.5
androidx.compose.foundation:...:1.6.6   -> 1.9.5
androidx.compose.animation:...:1.6.6    -> 1.9.5
androidx.compose.material3:1.2.1        -> 1.4.0
androidx.compose.material:material-ripple            1.8.1   <- not 1.9.5
androidx.compose.material:material-icons-extended    1.7.8   <- not 1.9.5
```

**Impact.** The BOM declaration is decorative and actively misleading: the developer believes they are
pinned to Compose 1.6.6 and are in fact shipping 1.9.5, three minor versions ahead. Consequences:
(a) the artifact set is not internally version-consistent — ripple 1.8.1 and icons 1.7.8 sit alongside
ui/runtime 1.9.5; (b) any future material3 version change silently moves the entire Compose stack with no
diff in the catalog to review; (c) the Compose compiler plugin is Kotlin 2.0.0's, which is several
releases behind a 1.9.5 runtime. It compiles and the baseline build passed, so nothing is broken today —
but the pinning is fiction.

The coherent states are: bump the BOM to a version whose material3 *is* 1.4.0 and drop the separate
material3 pin, or drop the BOM entirely and pin every Compose artifact explicitly. The current mix is
neither.

---

### BUILD-4 — High — KSP 2.0.21-1.0.25 on Kotlin 2.0.0; KSP itself says this is unsupported

`build.gradle.kts:8` (`id("com.google.devtools.ksp") version "2.0.21-1.0.25"`), `gradle/libs.versions.toml:3` (`kotlin = "2.0.0"`)

Verified — `./gradlew :app:help` prints at configuration time, ten times:

```
ksp-2.0.21-1.0.25 is too new for kotlin-2.0.0. Please upgrade kotlin-gradle-plugin to 2.0.21.
```

And `kotlinCompilerPluginClasspathDebug` resolves `com.google.devtools.ksp:symbol-processing:2.0.21-1.0.25`
alongside `kotlin-compose-compiler-plugin-embeddable:2.0.0`, dragging `kotlin-stdlib:2.0.21` onto the
compiler plugin classpath.

**Impact.** KSP1 loads into the Kotlin compiler as a compiler plugin compiled against a different compiler
version's internal API. This is the mechanism that produces `NoSuchMethodError` / `AbstractMethodError`
during annotation processing. What runs through it here is Room's DAO and database code generation. It
works today at a patch-level skew; it is explicitly unsupported by the tool and will break on the next
version bump made in isolation. Also note the KSP version is hardcoded in `build.gradle.kts` rather than in
the version catalog (violates B1).

---

### BUILD-5 — Medium — Kotlin 2.1.0 stdlib compiled by a Kotlin 2.0.0 compiler

`gradle/libs.versions.toml:3,13`

`./gradlew :app:dependencyInsight --configuration debugCompileClasspath --dependency kotlin-stdlib`:

```
org.jetbrains.kotlin:kotlin-stdlib:{strictly 2.1.0} -> 2.1.0
  Selection reasons:
    - By conflict resolution: between versions 2.1.0 and 2.0.0
org.jetbrains.kotlin:kotlin-stdlib:2.1.0
  +--- org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.0
```

`kotlinx-serialization-json 1.8.0` pulls stdlib 2.1.0, which beats the Kotlin Gradle plugin's 2.0.0.

**Impact.** JetBrains' documented rule is that the stdlib must not be newer than the compiler. A 2.0.0
compiler sees `@SinceKotlin(2.1)` declarations it has no model for. No warning was emitted in the baseline
build and no breakage is observed, so this is a latent unsupported configuration rather than an active bug
— which is why it is Medium, not High. Same fix as BUILD-4: raise `kotlin`.

---

### BUILD-6 — Medium — CI builds only `assembleDebug`, only on push to `main`

`.github/workflows/main.yml:3-5,16-26`

```yaml
on:
  push:
    branches: [ main ]
...
      - uses: actions/setup-java@v3      # v4 is current; no `cache: gradle`
      - run: ./gradlew assembleDebug
```

No `pull_request` trigger, no `lintDebug`, no `testDebugUnitTest`, no `assembleRelease`, no
`gradle/actions/setup-gradle` or dependency caching.

**Impact.** Three separate holes. (a) No branch or PR is ever built — the current `claude-audit` branch has
never been through CI. (b) The 39 lint warnings and any test regression are invisible; CI green means only
"it compiled in debug". (c) Because CI never builds release, the entire BUILD-1 risk surface is guaranteed
to be discovered by a human, late. Every run is also a cold ~3min+ build with no Gradle cache.

---

### BUILD-7 — Medium — Teams notify step is a shell-injection surface that opens up the moment a PR trigger is added

`.github/workflows/main.yml:34-87`

```yaml
        run: |
          APK_URL="https://github.com/${{ github.repository }}/actions/runs/${{ github.run_id }}"
          PAYLOAD=$(cat <<EOF        # unquoted heredoc delimiter -> shell expansion inside
          ...
                        { "title": "Branch",  "value": "${{ github.ref_name }}" },
          ...
          curl -H "Content-Type: application/json" -d "$PAYLOAD" $TEAMS_WEBHOOK
```

`${{ github.* }}` expressions are substituted by Actions into the script text *before* bash sees it, inside
an unquoted heredoc. `$TEAMS_WEBHOOK` on line 87 is also unquoted.

**Impact.** Safe today only because the trigger is `push: branches: [main]`, so `github.ref_name` is always
the literal `main`. BUILD-6 recommends adding a `pull_request` trigger — at that moment `ref_name`/`head_ref`
become attacker-controlled (a branch name is arbitrary text) and this becomes remote code execution in CI
with access to `secrets.TEAMS_WEBHOOK_URL`. These two findings must be fixed together, not sequentially.
Separately, on a fork the secret is unset and `curl` receives no URL, failing the step.

---

### BUILD-8 — Medium — kapt for Hilt forces annotation processing back to Kotlin language version 1.9

`app/build.gradle.kts:7` (`kotlin("kapt")`), `app/build.gradle.kts:69` (`kapt(libs.hilt.android.compiler)`)

From `audit/00-baseline.md`:

```
w: Kapt currently doesn't support language version 2.0+. Falling back to 1.9.   (x3)
```

Hilt is on kapt while Room is on KSP — two annotation-processing passes over the same 64 files.
Dagger/Hilt 2.52 (the version in use) supports KSP.

**Impact.** kapt generates Java stubs for every Kotlin file on every build; it is the dominant incremental
build cost in a module this size, and it is the reason the module's annotation processing runs at language
version 1.9 rather than 2.0. Migrating Hilt to KSP removes kapt, the `kotlin("kapt")` plugin, and the
language-version fallback in one change.

---

### BUILD-9 — Medium — Room schema export unconfigured, no schema committed, no migration path

`app/src/main/java/com/peal/appscheduler/data/local/SchedulerAppDatabase.kt:13`,
`app/src/main/java/com/peal/appscheduler/di/DatabaseModule.kt:18-25`, `app/build.gradle.kts` (no `ksp { }` block)

```kotlin
@Database(entities = [ScheduleEntity::class], version = 1)
abstract class SchedulerAppDatabase : RoomDatabase()
```

`exportSchema` defaults to `true`; no `room.schemaLocation` KSP arg is set, hence the baseline's
`Schema export directory was not provided` warning (emitted twice). No `schemas/` directory exists in the
repo. The builder has neither `addMigrations(...)` nor `fallbackToDestructiveMigration()`:

```kotlin
Room.databaseBuilder(context, SchedulerAppDatabase::class.java, AppConstant.DATABASE_NAME).build()
```

**Impact.** Nothing is broken at version 1. But the app's entire value proposition is persisted schedules,
and the first change to `ScheduleEntity` throws
`IllegalStateException: A migration from 1 to 2 was required but not found` at `build()` — every user loses
every schedule, on app start, with no recovery. Schema export is also a one-way door: without a committed
`1.json`, the v1 schema can never be reconstructed exactly for `MigrationTestHelper`, so migration tests
become impossible to write correctly after the fact. Set this up before the first entity change, not after.

---

### BUILD-10 — Medium — No build caching, no parallelism, 2 GB heap

`gradle.properties:9,13`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# org.gradle.parallel=true      <- commented out
```

Absent: `org.gradle.caching`, `org.gradle.configuration-cache`, `org.gradle.parallel`, `kotlin.incremental`.
Present and correct: `android.useAndroidX`, `android.nonTransitiveRClass`, `kotlin.code.style`.

Configuration cache was probed read-only: `./gradlew :app:help --configuration-cache` → `BUILD SUCCESSFUL`,
`Configuration cache entry stored`. So the *configuration phase* is CC-compatible.

**Impact.** Baseline cold build was 3m16s; every local and CI build pays full cost with no build cache.
2048m is tight for kapt + KSP + (eventually) R8 in one daemon.

**unverified:** whether the full task graph is configuration-cache compatible. Confirming requires
`./gradlew assembleDebug --configuration-cache` — the Hilt Gradle plugin and kapt are the usual offenders,
and BUILD-8's kapt removal is a prerequisite worth doing first. Parallel/caching are safe to enable now;
`org.gradle.parallel` is a no-op benefit in a single-module build.

---

### BUILD-11 — Medium — Dependency stack is 1–2 years stale; Kotlin 2.0.0 is the root blocker

`gradle/libs.versions.toml` throughout

Versions below are Android Lint's own resolved index from `app/build/reports/lint-results-debug.xml` — not
asserted from memory. All 24 `GradleDependency` / `NewerVersionAvailable` warnings:

| Catalog line | Artifact | Current | Available |
|---|---|---|---|
| :3 | `org.jetbrains.kotlin.android` / `.plugin.compose` | 2.0.0 | 2.4.10 |
| :2 | `com.android.application` | 8.13.1 | 9.4.0 (or 8.13.2) |
| wrapper:4 | Gradle | 8.14.3 | 8.14.5 |
| :14 | Hilt (plugin + runtime + compiler) | 2.52 | 2.60.1 |
| :17 | Room (runtime/ktx/compiler/testing) | 2.6.1 | 2.8.4 |
| :10 | `compose-bom` | 2024.04.01 | 2026.08.00 |
| :13 | `kotlin.plugin.serialization` | 1.8.0 | 2.4.10 |
| :13 | `kotlinx-serialization-json` | 1.8.0 | 1.11.0 |
| :8 | `lifecycle-runtime-ktx` | 2.8.7 | 2.11.0 |
| :20 | `lifecycle-viewmodel-navigation3` | 2.10.0 | 2.11.0 |
| :9 | `activity-compose` | 1.10.0 | 1.13.0 |
| :4 | `core-ktx` | 1.15.0 | 1.19.0 |
| :19 | `navigation3-runtime` / `-ui` | 1.0.0 | 1.1.7 |
| :15 | `hilt-navigation-compose` | 1.2.0 | 1.4.0 |
| :16 | `desugar_jdk_libs` | 2.0.3 | 2.1.5 |
| :12 | `navigation-compose` | 2.8.7 | 2.10.0 (delete instead — BUILD-14) |
| :18 | `arch.core:core-testing` | 2.1.0 | 2.2.0 |
| :6,:7 | `androidx.test.ext:junit` / espresso | 1.2.1 / 3.6.1 | 1.3.0 / 3.7.0 |

**Impact.** Not "upgrade everything" — that is high-risk and low-value. The dependency order that matters:
Kotlin is the keystone, because raising it simultaneously retires BUILD-4 (KSP skew), BUILD-5 (stdlib
skew), BUILD-8's kapt fallback, and BUILD-12. Suggested sequence: (1) Kotlin + KSP + compose-compiler
plugin together, in lockstep; (2) Hilt (and move it to KSP); (3) Room; (4) the Compose BOM/material3
resolution from BUILD-3; (5) AGP 8.13.2 patch; (6) AGP 9 as its own separate project — that is a major
migration and the `agp-9-upgrade` skill is installed for it.

`OldTargetApi` at `app/build.gradle.kts:18` is in the lint report but is **not** a finding: targetSdk 36
meets current Play requirements. Lint is asking for the newest preview level. Safe to ignore.

---

### BUILD-12 — Medium — Serialization *plugin* version is taken from the runtime *library*'s version ref (works today by accident)

`gradle/libs.versions.toml:63`, `build.gradle.kts:6`, `app/build.gradle.kts:5`

```toml
serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlinxSerialization" }
```

`kotlinxSerialization = "1.8.0"` is the *runtime library* version. The Kotlin serialization Gradle plugin
is a Kotlin compiler plugin and must track the Kotlin compiler version (2.0.0), not the library. Lint sees
it as 1.8.0 and reports it as such.

**It is not currently broken, and it is worth saying so precisely.** Verified with `buildEnvironment` and
`:app:dependencies --configuration kotlinCompilerPluginClasspathDebug`:

```
org.jetbrains.kotlin.plugin.serialization...gradle.plugin:1.8.0
|    \--- org.jetbrains.kotlin:kotlin-serialization:1.8.0 -> 2.0.0     # rescued by kotlin-gradle-plugins-bom:2.0.0

kotlinCompilerPluginClasspathDebug
+--- org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable:2.0.0
```

`kotlin-gradle-plugins-bom:2.0.0` constrains `kotlin-serialization` to 2.0.0, so conflict resolution
upgrades it and the compiler plugin that actually loads is the correct 2.0.0 one.

**Impact.** A latent trap, not a live defect. It survives only because KGP 2.0.0 is also on the buildscript
classpath and its BOM wins the conflict. It produces permanent lint noise, it makes every Kotlin upgrade
depend on someone remembering to bump an unrelated version ref, and if plugin resolution order or that BOM
constraint ever changes it will silently load a 1.8.0 compiler plugin into a 2.x compiler. Point the alias
at `version.ref = "kotlin"`.

---

### BUILD-13 — Medium — No `lint { }` block at all

`app/build.gradle.kts:11-46`

The `android { }` block has no `lint { }`: no `abortOnError`, no `warningsAsErrors`, no `checkDependencies`,
no `baseline`. Combined with BUILD-6 (lint never runs in CI), lint findings have no enforcement mechanism
whatsoever.

**Impact.** 39 warnings with no ratchet — the count only goes up. More importantly `checkDependencies`
defaults to false, so lint does not analyze the merged manifest and resources from dependencies. This is
the concrete basis for the caveat already recorded in `audit/00-baseline.md`: lint's silence about the
exported `AppSchedulerReceiver`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` and exact
alarms is not evidence that those are correct. Phase 4 should not treat a clean lint run as coverage.

---

### BUILD-14 — Medium — Dead Navigation-2 dependency ships 473 classes into the release APK

`app/build.gradle.kts:59`, `gradle/libs.versions.toml:12,39`,
`app/src/main/java/.../ui/shared/navigation/AppSchedulerNavHost.kt:7-11`

```kotlin
import androidx.navigation.NavHostController          // unused
import androidx.navigation.compose.NavHost            // unused
import androidx.navigation.compose.composable         // unused
import androidx.navigation.compose.rememberNavController  // unused
import androidx.navigation.toRoute                    // unused
```

Confirmed unused: the file's body uses Nav3 (`rememberNavBackStack`, `entryProvider`, `NavDisplay`,
`DialogSceneStrategy`), and a repo-wide grep for `NavHost(`, `rememberNavController`, `toRoute`,
`composable(` outside those import lines returns nothing. 473 `androidx/navigation/*` classes are in the
release APK.

**Impact.** Leftover from the Nav2→Nav3 migration in commit `af9d383`. Size, plus genuine ambiguity for the
next reader about which navigation library is authoritative. Note `androidx.hilt:hilt-navigation-compose`
depends on `navigation-runtime` transitively, so not all 473 classes disappear — but the explicit
`navigation-compose` declaration and its `navigationCompose` catalog entry are pure dead weight.

---

### BUILD-15 — Low — No `debug` build type customisation; debug and release share the applicationId

`app/build.gradle.kts:25-33`

`buildTypes { }` declares only `release`. No `debug { applicationIdSuffix = ".debug" }`, no
`versionNameSuffix`.

**Impact.** Debug and release both install as `com.peal.appscheduler`, so they cannot coexist on a device
and installing a debug build overwrites a real one — including its Room database. Minor for a solo project,
annoying the first time it costs someone their test data.

---

### BUILD-16 — Low — `-Xjvm-default=all` is unnecessary here and is the deprecated flag spelling

`app/build.gradle.kts:41`

```kotlin
freeCompilerArgs += listOf("-Xjvm-default=all")
```

There are zero `.java` files under `app/src` (verified by `find`), and this is a single application module
with no external consumers. The flag's only effect is emitting interface default methods that nothing
outside the module implements.

**Impact.** None today. Kotlin later renamed this to the stable `-jvm-default` and deprecated the `-X`
spelling, so it will start emitting a warning during the BUILD-11 Kotlin upgrade. Either drop it or migrate
the spelling at that point — dropping is the smaller correct change.

---

### BUILD-17 — Low — `desugar_jdk_libs` 2.0.3 (2.1.5 available)

`gradle/libs.versions.toml:16`, `app/build.gradle.kts:35,90`

Core library desugaring is **genuinely required and correctly enabled** — `java.time` is used across 7
files with `minSdk = 24`, e.g. `domain/utils/DateTimeExtensions.kt:7-11` (`Instant`, `LocalDate`,
`LocalTime`, `ZoneId`, `DateTimeFormatter`), `ui/screens/schedule/SchedulerViewModel.kt:29-31`,
`ui/shared/components/DatePickerDialog.kt:12-14`. This is a correct configuration, not a defect.

**Impact.** 2.0.3 predates the 2.1.x line. Low on its own; bundle it with the AGP/compileSdk work in
BUILD-11 rather than as a standalone change.

---

### BUILD-18 — Low — Gradle wrapper has no `distributionSha256Sum`

`gradle/wrapper/gradle-wrapper.properties:4`

The wrapper jar and properties are both committed (good), but the distribution's checksum is not pinned.

**Impact.** The downloaded Gradle distribution's integrity is unverified. Low for a personal repo, standard
hardening for anything shared.

---

## Non-findings (recorded so nobody chases them)

These looked suspicious and are not problems. Each was checked.

- **The kapt "options were not recognized by any processor" warning is benign.** The baseline shows
  `'[dagger.fastInit, dagger.hilt.android.internal.disableAndroidSuperclassValidation, ...]'`. This does
  *not* mean Hilt's fastInit is off. `app/build/generated/hilt/component_sources/release/.../DaggerAppSchedulerApp_HiltComponents_SingletonC.java`
  contains 13 `SwitchingProvider` entries — fastInit is active in both debug and release. The warning comes
  from kapt's javac stub pass, which sees the options but has no processor registered for them.
- **`Deprecated Gradle features were used`** — sourced with `--warning-mode all`. It is
  `Configuration.fileCollection(Spec) has been deprecated`, emitted from a plugin (AGP/Hilt/KSP), not from
  any script in this repo. There is nothing to fix here directly; it clears with the BUILD-11 upgrades. It
  does mean the project cannot move to Gradle 9 / AGP 9 until those plugins are current.
- **No debug or test code leaks into the release APK.** The only `androidx/compose/ui/tooling/*` classes in
  the release dex are the 19 annotation classes from `ui-tooling-preview` (a correct `implementation`
  dependency). `ui-tooling` and `ui-test-manifest` are correctly `debugImplementation`, and no
  espresso/junit/androidx.test classes are present.
- **Release manifest is correct.** `aapt2 dump xmltree` on the release APK shows no `android:debuggable`,
  no `android:testOnly`, and `extractNativeLibs="false"`.
- **Reproducibility (B10) is in good shape.** Wrapper jar committed (`git ls-files gradle/wrapper/`), no
  dynamic `+` versions anywhere in the catalog, no snapshot repositories,
  `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` in `settings.gradle.kts:15`, and
  `pluginManagement` repositories are group-scoped with `includeGroupByRegex`. Only BUILD-18 is missing.
- **`OldTargetApi` (`app/build.gradle.kts:18`)** — targetSdk 36 meets current Play requirements. Lint wants
  the newest preview. Ignore or suppress.
- **B7 convention plugins — not applicable.** Single module, no repeated build logic. `build-logic/` would
  be overhead here, not an improvement.
- **Baseline profiles: partially present, but they never reach a device.** `audit/00-inventory.md` says "no
  baseline profile"; more precisely, AGP merged the AndroidX/Compose library profiles into
  `app/build/outputs/apk/release/baselineProfiles/{0,1}/app-release-unsigned.dm` (12 KB each). Because the
  build produces a bare APK and there is no `bundleRelease` (BUILD-19), those `.dm` files are never
  delivered. There is no app-authored profile and no `androidx.baselineprofile` plugin. Startup-performance
  consequences belong to Phase 3, not here.

---

## BUILD-19 — High — No signing config and no bundle target: there is no release artifact that can ship

`app/build.gradle.kts:11-46`

No `signingConfigs { }`, no `signingConfig` on the `release` build type, no keystore or `keystore.properties`
in the repo or `.gitignore`. The only release output is
`app/build/outputs/apk/release/app-release-unsigned.apk` (12,728,468 bytes). No `bundleRelease` has been
run; there is no AAB, which is what Play requires for new apps.

**Impact.** Combined with BUILD-1 and BUILD-6, the release path in this repo does not exist end to end.
Nothing has ever been minified, signed, bundled, or built by CI. If this project is ever meant to ship, the
first attempt will hit R8 keep-rule breakage, missing signing, and missing AAB config simultaneously —
which is the worst time to discover all three. This is the finding to fix first, because it is the one that
forces the others to be exercised.

---

## Summary

| ID | Sev | Title |
|---|---|---|
| BUILD-19 | High | No signing config / no bundle target — no shippable release artifact |
| BUILD-1 | High | R8 + resource shrinking disabled; keep rules never validated |
| BUILD-2 | High | `material-icons-extended`: 11,400 classes (42% of APK) for 2 core icons |
| BUILD-3 | High | Compose BOM inert; stack silently resolves to 1.9.5, not the pinned 1.6.6 |
| BUILD-4 | High | KSP 2.0.21 on Kotlin 2.0.0 — KSP prints "too new for kotlin-2.0.0" |
| BUILD-5 | Medium | Kotlin 2.1.0 stdlib compiled by a 2.0.0 compiler |
| BUILD-6 | Medium | CI runs only `assembleDebug`, only on push to `main` |
| BUILD-7 | Medium | Teams step is a shell-injection surface once a PR trigger is added |
| BUILD-8 | Medium | kapt for Hilt forces annotation processing to language version 1.9 |
| BUILD-9 | Medium | Room schema export unconfigured; no schema, no migrations |
| BUILD-10 | Medium | No build cache / config cache / parallel; 2 GB heap |
| BUILD-11 | Medium | Dependency stack 1–2 years stale; Kotlin is the root blocker |
| BUILD-12 | Medium | Serialization plugin version ref points at the runtime library (works by accident) |
| BUILD-13 | Medium | No `lint { }` block — no abortOnError, no checkDependencies, no baseline |
| BUILD-14 | Medium | Dead Nav2 dependency ships 473 classes |
| BUILD-15 | Low | No debug `applicationIdSuffix` |
| BUILD-16 | Low | `-Xjvm-default=all` unnecessary and deprecated spelling |
| BUILD-17 | Low | `desugar_jdk_libs` 2.0.3 (2.1.5 available) |
| BUILD-18 | Low | No wrapper `distributionSha256Sum` |

Zero Critical. Five High, and they are not five independent problems — BUILD-19, BUILD-1 and BUILD-6
are one problem seen from three angles (the release path is never exercised), and BUILD-4/BUILD-5/BUILD-12
plus BUILD-8's kapt fallback all clear with a single Kotlin upgrade. The two genuinely independent Highs
are BUILD-2 (delete one line, halve the app) and BUILD-3 (the version pinning is fiction).

## Coverage and limits

- Read in full: `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/proguard-rules.pro`,
  `.github/workflows/main.yml`, `.gitignore`, `SchedulerAppDatabase.kt`, `DatabaseModule.kt`,
  `AppSchedulerNavHost.kt` (imports + body).
- Read-only Gradle tasks run: `:app:dependencies` (releaseRuntimeClasspath, debugCompileClasspath, ksp,
  kotlinCompilerPluginClasspathDebug), `:app:dependencyInsight`, `buildEnvironment`, `:app:help`
  (plain, `--warning-mode all`, `--configuration-cache`). No recompiling task was run.
- APK analysis used the release APK already on disk from the baseline run, via `dexdump` and `aapt2` from
  build-tools 36.0.0.
- **Not done:** `bundleRelease` (no AAB exists to analyze), a minified build (would recompile), a
  `--profile` run, and full configuration-cache verification. The exact byte saving from BUILD-2 and the
  full CC compatibility in BUILD-10 are the two numbers this phase could not measure without rebuilding.
