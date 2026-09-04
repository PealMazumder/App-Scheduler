# Phase 7 — UX, accessibility, adaptive UI

## Fast signals

```bash
grep -rn "contentDescription" --include="*.kt" | wc -l
grep -rn "Icon(\|Image(" --include="*.kt" | wc -l          # compare to the above
grep -rn "contentDescription = \"\"" --include="*.kt"       # correct for decorative, check intent
grep -rn "\.size(\|\.height(\|\.width(" --include="*.kt" | grep -o "[0-9]\+\.dp" | sort -n | head
grep -rn "sp\b" --include="*.kt" | wc -l                    # text should use sp, not dp
grep -rn "WindowInsets\|enableEdgeToEdge\|systemBars" --include="*.kt"
grep -rn "WindowSizeClass\|calculateWindowSizeClass" --include="*.kt"
grep -rn "isSystemInDarkTheme\|Color(0xFF" --include="*.kt" | head -30
```

## Checklist

**U1 — Content descriptions.** Every meaningful `Icon`/`Image` needs one; decorative ones should be
explicitly `null` (not `""`). Interactive icon-only buttons without labels are unusable with TalkBack.
*High — this is an exclusion, not a polish item.*

**U2 — Touch targets.** Minimum 48dp. Icon buttons sized to the icon (24dp) are a common miss. Check
`.size(24.dp)` on clickables.

**U3 — Text scaling.** Text sized in `sp`, layouts that survive 200% font scale without clipping. Fixed-
height containers around text are the usual failure. Test with `adb shell settings put system font_scale 2.0`.

**U4 — Contrast.** Custom colors meeting 4.5:1 for body text. Hardcoded hex colors outside the theme are
both a contrast and a theming risk.

**U5 — Semantics.** Custom composables with `Modifier.clickable` on a `Box` need `role`, `onClickLabel`,
and often `mergeDescendants`. State (selected, checked, disabled, error) should be in semantics, not only
visual.

**U6 — Edge-to-edge and insets.** Required behavior on recent Android versions. Content must not sit under
system bars; keyboard (IME) insets handled. Google's `edge-to-edge` skill is authoritative here — use it.

**U7 — Adaptive layout.** `WindowSizeClass` used rather than hardcoded phone assumptions; behavior on
tablets, foldables, and landscape. Check nothing is locked to portrait without a reason.

**U8 — Theming.** Material 3 theme applied consistently; dark theme actually correct (not just inverted);
dynamic color supported or deliberately declined; no hardcoded colors bypassing the theme.

**U9 — Loading / empty / error states.** Every screen that loads data needs all three designed. Missing
error states usually mean the user sees an infinite spinner on failure. Grep for screens with a loading
branch but no error branch.

**U10 — Localization.** No hardcoded user-facing strings in Kotlin; plurals via `pluralStringResource`;
RTL support (`start`/`end` rather than `left`/`right`).

**U11 — Predictive back.** Supported and not broken by custom back handling.

## Verification

`./gradlew lintDebug` catches a good portion of U1/U2/U10. The Accessibility Scanner app and TalkBack on a
real device catch the rest. Say which method produced each finding.
