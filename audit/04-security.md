# 04 — Security & Privacy (Audit Phase 4)

**Date:** 2026-09-04 · **Branch:** `claude-audit` @ `be0f508` · **Scope:** whole module (`:app`)

Method: static review only. No device was attached, so nothing here was reproduced at runtime;
runtime-behaviour claims are marked **unverified** with the measurement that would confirm them.
`android docs` was unavailable (knowledge-base download failed), so platform-version claims come from
prior knowledge, not from a fetched doc.

Files read in full: `AndroidManifest.xml`, the release **merged** manifest, `res/xml/backup_rules.xml`,
`res/xml/data_extraction_rules.xml`, `MainActivity.kt`, `receiver/AppSchedulerReceiver.kt`,
`service/AppLaunchService.kt`, `service/RescheduleService.kt`, `data/wapper/AlarmManagerWrapper.kt`,
`data/repositoryImpl/*`, `data/local/*`, `domain/usecase/*`, `domain/utils/SdkUtils.kt`,
`ui/utils/ContextExt.kt`, `ui/screens/schedule/SchedulerScreen.kt` (permission block),
`utils/AppConstant.kt`, `app/build.gradle.kts`.

---

## Attack surface

Merged release manifest, all components:

| Component | exported | permission |
|---|---|---|
| `MainActivity` | true (LAUNCHER only) | — |
| `AppSchedulerReceiver` | **true** | **none** |
| `AppLaunchService` | false | — |
| `RescheduleService` | false | — |
| `androidx.room.MultiInstanceInvalidationService` | false | — |
| `androidx.profileinstaller.ProfileInstallReceiver` | true | `android.permission.DUMP` (fine) |
| `androidx.startup.InitializationProvider` | false | — |

`MainActivity` never calls `getIntent()` and has no `onNewIntent` — no activity-side intent handling
to attack. The only unprotected entry point is `AppSchedulerReceiver`.

