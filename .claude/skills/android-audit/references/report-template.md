# Report format

Write `audit/REPORT.md` in this structure. Keep it readable by a tech lead in ten minutes.

```markdown
# Android codebase audit — <project> — <date>

## Scope & method
What was audited, what was sampled vs read fully, what was skipped and why, what tools were run
(lint, compose metrics, release build, device tests). State coverage honestly.

## Verdict
3–5 sentences. What state is this codebase in, what are the top three risks, what should happen first.
No hedging, no padding.

## What's working
Genuinely good patterns worth preserving. Tells the reader what not to churn.

## Findings

### Critical
| ID | Finding | Location | Impact |
|---|---|---|---|
| SEC-01 | Exported activity accepts unvalidated file URI | `ShareActivity.kt:34` | Any app can trigger upload of arbitrary readable files |

### High
...

### Medium / Low
Cluster these. "MED-04: 23 composables missing `modifier` parameter — full list in Appendix A."

## Fix plan
Ordered by severity × effort, in shippable batches.

**Batch 1 — <name> (est. <n> days)**
- SEC-01, SEC-03 — ...
- Verification: <what proves it's fixed>

## Unverified hypotheses
Things you suspect but did not confirm, and the measurement that would confirm each.

## Appendices
Full lists for clustered findings; raw tool output paths.
```

## findings.json

Also emit this so fixes can be tracked and picked up mechanically.

```json
{
  "project": "string",
  "audited_at": "YYYY-MM-DD",
  "coverage": { "modules_read_fully": [], "modules_sampled": [], "skipped": [] },
  "findings": [
    {
      "id": "SEC-01",
      "phase": "security",
      "severity": "critical",
      "title": "Exported activity accepts unvalidated file URI",
      "locations": ["app/src/main/java/.../ShareActivity.kt:34"],
      "impact": "Any installed app can cause the user's readable files to be uploaded",
      "fix": "Validate the URI's authority against an allowlist; require a signature permission",
      "effort": "S",
      "verified": true,
      "verification_method": "manifest review + adb am start reproduction",
      "status": "open"
    }
  ]
}
```

`effort`: S (<2h), M (<1d), L (multi-day), XL (project).
`status`: open | in_progress | fixed | wontfix | deferred.
`verified`: whether you confirmed it, versus inferred it from reading code.
