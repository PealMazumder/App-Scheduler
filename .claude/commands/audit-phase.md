---
description: Run or re-run a single audit phase
argument-hint: architecture | compose | performance | security | build | testing | ux-a11y [+ optional module]
---

Run just the **$ARGUMENTS** phase of the audit.

Read `.claude/skills/android-audit/SKILL.md` for the rules, then the matching reference in
`.claude/skills/android-audit/references/`. If `audit/00-inventory.md` exists, read it instead of
re-deriving the project structure.

Write the phase output to the corresponding `audit/0N-*.md`, and append or update any new findings in
`audit/findings.json` (preserve existing `status` values on findings that are already there).
