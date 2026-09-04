---
description: Run the full local verification suite and report honestly
allowed-tools: Bash(./gradlew:*), Read
---

Run, in order, and report the real result of each:

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

For each: pass/fail, and for failures the actual error with the file and line. Read the lint report at
`app/build/reports/lint-results-debug.html` (or the XML) and summarize new or high-severity issues rather
than dumping the whole thing.

Do not paper over failures or describe a partial run as green. If something fails for an environmental
reason (no device, missing SDK), say that specifically instead of reporting it as a code problem.
