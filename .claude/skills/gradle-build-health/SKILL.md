---
name: gradle-build-health
description: Manage the Gradle build for an Android project — version catalogs, dependency upgrades, build types and flavors, R8/ProGuard rules, build speed (configuration cache, KSP vs KAPT), convention plugins, APK/AAB size, and release configuration. Use this whenever editing any build.gradle.kts, libs.versions.toml, settings.gradle.kts, gradle.properties, or proguard rules; whenever adding or upgrading a dependency; whenever builds are slow; and whenever the app crashes only in release builds.
---

# Gradle build health

Full audit checklist: `../android-audit/references/build-release.md`.

## Before touching versions

**Do not write a version number from memory.** Training data goes stale and a wrong version wastes a full
sync cycle or silently pulls something incompatible. Read `gradle/libs.versions.toml` for what's there,
and look up the current release before changing anything (`android docs`, the library's release notes).

Upgrade in sequence, not in bulk: Gradle wrapper → AGP → Kotlin → Compose compiler/BOM → libraries.
Each step gets its own commit and its own `./gradlew assembleDebug testDebugUnitTest`. A single commit
bumping thirty dependencies is unbisectable when something breaks — and something will break.

## Version catalog

Everything in `libs.versions.toml`, referenced as `libs.androidx.core.ktx`. Hardcoded coordinates in a
module build file are how you end up with two versions of the same library and a runtime `NoSuchMethodError`.

## Release builds are a different program

The most common shipped-and-broken bug is code that works in debug and crashes in release, because R8
removed or renamed something reached by reflection.

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

Rules to check:
- Serialization models (Gson/Moshi/kotlinx), Room entities, and anything reached reflectively need keeps —
  or, better, annotation-driven rules shipped by the library itself.
- `-keep class com.myapp.** { *; }` defeats minification entirely. Narrow it.
- **Always smoke-test an actual release build.** Reading the rules is not verification. Google's
  `r8-analyzer` skill is the right tool for diagnosing what got stripped.

Also confirm: release isn't `debuggable`, isn't signed with debug keys, and logging is stripped.

## Build speed

Check `gradle.properties` for configuration cache, build cache, and parallel execution, and prefer KSP
over KAPT — KAPT forces Java stub generation and is often the single largest chunk of build time in a
Kotlin project.

Measure before optimizing: `./gradlew assembleDebug --profile` or a build scan. Build-time advice given
without a profile is guessing.

## Convention plugins

Past roughly five modules, copy-pasted `android { }` blocks drift. Move shared config into `build-logic/`
convention plugins so there's one place to change compileSdk, Java target, and common dependencies.

## Adding a dependency

Ask first — it's a decision, not an implementation detail. Then check: transitive weight
(`./gradlew :app:dependencies`), permissions it merges into the manifest (check the merger report), whether
it's maintained, and whether the standard library already covers it.
