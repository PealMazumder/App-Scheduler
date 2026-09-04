---
description: Fix one audit finding, verify it, and update tracking
argument-hint: <finding ID, e.g. SEC-01> or a batch name
---

Fix **$ARGUMENTS** from `audit/findings.json`.

Process:

1. Read the finding and open every location it cites. Confirm it's still real — the code may have moved,
   or the finding may have been wrong. If it isn't real, mark it `wontfix` with a reason and stop; don't
   invent work to justify the entry.
2. Read the relevant skill before changing code (`compose-quality`, `android-architecture`, etc.).
3. Find an existing correct example of the pattern in this codebase and match it.
4. Make the smallest change that is actually correct. Fix only this finding — if you spot others, list
   them at the end rather than folding them into the diff. Mixed diffs get reverted wholesale.
5. Add or update a test that would have caught this, where the finding is testable.
6. Verify: `./gradlew assembleDebug lintDebug testDebugUnitTest`. For a performance finding, measure
   before and after — an unmeasured performance fix is a guess. For an R8 or security finding, build and
   smoke-test the release variant.
7. Update the finding's `status` in `audit/findings.json`.

Report what changed, what you verified and how, and anything you're unsure about. If verification failed,
say so plainly rather than describing the change as done.
