# Phase 3 — Lifecycle, memory, startup

Goal: leaks, ANRs, and slow startup. These are the findings users actually feel.

## Fast signals

```bash
grep -rn "GlobalScope\|CoroutineScope(Dispatchers" --include="*.kt"
grep -rn "runBlocking" --include="*.kt" | grep -v Test
grep -rn "registerReceiver\|addListener\|registerCallback\|addObserver" --include="*.kt"   # each needs a matching remove
grep -rn "companion object" -A5 --include="*.kt" | grep -i "context\|activity\|view"        # static context = leak
grep -rn "Thread(\|AsyncTask\|Handler()" --include="*.kt"
grep -rn "\.await()\|Tasks.await" --include="*.kt" | grep -v Test
cat app/src/main/AndroidManifest.xml     # look at Application class, providers, startup work
```

## Checklist

**L1 — Scope leaks.** `GlobalScope` or a manually created `CoroutineScope` with no cancellation. Work
outlives its owner and holds references. *High.*

**L2 — Context in static/long-lived fields.** Activity or View reference in a `companion object`,
singleton, or `object`. Classic leak. *Critical if the object is long-lived.*

**L3 — Unbalanced registration.** Every `register*`/`add*Listener`/`addObserver` needs its unregister on
the correct lifecycle event or in `onDispose`. Grep counts of register vs unregister per file is a fast
heuristic.

**L4 — Main-thread blocking.** `runBlocking` in production code, synchronous disk/network/SharedPreferences
reads on the main thread, large JSON parse on main. ANR territory. *High/Critical.*

**L5 — Startup cost.** Everything in `Application.onCreate()` runs before first frame. Check for SDK
initialization, DB opens, and eager DI graph construction there. Prefer `androidx.startup`, lazy init, or
background init. Measure with `adb shell am start -W` or a startup macrobenchmark rather than guessing.

**L6 — Baseline Profile.** Absent baseline profiles cost roughly 20–30% on cold start and scroll jank on
first runs. Check for `baselineprofile` module / `baseline-prof.txt`. *High if missing on a shipping app.*

**L7 — Process death / state restoration.** `SavedStateHandle` used for state that must survive process
death (search queries, form input, scroll position for deep-linked content). Test with "Don't keep
activities" or `adb shell am kill`. Missing restoration is invisible in normal testing and very visible to
users on low-memory devices.

**L8 — Config change handling.** Rotation, dark mode, font scale, window resize on foldables/tablets. Look
for `android:configChanges` used to dodge the problem rather than solve it.

**L9 — WorkManager vs ad-hoc background work.** Long-running work not using WorkManager will be killed.
Check constraints and backoff policy are set sensibly.

**L10 — Bitmap/memory.** Large drawables in `res/` without density variants, full-size bitmaps in memory,
missing `Coil`/`Glide` size hints.

**L11 — Database work.** Room queries returning `Flow` (good) vs one-shot reads on main; missing indices on
columns used in `WHERE`/`ORDER BY`; migrations present for every schema version and actually tested.

## Verification

For anything here, prefer evidence:

```bash
adb shell am start -W <pkg>/<activity>       # startup timing
adb shell dumpsys meminfo <pkg>
adb shell dumpsys gfxinfo <pkg> framestats
```

LeakCanary in debug builds is the cheapest way to convert "suspected leak" into a confirmed one — if it's
not in the project, recommending it is itself a reasonable finding.