No permission creep from dependencies: the merged manifest adds only
`com.peal.appscheduler.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AGP-generated, signature-level).

---

## Findings

### SEC-1 — Exported receiver dispatches an internal action any app can send · **High**

`app/src/main/AndroidManifest.xml:36-42` — `AppSchedulerReceiver` is `exported="true"` with no
`android:permission`.
`app/src/main/java/com/peal/appscheduler/receiver/AppSchedulerReceiver.kt:28-41` — besides
`ACTION_BOOT_COMPLETED` it handles `"com.peal.ACTION_SCHEDULE_APP"`, an app-private action that is
**not** in the `<intent-filter>` and is **not** a protected broadcast.

`exported="true"` means any app can deliver an *explicit* broadcast to the component regardless of the
declared filter:

```kotlin
sendBroadcast(Intent().apply {
    component = ComponentName("com.peal.appscheduler",
                              "com.peal.appscheduler.receiver.AppSchedulerReceiver")
    action = "com.peal.ACTION_SCHEDULE_APP"
    putExtra("PACKAGE_NAME", "<any package>")
    putExtra("SCHEDULE_ID", <any row id>)
})
```

Both extras are read with zero validation (`:29-30`) and forwarded straight into
`AppLaunchService` (`:32-41`), which:
- launches the named package's launcher activity from the background
  (`AppLaunchService.kt:98-108`, `getLaunchIntentForPackage` + `FLAG_ACTIVITY_NEW_TASK`), and
- writes `EXECUTED` / `FAILED` onto the schedule row with that id
  (`AppLaunchService.kt:86-91` → `ScheduleRepositoryImpl.updateScheduleStatus`, an unbounded
  `UPDATE schedules SET status=? WHERE id=?`).

Impact: a zero-permission third-party app gets (a) an on-demand foreground-activity launch of any
installed app — a background-activity-launch proxy it could not perform itself, useful for
foreground-hijack/phishing timing — and (b) write access to arbitrary rows of the schedule DB,
letting it silently mark the user's pending schedules as executed/failed.

Mitigating: not full intent redirection. The launched intent is built by `getLaunchIntentForPackage`,
so the attacker chooses the *package* but not the *component*, and cannot reach non-exported
activities or attach URI-permission grants. The `ACTION_BOOT_COMPLETED` branch is also not
spoofable — the platform rejects `sendBroadcast` of a protected action from a non-system UID even
with an explicit component.

`exported="true"` is genuinely required for the BOOT_COMPLETED filter, so the fix is to move the
private action to a second, `exported="false"` receiver (per `android-intent-security`: "MUST
explicitly set `android:exported="false"` for all components that don't need external communication";
"MUST rely on the system's Protected Broadcast mechanism for system events").

### SEC-2 — `RescheduleService` declares no `foregroundServiceType` but calls `startForeground()` · **Critical** (unverified)

`app/src/main/AndroidManifest.xml:48-49` — `<service android:name=".service.RescheduleService"
android:exported="false" />`, no `android:foregroundServiceType`.
`app/src/main/java/com/peal/appscheduler/service/RescheduleService.kt:44,52-62` — `onCreate()` calls
`startForegroundService()` which calls `startForeground(notificationId, notification)`.

`targetSdk = 36` (`app/build.gradle.kts:18`). For apps targeting API 34+, `Service.startForeground()`
on a service with no declared `foregroundServiceType` throws `MissingForegroundServiceTypeException`.
The service is started unconditionally from every `BOOT_COMPLETED`
(`AppSchedulerReceiver.kt:20-25`), i.e. this would crash on **every device boot** on Android 14+, and
no pending schedule would ever be re-armed after a reboot — the app's headline feature.

Note the project's own baseline build is clean: lint reported 0 errors / 39 warnings and did **not**
raise `ForegroundServiceType` (see `audit/00-baseline.md`), so this was not caught by tooling.

**Unverified.** Confirming measurement: install on an API 34+ emulator, `adb reboot`, then
`adb logcat | grep -i foregroundservice` and look for
`MissingForegroundServiceTypeException` / `AndroidRuntime` crash from `RescheduleService`.

### SEC-3 — `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` used with no qualifying exemption · **High**

`app/src/main/AndroidManifest.xml:9` (permission) and `:44-46`
(`AppLaunchService`, `android:foregroundServiceType="systemExempted"`).

`systemExempted` is the escape-hatch FGS type reserved for apps the platform already exempts —
device owner/profile owner, VPN apps, emergency/safety role holders and similar system integrations.
Nothing in this codebase puts the app in any of those categories: `AppLaunchService` does one thing,
`getLaunchIntentForPackage` + `startActivity` (`AppLaunchService.kt:96-118`). Two consequences:

1. **Play policy.** FGS types must be declared in Play Console, and `systemExempted` is a
   high-scrutiny declaration that is only approved for the exempted categories. As written this app
   should expect rejection. The honest types for this workload are `shortService` (the work is a
   sub-minute one-shot) or `specialUse` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification.
2. **Runtime.** On Android 14+ a non-exempt app starting a `systemExempted` FGS risks
   `ForegroundServiceStartNotAllowedException`, which would break every scheduled launch.

**Unverified (runtime half).** Confirming measurement: fire a schedule on an API 34+ device and check
logcat for `ForegroundServiceStartNotAllowedException` from `AppLaunchService`. The Play-policy half
is not a runtime question — it stands on the manifest alone.

### SEC-4 — `SYSTEM_ALERT_WINDOW` is demanded from the user but never used to draw anything · **High**

`app/src/main/AndroidManifest.xml:7` declares the permission.
`MainActivity.kt:53-66` shows a modal `CommonAlertDialog` on **every** `ON_RESUME` while
`Settings.canDrawOverlays(context)` is false; `MainActivity.kt:99-112` sends the user to
`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.

Grepping the whole of `app/src/` for `WindowManager`, `TYPE_APPLICATION_OVERLAY` and
`addView` returns nothing outside those two `MainActivity` sites. **The app never draws an overlay.**
The permission is being acquired for its side effect: holding `SYSTEM_ALERT_WINDOW` is one of the
documented exemptions from background-activity-launch restrictions, and that is what makes
`AppLaunchService.startActivity()` (`AppLaunchService.kt:107-108`) work from the background.

Impact: `SYSTEM_ALERT_WINDOW` is a Play-restricted permission reviewed against declared overlay
functionality. An app that requests it, nags for it on every resume, and never draws an overlay is
the exact pattern policy review flags. It is also a heavier grant than the feature needs — overlay
permission lets the app draw over any other app, a capability the user is being asked for under a
"needs permission to display over other apps" string that does not describe what it is used for
(`R.string.this_app_needs_permission_to_display_over_other_apps`).

The supported route for "wake the user at time T and show something" is a notification with
`setFullScreenIntent` (`USE_FULL_SCREEN_INTENT`), or a user-tappable notification, rather than an
unattended background activity launch.

### SEC-5 — `cancelSchedule` cancels the wrong PendingIntent; a cancelled schedule still launches the app · **High**

`AlarmManagerWrapper.kt:32-43` builds the alarm's intent **with**
`action = "com.peal.ACTION_SCHEDULE_APP"`.
`AlarmManagerWrapper.kt:52-62` builds the cancellation intent **without** any action.

