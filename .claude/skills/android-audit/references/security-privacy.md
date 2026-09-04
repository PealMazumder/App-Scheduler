# Phase 4 — Security & privacy

Goal: find things that leak user data or let another app do something it shouldn't. Google's official
`android-intent-security` skill is authoritative for intent handling — defer to it there.

## Fast signals

```bash
grep -rn "android:exported=\"true\"" app/src/main/AndroidManifest.xml
grep -rn "android:allowBackup\|android:debuggable\|usesCleartextTraffic" app/src/main/AndroidManifest.xml
grep -rn "API_KEY\|SECRET\|PASSWORD\|TOKEN\|Bearer " --include="*.kt" --include="*.xml" --include="*.properties" | grep -v Test
grep -rn "Log\.\(d\|v\|i\)" --include="*.kt" | wc -l          # then check what's being logged
grep -rn "getSharedPreferences\|MODE_WORLD" --include="*.kt"
grep -rn "javaScriptEnabled\|addJavascriptInterface\|setAllowFileAccess" --include="*.kt"
grep -rn "TrustManager\|HostnameVerifier\|setHostnameVerifier" --include="*.kt"
grep -rn "PendingIntent" --include="*.kt"                      # mutability flags
```

## Checklist

**S1 — Exported components.** Every `exported="true"` activity/service/receiver/provider is an entry point
from other apps. Each needs a permission or input validation. Implicit-intent-matching components are
exported by default on older manifest patterns. *Critical if it performs a privileged action.*

**S2 — Deep link validation.** Deep links carry attacker-controlled data. Check that IDs are validated,
that a deep link can't drop the user into an authenticated screen without an auth check, and that
`android:autoVerify` is set for App Links you claim to own.

**S3 — Secrets in the binary.** API keys in source, `BuildConfig`, or resources are extractable. A key in
`local.properties` piped into `BuildConfig` is still in the APK. Findings should say what the key grants —
an analytics write key is not the same risk as a payment key.

**S4 — PII in logs.** Tokens, emails, user IDs, locations in `Log.*`. Also check crash reporting and
analytics payloads. Release builds should strip logs via R8 rules.

**S5 — Storage of sensitive data.** Tokens in plain SharedPreferences vs EncryptedSharedPreferences /
Keystore. Sensitive files on external storage. `allowBackup="true"` means it syncs off-device.

**S6 — Network.** Cleartext traffic disabled; certificate pinning where warranted (and pinning that has a
backup pin + expiry plan — a pin without one is an outage waiting to happen); no custom `TrustManager`
that accepts everything.

**S7 — WebView.** JS enabled only when needed, `addJavascriptInterface` only with trusted content, file
access disabled, no loading of arbitrary URLs from intents.

**S8 — PendingIntent mutability.** Must be explicitly `FLAG_IMMUTABLE` or `FLAG_MUTABLE`. Mutable + implicit
is a known escalation path.

**S9 — Permissions.** Every declared permission justified; runtime permission rationale and denial paths
handled; no permission creep from a dependency (check the merged manifest, not just yours:
`app/build/outputs/logs/manifest-merger-*-report.txt`).

**S10 — Third-party SDKs.** What data do they collect? Does it match the Play Data Safety declaration?
Mismatch is a policy risk, not just an ethical one.

**S11 — Root/tamper assumptions.** Note whether the app assumes a trusted client for anything security-
relevant. Client-side-only validation of entitlements is a finding.

## Tone

Report security findings factually with the concrete attack path, not as alarm. "Any installed app can
send an intent to `ShareActivity` with an arbitrary file URI, which is then read and uploaded" is useful.
"Security vulnerability!" is not.
