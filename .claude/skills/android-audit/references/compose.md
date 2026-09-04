# Phase 2 — Jetpack Compose correctness & recomposition

Goal: find composables that are wrong (state bugs, lifecycle bugs) and composables that are slow
(unnecessary recomposition). Wrong first — slow is easier to live with than incorrect.

## Get the compiler metrics first

This is the single highest-signal artifact and most audits skip it. It tells you which composables are
restartable/skippable and which parameters are unstable — facts, not guesses.

Add to the module's build file (or `build.gradle.kts` root convention):

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

Then `./gradlew assembleRelease` and read `build/compose_compiler/*-composables.txt` and
`*-classes.txt`. Look for: composables marked `restartable` but not `skippable`, and classes marked
`unstable`. Each unstable class used as a composable parameter is a recomposition source.

Revert the build file change afterward unless the user wants it kept.

## Fast signals

```bash
grep -rn "collectAsState()" --include="*.kt"                    # should be collectAsStateWithLifecycle
grep -rn "LaunchedEffect(true)\|LaunchedEffect(Unit)" --include="*.kt"
grep -rn "@Composable" -A2 --include="*.kt" | grep -n "List<\|Set<\|Map<"   # unstable params
grep -rn "\.value\s*=" --include="*Screen.kt" --include="*.kt" | grep -i compose
grep -rn "mutableStateOf" --include="*.kt" | grep -v "remember"  # state without remember
grep -rn "Modifier\.fillMaxSize()\.\|\.background(" --include="*.kt" | head
grep -rn "SideEffect\|DisposableEffect\|rememberCoroutineScope" --include="*.kt"
grep -rn "LazyColumn\|LazyRow\|LazyVerticalGrid" -A6 --include="*.kt" | grep -c "key ="
```

## Correctness checklist

**C1 — `collectAsStateWithLifecycle`.** `collectAsState()` keeps collecting while the app is backgrounded:
wasted work, and upstream flows (location, network polling) keep running. *High.*

**C2 — State not remembered.** `mutableStateOf` without `remember` resets every recomposition. Usually a
bug that manifests as "the field clears itself while typing". *High.*

**C3 — `LaunchedEffect` keys.** `LaunchedEffect(Unit)` when the effect depends on a changing value means
the effect uses a stale value forever. Conversely, an unstable key restarts the effect every
recomposition — check for effects that fire repeatedly.

**C4 — Effects used for the wrong thing.** Navigation or one-shot events triggered from a composable body
instead of an effect will re-fire on recomposition. Look for `if (state.navigateNext) navigate()` in a
body without a consumption mechanism.

**C5 — `rememberCoroutineScope` misuse.** Fine for user-triggered work; a smell if used for anything that
should be in the ViewModel. Work started here dies with the composable — check nothing important depends
on it completing.

**C6 — `DisposableEffect` cleanup.** Every registered listener/callback/receiver needs an `onDispose`.
Missing cleanup here is a leak (see phase 3).

**C7 — Modifier contract.** `Modifier` should be the first optional parameter, named `modifier`, defaulted
to `Modifier`, applied to the outermost node, and not applied twice. Violations break caller layout in
ways that look like the caller's fault. *Medium, but usually a large cluster.*

**C8 — State hoisting.** Reusable composables should be stateless where practical. A leaf component owning
state it doesn't need makes it untestable and unpreviewable.

**C9 — Lazy list keys.** `items(list)` without a `key` causes wrong item state on reorder/delete and kills
item-level animation and reuse. *High for dynamic lists, Low for static.*

**C10 — Nested scrollables.** A `LazyColumn` inside a vertically scrollable `Column` throws or forces
infinite measurement. Also check `LazyColumn` given an unbounded height.

## Performance checklist

**P1 — Unstable parameters.** `List<T>` params make a composable unskippable. Use `ImmutableList`
(kotlinx-collections-immutable) or `@Immutable`/`@Stable` annotations where the guarantee is genuinely
true — annotating a lying class is worse than the original problem.

**P2 — Lambda allocation / unstable callbacks.** Lambdas capturing unstable values defeat skipping. Method
references and stable captures help.

**P3 — Reading state too high.** Reading a frequently-changing value (scroll offset, animation, text
field) in a parent recomposes the whole subtree. Push the read down via lambda-based modifiers
(`Modifier.offset { }`, `graphicsLayer { }`, `drawBehind { }`) so it happens in layout/draw instead of
composition. This is the classic scroll-jank fix.

**P4 — Derived state.** Computation on every recomposition that should be `derivedStateOf` (e.g.
`scrollState.firstVisibleItemIndex > 0`). Conversely, flag `derivedStateOf` wrapping something cheap —
it has its own overhead and is frequently cargo-culted.

**P5 — Work in composable bodies.** Sorting, filtering, formatting, regex, date parsing on every
recomposition. Move to the ViewModel or wrap in `remember(key)`.

**P6 — Expensive object creation.** `SimpleDateFormat`, `NumberFormat`, `Paint`, `Brush` built inline.
`remember` them.

**P7 — Over-invalidation from theme/CompositionLocal.** A frequently-changing value in a
`CompositionLocal` invalidates every reader.

**P8 — Image loading.** Loading at full resolution into a small target; missing size hints in Coil/Glide.
Common cause of memory pressure and jank on lists.

## Verification

Don't file a performance finding on reasoning alone if you can measure it. The Layout Inspector's
recomposition counts, the compiler metrics report, or a macrobenchmark with `FrameTimingMetric` all give
real numbers. Mark anything unmeasured as **unverified** in the report.