`PendingIntent` matching uses `Intent.filterEquals`, which compares action/data/type/component/
categories (extras are ignored). Action `"com.peal.ACTION_SCHEDULE_APP"` vs `null` → not equal →
`PendingIntent.getBroadcast(...)` at `:57` returns a *different* PendingIntent, and
`alarmManager.cancel(pendingIntent)` at `:62` cancels that new one. The real alarm survives, and the
method returns `Result.success(Unit)` (`:63`).

Downstream, `CancelScheduledAppUseCase.kt:29-37` treats that success as truth and writes
`CANCELLED` to the DB. So: the user cancels a schedule, the UI says cancelled, the row says
cancelled — and at the scheduled time the app is launched anyway, and
`AppLaunchService.updateScheduleStatus` overwrites the row back to `EXECUTED`.

(The request-code difference — `scheduleId.hashCode()` at `:39` vs `scheduleId.toInt()` at `:58` —
is *not* the cause; `Long.hashCode()` equals `toInt()` for row ids below 2^31. The action is.)

Impact: an app the user explicitly told the scheduler not to launch is launched, in the foreground,
unattended. That is a consent/privacy failure as much as a correctness bug. `updateSchedule`
(`:69-88`) is unaffected — it re-arms with the same action + request code, so `FLAG_UPDATE_CURRENT`
replaces the alarm correctly.

**Unverified** (static reasoning about `filterEquals`). Confirming measurement: schedule an app,
cancel it, `adb shell dumpsys alarm | grep com.peal.appscheduler` and check whether the alarm is
still listed.

### SEC-6 — Cloud backup enabled with template (empty) rules; schedule DB leaves the device · **Medium**

`app/src/main/AndroidManifest.xml:16-18` — `allowBackup="true"`,
`dataExtractionRules="@xml/data_extraction_rules"`, `fullBackupContent="@xml/backup_rules"`.

Both XML files are the unmodified Studio templates with every rule commented out:
`res/xml/backup_rules.xml` has an empty `<full-backup-content>`, `res/xml/data_extraction_rules.xml`
has an empty `<cloud-backup>` and no `<device-transfer>` block. Empty rule sets mean *default*
behaviour — back up everything — not *nothing*.

What gets backed up: the Room DB `app_scheduler_db` (`AppConstant.kt:8`), table `schedules`
(`ScheduleEntity.kt:11-18`) = `packageName`, `appName`, `scheduledTime`, `status`.

Impact: a per-user inventory of selected installed apps plus the times the user arranges to use them
is uploaded to Google cloud backup (and included in device-to-device transfer). That is behavioural
data, and installed-app lists are treated as sensitive under Play's data-safety rules — it needs to
be either excluded from backup or disclosed in the Data Safety form. Low blast radius (no
credentials, no tokens — there is no auth or network in this app at all), hence Medium.

Secondary: `tools:targetApi="31"` on `<application>` (`:24`) suppresses the lint reminder that
`fullBackupContent` only applies to API 23-30 and `dataExtractionRules` to 31+; both being empty
templates means neither range is actually configured.

### SEC-7 — POST_NOTIFICATIONS requested with no rationale and no result handling · **Medium**

`MainActivity.kt:44` calls `requestNotificationPermission()` from `onCreate`, before `setContent`.
`MainActivity.kt:77-89` fires `ActivityCompat.requestPermissions(...)` immediately on API 33+.
`MainActivity` does **not** override `onRequestPermissionsResult` (confirmed — the class body ends at
`:118`), and `NOTIFICATION_PERMISSION_REQUEST_CODE` (`:116`) is therefore never consumed.

Impact: (1) the system dialog appears on first launch with no context, which is the pattern that
maximises denial; (2) denial is invisible to the app — there is no rationale path and no degraded
mode. Since both services are foreground services whose only user-facing output is their
notification (`AppLaunchService.kt:129-136`, `RescheduleService.kt:52-62`), a denied permission means
every "Launch failed" / "app is not installed" message is silently dropped and the user sees a
scheduled launch simply not happen with no explanation. Compare the overlay and exact-alarm
permissions, which do get rationale dialogs — notifications is the odd one out.

Also note `ActivityCompat.requestPermissions` is the legacy path; the rest of the file already uses
`rememberLauncherForActivityResult` (`:93`), so this is inconsistent with the file's own pattern.

### SEC-8 — Exact-alarm permission gate uses API 33 where the platform gate is API 31 · **Medium**

`ui/screens/schedule/SchedulerScreen.kt:119`:
`if (isAndroidTIRAMISUOrLater() && !alarmManager.canScheduleExactAlarms())` — `isAndroidTIRAMISUOrLater()`
is `SDK_INT >= 33` (`domain/utils/SdkUtils.kt:11-12`).

