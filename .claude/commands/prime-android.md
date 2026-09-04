---
description: Inspect the repo and fill in the Project facts block in CLAUDE.md
allowed-tools: Bash(./gradlew:*), Bash(find:*), Bash(cat:*), Bash(grep:*), Bash(wc:*), Read, Edit, Glob, Grep
---

Establish ground truth about this project and record it, so no later session has to guess.

Gather:

```bash
cat settings.gradle.kts 2>/dev/null || cat settings.gradle
cat gradle/libs.versions.toml
find . -name "build.gradle.kts" -not -path "*/build/*" | head -30
grep -rn "minSdk\|targetSdk\|compileSdk\|applicationId" --include="build.gradle.kts" | grep -v "/build/"
find . -name "*.kt" -not -path "*/build/*" | wc -l
find . -path "*/src/test/*" -name "*.kt" | wc -l
find . -path "*/src/androidTest/*" -name "*.kt" | wc -l
cat app/src/main/AndroidManifest.xml
ls .github/workflows/ 2>/dev/null
```

Determine from evidence, not assumption:
- Module layout and what each module is for
- DI framework (Hilt / Koin / manual) and how the graph is rooted
- Navigation approach (Nav 2 Compose / Nav3 / custom) and whether routes are type-safe
- Networking, persistence, image loading, serialization libraries
- Test stack and rough test counts
- CI setup and what it actually runs
- Whether baseline profiles, R8, and convention plugins are present

Then edit `CLAUDE.md`, replacing the `Project facts` block with what you found. For anything you could not
determine, write `unknown — <what you'd need to check>` rather than a plausible guess. A wrong fact in
CLAUDE.md is worse than a missing one, because every later session trusts it.

Finish with a short summary of the project's shape and anything that surprised you.
