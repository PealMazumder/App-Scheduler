---
description: Review the Compose UI in specified files
argument-hint: <file or directory>
---

Review the composables in $ARGUMENTS against `.claude/skills/compose-quality/SKILL.md` and the checklist
in `.claude/skills/android-audit/references/compose.md`.

Check specifically: Modifier contract, state hoisting, `collectAsStateWithLifecycle`, effect keys and
cleanup, lazy list keys, parameter stability, work done in composable bodies, and accessibility
(content descriptions, touch targets, text scaling).

Output a short list of issues with `file:line`, severity, and the corrected code for each. Separate
**correctness bugs** from **performance concerns** from **style** — they warrant different urgency, and
mixing them makes the important ones easy to miss. Don't rewrite the files unless I ask.
