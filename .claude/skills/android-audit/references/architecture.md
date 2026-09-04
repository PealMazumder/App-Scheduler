# Phase 1 — Architecture & modularization

Goal: find places where responsibilities leak across layers, because those are what make every future
change expensive. Ignore naming debates; look for coupling that will bite.

## Fast signals

```bash
# ViewModels that know about Android UI or Context — layering violation
grep -rn "import android\.\(content\.Context\|widget\|view\|app\.Activity\)" --include="*ViewModel.kt"
grep -rn "androidx.compose" --include="*ViewModel.kt"

# UI reaching straight into data
grep -rln "Repository\|ApiService\|Dao" --include="*Screen.kt" --include="*Composable*.kt"

# Framework types crossing into domain
grep -rn "import retrofit2\|import androidx.room\|import okhttp3" --include="*.kt" | grep -i "domain\|usecase\|model"

# God classes
find . -name "*.kt" -not -path "*/build/*" -exec wc -l {} + | sort -rn | head -25

# Singletons / global state
grep -rn "^object \|companion object" --include="*.kt" | grep -v Test | head -40
grep -rn "GlobalScope" --include="*.kt"
```

## Checklist

**A1 — Layer separation.** UI → ViewModel/presentation → domain (optional) → data. Check the arrows only
point down. A repository importing a composable, or a ViewModel constructing a Retrofit service, is a
finding. *Severity: High if pervasive, Medium if isolated.*

**A2 — Single source of truth per piece of state.** The same value derived independently in two places
will drift. Look for the same field held in both a ViewModel and a `remember` in the screen.

**A3 — UI state shape.** One immutable state class per screen, exposed as `StateFlow`. Findings: many
separate `StateFlow`s per screen (causes torn/inconsistent frames), `MutableStateFlow` exposed publicly,
nullable-everything state where a sealed hierarchy (`Loading`/`Content`/`Error`) is the honest model.

**A4 — Events vs state.** One-shot events (navigation, snackbar) modeled as state fields cause replay on
config change. Check for `Channel`/`SharedFlow` or a consumed-event pattern.

**A5 — Repository contract.** Does it return `Flow` for observable data and suspend for one-shot? Does it
own the caching/offline decision, or does that logic leak into ViewModels?

**A6 — Error handling.** Look for a consistent result type (`Result`, sealed `Outcome`) vs. exceptions
thrown across layers vs. silent `catch { }`. Check `CancellationException` is rethrown — swallowing it
breaks structured concurrency in ways that surface as "this screen randomly stops updating".

**A7 — Dispatchers.** Injected, not hardcoded. `withContext(Dispatchers.IO)` scattered through ViewModels
means those functions can't be tested deterministically. Prefer the suspend function being main-safe at
its own boundary (usually the data layer).

**A8 — Module graph.** Check for cycles, for a "core"/"common" module that everything depends on and that
depends on everything (a monolith with extra steps), and for feature-to-feature dependencies that should
go through a shared API module. `./gradlew projects` plus each module's `dependencies` block.

**A9 — DI hygiene.** Correct scoping (`@Singleton` on things holding per-user state is a bug), no service
locator smuggled in alongside DI, no `Context` injected where `ApplicationContext` is meant.

**A10 — Navigation.** Type-safe routes vs stringly-typed. Whether ViewModels are scoped to the right
back-stack entry. Deep link handling validated (see the security phase — deep links are an attack surface).

**A11 — Domain layer necessity.** If use-cases exist, are they doing work, or are they one-line
pass-throughs? Ceremony without benefit is a real maintenance cost and worth flagging as Medium.

## Judgment note

Architecture findings are the easiest place to substitute dogma for analysis. Before filing one, answer:
what specific future change does this make harder, and how much? If you can't answer, it's Info.
