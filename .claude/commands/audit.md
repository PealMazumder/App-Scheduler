---
description: Run a full phased audit of the codebase
argument-hint: [scope, e.g. "whole repo" or ":feature:checkout"]
---

Run a full audit using the `android-audit` skill. Scope: $ARGUMENTS (if empty, the whole repo).

Read `.claude/skills/android-audit/SKILL.md` first and follow its phased workflow. Key expectations:

- Write each phase to `audit/` as you go. Don't hold the whole codebase in context.
- Run the real tools before reading code by hand: `./gradlew lintDebug`, a release build, and the Compose
  compiler metrics. Tool output beats inspection.
- Every finding needs `file:line` evidence. Mark anything you inferred but did not confirm as
  **unverified**, with the measurement that would confirm it.
- Produce `audit/REPORT.md` and `audit/findings.json`.
- Do not fix anything yet.

Track phases in your todo list so nothing gets silently dropped. If you run low on context, finish the
current phase, write its file, and tell me where to resume.