`canScheduleExactAlarms()` exists from API 31, and `AlarmManagerWrapper.kt:26-30` correctly gates on
`>= Build.VERSION_CODES.S` (31) and throws `SecurityException` when it returns false. On Android
12/12L (API 31-32) `SCHEDULE_EXACT_ALARM` is granted by default but the user can revoke it in
Settings; in that state the UI never shows the rationale dialog, the user schedules something, and
`AlarmManagerWrapper` swallows the resulting `SecurityException` into `Result.failure` (`:45-47`).
`ScheduleAppUseCase.kt:38-43` ignores the `scheduleApp` return value entirely — the row is written to
Room and the UI reports success. The alarm was never set.

Impact: on API 31-32 with the permission revoked, schedules are silently accepted and never fire.
`ui/utils/ContextExt.kt:17-28` (`openScheduleExactAlarmPermissionSettings`) already uses the correct
`>= S` check, so the two call sites disagree with each other.

Also worth flagging for release: `SCHEDULE_EXACT_ALARM` is itself a Play-restricted permission
requiring a declaration that the app's core function needs exact timing. That is arguably true here,
but it is a declaration the developer must file, not a free permission.

### SEC-9 — Package names and schedule ids logged, and logs survive into release · **Low**

`AppLaunchService.kt:56` (`"Starting app: $packageName, scheduleId: $scheduleId"`), `:74`, `:100`,
`:169`; `RescheduleService.kt:99` (`"Rescheduled app launch for package: ... at ..."`), `:104`.

`app/build.gradle.kts:26-32` sets `isMinifyEnabled = false` for release, so R8 never runs and no
`assumenosideeffects` stripping happens — these `Log.d`/`Log.e` calls ship in the release APK.

Impact: limited. The data is which apps the user schedules and when — behavioural, not credential —
and since Android 4.1 an app cannot read another app's logcat, so exposure requires ADB or a
privileged/rooted context. Worth cleaning up alongside enabling R8 (see the build phase), not on its
own.

---

## Checked and clean

Stating these explicitly so they are not re-audited:

- **S3 Secrets.** `grep` for `API_KEY|SECRET|PASSWORD|TOKEN|Bearer|apiKey` across `app/`, `gradle/`,
  `*.properties`, `*.kts`: **zero hits**. No `BuildConfig` fields, no keystore in the repo.
- **S6 Network.** The app has no networking dependency at all (`app/build.gradle.kts:48-91`), makes no
  HTTP calls, and declares no `INTERNET` permission. No `usesCleartextTraffic`, no
  `networkSecurityConfig`, no custom `TrustManager`/`HostnameVerifier`. Cleartext is moot.
- **S7 WebView.** No `WebView` anywhere. No `javaScriptEnabled` / `addJavascriptInterface`.
- **S8 PendingIntent mutability.** Both `PendingIntent.getBroadcast` calls
  (`AlarmManagerWrapper.kt:38-41`, `:57-60`) use `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, with an
  explicit component (`Intent(context, AppSchedulerReceiver::class.java)`). Correct.
- **S5 Storage.** No `getSharedPreferences`, no `MODE_WORLD_*`, no external-storage writes. Room DB
  lives in app-private storage. Nothing stored is credential-grade.
- **Intent redirection.** No component reads a nested `Intent` extra and re-dispatches it. The only
  intent built from untrusted input is `getLaunchIntentForPackage(packageName)`, which the system
  constructs — the caller controls the package, never the component or the flags.
- **`<queries>` / package visibility.** `AndroidManifest.xml:53-58` uses an
  `<intent>` MAIN/LAUNCHER filter, **not** `QUERY_ALL_PACKAGES`. This is the policy-correct
  narrow declaration: Play's package-visibility policy restricts `QUERY_ALL_PACKAGES` and explicitly
  points developers at `<queries>` intent filters instead. `DeviceAppsRepositoryImpl.kt:24-40` uses
  `queryIntentActivities` against exactly that filter, and `AppLaunchService.isAppInstalled`
  (`:120-127`) is consistent with it. **No finding here** — this part is right.
- **S9 Permission creep.** Release merged manifest adds no third-party permissions.
- **S10 Third-party SDKs.** No analytics, no crash reporter, no ads. Nothing collects data off-device.
- **S11 Trusted-client assumptions.** No entitlements, no auth, no server. N/A.
- **Debuggable.** No `android:debuggable` in the manifest or `build.gradle.kts`.

## Not determined

- Whether SEC-2 / SEC-3 actually throw at runtime (no device attached; measurements given above).
- The Play Console FGS-type and restricted-permission declarations — out of repo.
- Data Safety form contents vs SEC-6 — out of repo.
