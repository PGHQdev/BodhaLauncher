# Delimiting a "phone session" (unlock → lock) across Android versions and OEMs

- Date: 2026-08-04
- Question: What mechanisms reliably delimit a phone session (unlock → lock) for a launcher app (minSdk 29), across Android versions and OEMs?
- Method: primary sources only — developer.android.com reference/guides, AOSP source (android.googlesource.com), dontkillmyapp.com as an OEM-behavior catalog. Quotes are verbatim from the cited page/file.

## 1. Broadcasts: ACTION_USER_PRESENT, ACTION_SCREEN_ON/OFF

### Registration rules

- `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` have never been manifest-registrable, on any API level: "You cannot receive this through components declared in manifests, only by explicitly registering for it with `Context.registerReceiver()`. This is a protected intent that can only be sent by the system." ([Intent#ACTION_SCREEN_ON](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON), [#ACTION_SCREEN_OFF](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_OFF))
- `ACTION_USER_PRESENT` is an implicit broadcast and is **not** on the implicit-broadcast exemption list, so apps targeting API 26+ cannot receive it via a manifest receiver; it must be context-registered. ([Implicit broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions) — the list contains BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, USER_INITIALIZE, TIME_SET, LOCALE_CHANGED, USB/Bluetooth/telephony/SMS/media actions, etc.; neither USER_PRESENT nor SCREEN_ON/OFF appear.)
- Context-registered receivers "receive broadcasts as long as their registering context is valid" — for an application-context registration, as long as the process runs. ([Broadcasts overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts))

### Meaning / delivery semantics

- SCREEN_ON/OFF track the **interactive** state, not the panel: "For historical reasons, the name of this broadcast action refers to the power state of the screen but it is actually sent in response to changes in the overall interactive state of the device. … To determine the actual state of the screen, use `Display.getState()`. See `PowerManager.isInteractive()`." ([Intent#ACTION_SCREEN_ON](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON))
- `ACTION_USER_PRESENT`: "Sent when the user is present after device wakes up (e.g when the keyguard is gone)." ([Intent#ACTION_USER_PRESENT](https://developer.android.com/reference/android/content/Intent#ACTION_USER_PRESENT))
- Delivery timing is best-effort: "delivery times of broadcasts are not guaranteed", and since Android 14 the system "can place context-registered broadcasts in a queue while the app is in the cached state"; the guide names `ACTION_SCREEN_ON` as a broadcast that is deferred for cached apps and delivered when the app leaves the cached state, with "multiple instances of certain broadcasts … merged into one broadcast". ([Broadcasts overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts), [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all))
- AOSP sends USER_PRESENT with `FLAG_RECEIVER_REPLACE_PENDING` plus `BroadcastOptions.setDeferralPolicy(DEFERRAL_POLICY_UNTIL_ACTIVE)` and `DELIVERY_GROUP_POLICY_MOST_RECENT` — i.e. it is coalesced and deferred for non-active processes by design, and it is sent per user, to the current user and each of its profiles via `sendBroadcastAsUser`. ([KeyguardViewMediator.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java), `USER_PRESENT_INTENT`/`USER_PRESENT_INTENT_OPTIONS` and `sendUserPresentBroadcast()`)

### When USER_PRESENT actually fires (AOSP source)

From [KeyguardViewMediator.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java):

- Normal case: sent from `handleKeyguardDone()` — i.e. when the keyguard is dismissed (after PIN/biometric, or after the swipe on a swipe keyguard).
- Lock screen set to **None**: keyguard never shows; `maybeSendUserPresentBroadcast()` is called from `onStartedWakingUp()` and sends USER_PRESENT on **every wake**: "Lock screen is disabled because the user has set the preference to 'None'. In this case, send out ACTION_USER_PRESENT here instead of in handleKeyguardDone()".
- So USER_PRESENT fires in all three security configurations (secure, swipe, none) — but with "None" it degrades to a synonym of SCREEN_ON, and with AOD "peek then pocket" wakes it may fire without a deliberate session (see §6).

### AOD

SCREEN_ON/OFF follow interactivity, so always-on display does not generate them: AOD is a non-interactive doze state (display in `STATE_DOZE`, `isInteractive() == false`, see §5), and the broadcasts fire only on interactive-state transitions ([Intent#ACTION_SCREEN_ON](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON), [PowerManager.isInteractive](https://developer.android.com/reference/android/os/PowerManager#isInteractive())).

## 2. KeyguardManager

All from [KeyguardManager reference](https://developer.android.com/reference/android/app/KeyguardManager). All are synchronous polling calls; only the API 33 listener pushes changes.

- `isKeyguardLocked()` (API 16): "Returns whether the lock screen (also known as Keyguard) is showing", including when occluded by an activity (incoming call) or temporarily disabled by lock-task mode. Critically: "'Showing' refers to a logical state of the UI, regardless of whether the screen happens to be on. When the power button is pressed on an unlocked device, the lock screen starts 'showing' immediately when the screen turns off." It "does not distinguish a lock screen that is requiring authentication … from a lock screen that is trivially" dismissible (swipe). With lock screen set to None it returns false because no keyguard exists.
- `isDeviceLocked()` (API 22): "the device is considered to be locked for a user when the user's apps are currently inaccessible and some form of lock screen authentication is required to regain access to them. … 'Swipe' does not count as authentication; if the lock screen is dismissible with swipe, for example due to the lock screen being set to Swipe or due to the device being kept unlocked by being near a trusted bluetooth device or in a trusted location, the device is considered unlocked." This is the CE-storage-relevant notion of locked (Smart Lock/trust agents included). Per-user: "The device-locked state may differ between different users", and for a profile "the device is considered to be locked as long as any challenge remains".
- `isDeviceSecure()` (API 23): "whether the user has a secure lock screen" (PIN/pattern/password vs swipe or none); config, not current state. `isKeyguardSecure()` (API 16) is the same plus locked SIM cards.
- `addKeyguardLockedStateListener(Executor, KeyguardLockedStateListener)` (API 33): "Registers a listener to execute when the keyguard locked state changes. Requires `Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE`" — a normal install-time permission; the listener mirrors `isKeyguardLocked()`. (An `addDeviceLockedStateListener` mirroring `isDeviceLocked()` exists on newer SDKs, above this project's floor.)

Consequences for session logic:

- `isKeyguardLocked()` flips to true at screen-off, so polling it at SCREEN_OFF always says "locked" — it cannot distinguish "user locked" from "screen timed out on an unlocked device"; both end the session anyway.
- `isDeviceLocked()` is false for swipe-keyguard and Smart Lock states even while the keyguard is showing, so it under-reports "locked" for non-secure users; `isKeyguardLocked()` is the right cross-check for "is a keyguard between the user and the launcher".
- Direct boot: `isDeviceLocked()` corresponds to whether credential-encrypted (CE) storage requires auth; before first unlock after boot the user is locked and only device-protected storage is safely accessible ([Direct Boot guide](https://developer.android.com/privacy-and-security/direct-boot)).

## 3. UsageStatsManager events

From [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event) and [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager):

- `SCREEN_INTERACTIVE` / `SCREEN_NON_INTERACTIVE` (API 28): "the screen has gone in to an interactive state (turned on for full user interaction, not ambient display or other non-interactive state)" / "a non-interactive state (completely turned off or turned on only in a non-interactive state like ambient display)". AOD is explicitly on the non-interactive side.
- `KEYGUARD_SHOWN` (API 28): "the screen's keyguard has been shown, whether or not the screen is off." `KEYGUARD_HIDDEN` (API 28): "the screen's keyguard has been hidden. This typically happens when the user unlocks their phone after turning it on." KEYGUARD_HIDDEN is the event-log analogue of USER_PRESENT.
- `DEVICE_SHUTDOWN`: timestamp "is actually the last time UsageStats database is persisted before the actual shutdown. Events (if there are any) between this timestamp and the actual shutdown is not persisted" — reboots can truncate the tail of a session in the log.
- Permission: "Most methods on this API require the permission `android.permission.PACKAGE_USAGE_STATS`. However, declaring the permission implies intention to use the API and the user of the device still needs to grant permission through the Settings application. See `Settings.ACTION_USAGE_ACCESS_SETTINGS`." — i.e. special "Usage access" toggle, not a runtime dialog.
- `queryEvents(begin, end)`: "Events are only kept by the system for a few days" (backfill window is short), and "Starting from Android R, if the user's device is not in an unlocked state (as defined by `UserManager.isUserUnlocked()`), then null will be returned" — you cannot query before first unlock after boot.
- It is a pull API over a persisted log: there is no push/latency guarantee documented; it is suited to retrospective reconstruction, not live detection.

## 4. ProcessLifecycleOwner / app visibility

[ProcessLifecycleOwner](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner): "Class that provides lifecycle for the whole application process. … dispatch `ON_START`, `ON_RESUME` events, as a first activity moves through these events. `ON_PAUSE`, `ON_STOP` … dispatched with a delay after a last activity passed through them" (the delay exists to swallow configuration changes); intended "for use cases where you would like to react on your app coming to the foreground or going to the background".

For a HOME app this signals "a launcher activity is started/resumed", which happens whenever the user presses Home, whenever an app above the launcher finishes — and on some devices the launcher activity is resumed **behind the keyguard** when the screen turns on. It says nothing about the keyguard: `isKeyguardLocked()` can be true while the launcher is ON_RESUME. It is a "launcher visible" signal, usable as a corroborating heartbeat inside a session, never as the session boundary.

## 5. PowerManager.isInteractive / Display state (AOD relation)

- [PowerManager.isInteractive()](https://developer.android.com/reference/android/os/PowerManager#isInteractive()): true = "awake and ready to interact with the user"; "the device is still considered to be interactive while dreaming … but not when it is dozing or asleep. … Certain features, such as 'ambient mode' may cause the main screen to remain on (albeit in a low power state) to display system-provided content while the device dozes. The system will send a screen on or screen off broadcast whenever the interactive state of the device changes."
- [Display.getState()](https://developer.android.com/reference/android/view/Display#getState()) returns `STATE_OFF/ON/DOZE/DOZE_SUSPEND/ON_SUSPEND/UNKNOWN`. `STATE_DOZE`: "The display is dozing in a low power state; it is still on but is optimized for showing system-provided content while the device is non-interactive" — this is AOD: display on, device non-interactive, SCREEN_OFF already fired.

So for session purposes "screen off" must mean **non-interactive**, not display-off; SCREEN_ON/OFF and SCREEN_INTERACTIVE/NON_INTERACTIVE already use that definition, and AOD needs no special-casing under it.

## 6. Failure modes

| Failure mode | What happens | Evidence / source |
|---|---|---|
| AOD / ambient display | Display stays in `STATE_DOZE` while device is non-interactive; SCREEN_OFF has fired; no spurious SCREEN_ON from AOD itself | [PowerManager.isInteractive](https://developer.android.com/reference/android/os/PowerManager#isInteractive()), [Display.STATE_DOZE](https://developer.android.com/reference/android/view/Display#STATE_DOZE), [UsageEvents SCREEN_NON_INTERACTIVE](https://developer.android.com/reference/android/app/usage/UsageEvents.Event#SCREEN_NON_INTERACTIVE) |
| Lock-screen peek (wake to check notifications, no unlock) | SCREEN_ON fires, keyguard shows, no USER_PRESENT / KEYGUARD_HIDDEN; must not count as a session | USER_PRESENT sent only on keyguard dismissal ([KeyguardViewMediator.handleKeyguardDone](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java)) |
| Lock screen set to None | No keyguard ever shows; USER_PRESENT fires on every wake (`maybeSendUserPresentBroadcast` in `onStartedWakingUp`); `isKeyguardLocked()` always false; every wake is a session | [KeyguardViewMediator.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java) |
| Swipe keyguard / Smart Lock | `isDeviceLocked()` false even while keyguard shows ("'Swipe' does not count as authentication"); USER_PRESENT still fires on swipe-dismiss | [KeyguardManager.isDeviceLocked](https://developer.android.com/reference/android/app/KeyguardManager#isDeviceLocked()) |
| `isKeyguardLocked()` true at screen-off on unlocked device | Keyguard "showing" starts at power-button press, before any lock; polling at SCREEN_OFF cannot tell timeout-lock from explicit lock | [KeyguardManager.isKeyguardLocked](https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked()) |
| Process death kills context-registered receivers | Receiver lives only "as long as their registering context is valid"; the HOME process is favored (`HOME_APP_ADJ`: "we want to try avoiding killing it, even if it would normally be in the background") but not immune, and OEMs (Huawei, Xiaomi, OnePlus, Samsung, …) kill beyond AOSP policy | [Broadcasts overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts), [ProcessList.java HOME_APP_ADJ](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ProcessList.java), [dontkillmyapp.com](https://dontkillmyapp.com/) |
| Cached-state broadcast queuing (Android 14+) | Context-registered broadcasts (SCREEN_ON named explicitly) queued while cached, coalesced, delivered late; USER_PRESENT is sent with `DEFERRAL_POLICY_UNTIL_ACTIVE` + `DELIVERY_GROUP_POLICY_MOST_RECENT`, so intermediate occurrences can be merged away | [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all), [Broadcasts overview](https://developer.android.com/develop/background-work/background-tasks/broadcasts), [KeyguardViewMediator.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java) |
| Direct boot (before first unlock) | Only device-protected storage accessible; `ACTION_LOCKED_BOOT_COMPLETED` needs `directBootAware` receiver + `RECEIVE_BOOT_COMPLETED`; unlock signaled by `ACTION_USER_UNLOCKED` ("only sent to registered receivers, not manifest receivers") then `ACTION_BOOT_COMPLETED`; `queryEvents` returns null while user locked (Android R+) | [Direct Boot guide](https://developer.android.com/privacy-and-security/direct-boot), [Intent#ACTION_LOCKED_BOOT_COMPLETED](https://developer.android.com/reference/android/content/Intent#ACTION_LOCKED_BOOT_COMPLETED), [Intent#ACTION_USER_UNLOCKED](https://developer.android.com/reference/android/content/Intent#ACTION_USER_UNLOCKED), [UsageStatsManager.queryEvents](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)) |
| Reboot mid-session | `DEVICE_SHUTDOWN` usage event timestamp is the last DB persist; trailing events lost | [UsageEvents.Event.DEVICE_SHUTDOWN](https://developer.android.com/reference/android/app/usage/UsageEvents.Event#DEVICE_SHUTDOWN) |
| Multi-user / work profile | USER_PRESENT is sent per user (current user + its profiles); KeyguardManager methods answer for the Context's user; profile lock state can require up to two challenges | [KeyguardViewMediator.sendUserPresentBroadcast](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java), [KeyguardManager.isDeviceLocked](https://developer.android.com/reference/android/app/KeyguardManager#isDeviceLocked()) |
| Doze / app standby | Doze implies the device is asleep, so no interactive transitions occur during it; the risk is deferred delivery when waking while the process is cached (row above), plus OEM-added restrictions | [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all), [dontkillmyapp.com](https://dontkillmyapp.com/) |

## 7. Recommendation for BodhaLauncher (target API 29+)

Live detection (primary):

1. Register `ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT` with `Context.registerReceiver()` on the application context in the launcher process. Manifest registration is impossible for SCREEN_ON/OFF and blocked for USER_PRESENT ([Intent docs](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_ON), [broadcast exceptions](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)). The HOME process is long-lived by OOM policy ([HOME_APP_ADJ](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ProcessList.java)), which makes this viable for a launcher where it is not for a normal app.
2. Session start = `USER_PRESENT` received, cross-checked at receipt with `KeyguardManager.isKeyguardLocked() == false` (guards against stale/coalesced delivery, [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked())). On devices with lock screen None, USER_PRESENT arrives at every wake ([KeyguardViewMediator](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java)) — accept that as the session start there; there is no stronger signal on such devices.
3. Session end = `SCREEN_OFF` (device non-interactive; covers AOD correctly by definition, [PowerManager.isInteractive](https://developer.android.com/reference/android/os/PowerManager#isInteractive())). A SCREEN_ON without subsequent USER_PRESENT is a peek, not a session.
4. On API 33+, additionally register `addKeyguardLockedStateListener` (install-time permission `SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE`) as a push-based cross-check of keyguard state ([KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager#addKeyguardLockedStateListener(java.util.concurrent.Executor,%20android.app.KeyguardManager.KeyguardLockedStateListener))).
5. On process start (launcher recreated after kill/reboot), re-register and reconstruct current state by polling `PowerManager.isInteractive()` + `KeyguardManager.isKeyguardLocked()` instead of assuming "session closed".

Retrospective repair (secondary, optional):

6. `UsageStatsManager.queryEvents` for `SCREEN_INTERACTIVE`/`SCREEN_NON_INTERACTIVE`/`KEYGUARD_HIDDEN`/`KEYGUARD_SHOWN` to backfill sessions missed during process death (OEM kills per [dontkillmyapp.com](https://dontkillmyapp.com/)). Costs: the special "Usage access" grant via Settings (`PACKAGE_USAGE_STATS`, no runtime dialog — real onboarding friction), events kept "only … for a few days", null before first unlock on Android R+, and shutdown-tail loss ([UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)), [UsageEvents.Event](https://developer.android.com/reference/android/app/usage/UsageEvents.Event)). Since BodhaLauncher likely wants usage access anyway (app-usage features), reusing the same grant for session repair is cheap; do not make it a prerequisite for basic session detection.

Do not use ProcessLifecycleOwner or launcher-activity visibility as the boundary — it fires behind the keyguard and on every Home press ([ProcessLifecycleOwner](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)); use it only as an in-session activity signal.

## Sources

- https://developer.android.com/reference/android/content/Intent (ACTION_SCREEN_ON/OFF, ACTION_USER_PRESENT, ACTION_USER_UNLOCKED, ACTION_LOCKED_BOOT_COMPLETED constant docs)
- https://developer.android.com/develop/background-work/background-tasks/broadcasts
- https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- https://developer.android.com/about/versions/14/behavior-changes-all
- https://developer.android.com/reference/android/app/KeyguardManager
- https://developer.android.com/reference/android/app/usage/UsageEvents.Event
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://developer.android.com/reference/android/os/PowerManager
- https://developer.android.com/reference/android/view/Display
- https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner
- https://developer.android.com/privacy-and-security/direct-boot
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/keyguard/KeyguardViewMediator.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/am/ProcessList.java
- https://dontkillmyapp.com/
