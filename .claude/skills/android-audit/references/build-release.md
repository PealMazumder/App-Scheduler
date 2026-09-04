# Phase 5 — Build, dependencies, release

## Fast signals

```bash
cat gradle/libs.versions.toml
cat settings.gradle.kts gradle.properties
./gradlew :app:dependencies --configuration releaseRuntimeClasspath > /tmp/deps.txt
./gradlew assembleRelease --scan          # or without --scan
grep -rn "implementation(\"" --include="build.gradle.kts" | grep -v libs\.   # hardcoded coords
find . -name "proguard-rules.pro" -exec cat {} +
```

## Checklist

**B1 — Version catalog.** All dependencies in `libs.versions.toml`, no hardcoded coordinates in module
build files, no duplicate versions of the same library. *Medium.*

**B2 — Version currency.** Check what's outdated, but **do not assert current versions from memory** —
look them up. Prioritize: security patches > libraries blocking a Compose/Kotlin upgrade > everything
else. A wholesale "upgrade all dependencies" recommendation is low-value and high-risk; recommend a
sequence.

**B3 — SDK levels.** `targetSdk` behind the Play requirement blocks updates on a deadline. Note the
behavior changes each skipped API level implies rather than just "bump it".

**B4 — Build config correctness.** Release build has `isMinifyEnabled = true` and `isShrinkResources =
true`; debug isn't signed with the release key; `debuggable` false in release; build types and flavors
make sense.

**B5 — R8 / keep rules.** Over-broad keep rules (`-keep class com.myapp.** { *; }`) defeat the point of
minification. Missing rules cause release-only crashes on reflection (Gson/Moshi models, Room, DI).
Google's `r8-analyzer` skill is the right tool here. Verify by actually running the release build and
smoke-testing it, not by reading rules.

**B6 — Build performance.** Configuration cache and build cache enabled; `org.gradle.parallel`; non-
transitive R classes; KSP rather than KAPT (KAPT is a large, avoidable build-time cost). Check with
`./gradlew assembleDebug --profile`.

**B7 — Convention plugins.** Repeated build logic copy-pasted across modules vs. `build-logic/`
convention plugins. Worth flagging above ~5 modules.

**B8 — APK/AAB size.** `./gradlew :app:bundleRelease` then inspect with the APK Analyzer. Look for
unstripped native libs, uncompressed assets, duplicated resources, and dependencies pulling in far more
than they're used for.

**B9 — CI.** Does CI run lint + unit tests + a release build? A green CI that only runs `assembleDebug`
gives false confidence — release-only R8 breakage is the most common shipped-and-broken class of bug.

**B10 — Reproducibility.** Gradle wrapper checked in and verified, JDK version pinned (`foojay`/toolchains),
no dependency on `+` or dynamic versions, no snapshot repos in release.
