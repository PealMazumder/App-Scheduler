---
name: android-audit
description: Systematically audit a Kotlin/Jetpack Compose Android codebase for architecture, Compose correctness and performance, memory/lifecycle leaks, security, build health, testing, and accessibility — producing evidence-backed findings with file:line references and a prioritized fix plan. Use this whenever the user asks to audit, review, assess, health-check, modernize, "find problems in", "clean up", or "apply best practices to" an Android project, a module, or a screen — and also when they ask a broad question like "is this codebase any good" or "what should I fix first". Use it even for single-module or single-file reviews; just run fewer phases.
---

# Android codebase audit

An audit is not a vibe check. Its output is a set of **findings**, each with an ID, a severity, concrete
evidence (`file:line`), a stated impact, and a fix. Anything you can't point at in the code is an
observation, not a finding — label it as such.

## The failure mode this skill exists to prevent

The naive approach is to read the whole repo and then write a report. That fails two ways: context runs
out halfway through and quality collapses on later modules, and the report drifts into generic advice
("consider using MVVM") that the user could have gotten without you.

So: **audit in phases, write findings to disk as you go, and keep the working set small.** The `audit/`
directory is your external memory. You should be able to lose all context between phases and resume.

## Workflow

### Phase 0 — Scope and setup

Ask the user exactly two things if not already clear, then proceed. Don't interrogate.

1. Scope: whole repo, or specific modules/features?
2. Goal: broad health check, or driving toward something specific (a release, a rewrite, onboarding a team)?

Then build the map:

```bash
mkdir -p audit/findings
./gradlew projects                                  # module graph
find . -name "*.kt" -not -path "*/build/*" | wc -l  # size
cat gradle/libs.versions.toml
cat settings.gradle.kts
```

Write `audit/00-inventory.md`: modules and their purpose, LOC per module, dependency versions, minSdk/
targetSdk, DI framework, navigation approach, test counts. This is the reference every later phase reads
instead of re-deriving. Also record what you could NOT determine.

**Sizing:** under ~15k lines, read broadly. Over that, sample: every module's DI setup and public API,
every ViewModel, the 10 largest composables, all of `:app`, and anything that greps as risky. State your
sampling strategy in the report — an audit that hides its coverage is not trustworthy.

### Phase 1–7 — Run the checklists

Each phase has a reference file. Read it, run its greps, record findings, write the phase file, then move
on. Do not carry a phase's raw file contents into the next phase.

| Phase | Reference | Output |
|---|---|---|
| 1 | `references/architecture.md` | `audit/01-architecture.md` |
| 2 | `references/compose.md` | `audit/02-compose.md` |
| 3 | `references/performance-lifecycle.md` | `audit/03-performance.md` |
| 4 | `references/security-privacy.md` | `audit/04-security.md` |
| 5 | `references/build-release.md` | `audit/05-build.md` |
| 6 | `references/testing.md` | `audit/06-testing.md` |
| 7 | `references/ux-a11y.md` | `audit/07-ux-a11y.md` |

Skip phases the scope doesn't touch, and say in the report that you skipped them. Silent omission reads
as a clean bill of health.

**Let the tools do the mechanical work first.** Before reading code by hand, harvest:

```bash
./gradlew lintDebug                      # then read build/reports/lint-results-*.html or .xml
./gradlew assembleRelease                # R8 warnings, missing keep rules
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

Compose compiler metrics are the highest-value signal available and most audits skip them — see
`references/compose.md` for how to enable and read them.

### Phase 8 — Report

Write `audit/REPORT.md` using `references/report-template.md`, plus `audit/findings.json` (schema in the
template) so fixes can be picked up mechanically afterward.

Report rules:
- **Lead with the three things that matter**, not with a category listing.
- **Order by severity × effort**, not by phase order.
- **Every finding cites code.** `feature/home/HomeViewModel.kt:47` — not "some ViewModels".
- **Count and cluster.** "23 composables missing `Modifier` param (list in appendix)" beats 23 findings.
- **Say what's good too.** A report that's 100% criticism gets read as noise. If the DI setup is clean,
  say so — it tells the user what not to touch.
- **No fix instructions in the report body.** One-line summary per finding; detail goes in the fix plan.

## Severity

Assign from consequence, not from how much the pattern annoys you.

| | Meaning |
|---|---|
| **Critical** | Ships a security hole, data loss, crash on a common path, or blocks release |
| **High** | Real user-visible impact (jank, ANR risk, leak, broken a11y) or a correctness bug |
| **Medium** | Maintainability/consistency cost that will compound; wrong-but-not-yet-harmful patterns |
| **Low** | Style, naming, minor duplication |
| **Info** | Observation, no action implied |

A missing `Modifier` parameter is Medium, not High. Reserve Critical for things you would call someone
about on a weekend. Inflated severity makes the whole report ignorable.

## After the audit

Don't start fixing unless asked. When you do:
- One finding (or one tight cluster) per commit.
- Verify after each: `./gradlew assembleDebug lintDebug testDebugUnitTest`.
- **For performance findings, measure before and after.** An unmeasured performance "fix" is a guess with
  extra steps. Use the Android profiler skill or a macrobenchmark.
- Update `audit/findings.json` status as you go.

## Honesty requirements

These matter more than thoroughness:

- If you didn't read a module, say so.
- If a finding is a hypothesis (suspected leak, suspected recomposition), mark it **unverified** and state
  what measurement would confirm it.
- If the codebase is mostly fine, the report should say the codebase is mostly fine. Manufacturing
  findings to justify the exercise wastes the user's time and buries the real issues.
- If a pattern deviates from Google's guidance but is *deliberate and working*, note it as a tradeoff, not
  a defect. You are auditing a real app with history, not grading homework.
