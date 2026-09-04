# 00 — Build baseline (Audit Stage 2)

Run on 2026-09-04, macOS (darwin 25.6.0), JDK/Gradle 8.14.3, branch `claude-audit` @ `be0f508`.

```
./gradlew assembleDebug lintDebug testDebugUnitTest assembleRelease
```

## Result

**BUILD SUCCESSFUL in 3m 16s — exit code 0. 123 actionable tasks, 123 executed** (cold, no build cache).

| Task | Result |
|---|---|
| `assembleDebug` | ✅ pass — `app-debug.apk`, 19,342,354 bytes (18.4 MB) |
| `lintDebug` | ✅ pass — **0 errors, 39 warnings** |
| `testDebugUnitTest` | ✅ pass — **1 test, 0 failures, 0 skipped** (`ExampleUnitTest`, 0.001s) |
| `assembleRelease` | ✅ pass — `app-release-unsigned.apk`, 12,728,468 bytes (12.1 MB) |

The debug build is **not** broken. Proceeding with the audit.

## Compiler / annotation-processor warnings

```
w: [ksp] data/local/SchedulerAppDatabase.kt:13: Schema export directory was not provided to the
   annotation processor so Room cannot export the schema. You can either provide
   `room.schemaLocation` ... OR set exportSchema to false.       (emitted twice)

w: Kapt currently doesn't support language version 2.0+. Falling back to 1.9.   (x3)

warning: The following options were not recognized by any processor:
   '[dagger.fastInit, dagger.hilt.android.internal.disableAndroidSuperclassValidation,
     dagger.hilt.android.internal.projectType,
     dagger.hilt.internal.useAggregatingRootProcessor, kapt.kotlin.generated]'

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.
```

Zero Kotlin `w:` warnings from the app's own source — no unused-variable/deprecation noise in
production code beyond the above. Notably the five dead `androidx.navigation.*` imports in
`AppSchedulerNavHost.kt` are not flagged by the compiler at warning level.

## Lint breakdown (`app/build/reports/lint-results-debug.xml`)

| Severity | Issue id | Count |
|---|---|---|
| Warning | `GradleDependency` | 17 |
| Warning | `NewerVersionAvailable` | 7 |
| Warning | `UnusedResources` | 8 |
| Warning | `AndroidGradlePluginVersion` | 2 |
| Warning | `UseKtx` | 2 |
| Warning | `OldTargetApi` | 1 |
| Warning | `RedundantLabel` | 1 |
| Warning | `TypographyEllipsis` | 1 |
| **Total** | | **39** |

Non-version-bump issues in full:

- `app/build.gradle.kts:18` `OldTargetApi` — not targeting the latest Android version.
- `AndroidManifest.xml:28` `RedundantLabel` — `MainActivity`'s `android:label` duplicates the app label.
- `res/values/colors.xml:3-9` — all 7 template colors (`purple_200/500/700`, `teal_200/700`,
  `black`, `white`) unused.
- `res/values/strings.xml:23` — `another_app_is_already_scheduled_at_this_time_...` unused
  (relevant: a conflict-detection message exists as a string but nothing references it).
- `res/values/strings.xml:21` `TypographyEllipsis`.
- `ui/utils/ImageUtils.kt:19` `UseKtx` — prefer `createBitmap`.
- `MainActivity.kt:107` `UseKtx` — prefer `String.toUri`.

## Caveats on this baseline

- **Lint's clean bill on the manifest/security side is not strong evidence.** `lintOptions` is
  unconfigured: `abortOnError` is default, `checkDependencies` is off, and no baseline file is in
  use. Lint reported nothing about `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, the
  exported receiver, or exact alarms — absence of a lint warning is not proof those are correct.
- **`assembleRelease` exercised no R8 pass.** `isMinifyEnabled = false`, so this build proves
  nothing about keep rules; the 12.1 MB release APK is an un-shrunk figure. A real minified build
  has never been run in this repo.
- **The release APK is unsigned** — no `signingConfig` exists.
- **`testDebugUnitTest` passing is meaningless as a quality signal.** The single test asserts
  `2 + 2 == 4`. Zero lines of production code are covered by JVM tests.
- **`connectedDebugAndroidTest` was NOT run** — no device or emulator attached. `ScheduleDaoTest`
  (the only substantive test in the repo) is therefore unverified in this audit.
