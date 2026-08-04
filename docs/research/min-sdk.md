# Research: min SDK for Bodha

Resolves wayfinder ticket #30. Facts only; the decision itself lands as an ADR via `/domain-modeling`.

## 1. Market: Android version distribution

Snapshot: April 2026 data via [apilevels.com](https://apilevels.com/) (derived from Statcounter GlobalStats; Google no longer publishes a public distribution dashboard — its data surfaces only in Android Studio's new-project wizard). Page updated 2026-05-28.

| Min version | Cumulative device coverage |
|---|---|
| Android 8 (API 26)+ | 96.1% |
| Android 9 (API 28)+ | 93.5% |
| **Android 10 (API 29)+** | **91.1%** |
| Android 11 (API 30)+ | 86.9% |
| Android 12 (API 31)+ | 78.8% |
| Android 13 (API 33)+ | 68.9% |
| Android 14 (API 34)+ | 54.5% |

## 2. API levels of Bodha's core mechanisms

| Mechanism | API level | Notes | Source |
|---|---|---|---|
| HOME intent handling (`Intent.CATEGORY_HOME`) | 1 | Launcher via `MAIN`/`HOME`/`DEFAULT` intent-filter works on every version | [Intent#CATEGORY_HOME](https://developer.android.com/reference/android/content/Intent#CATEGORY_HOME) |
| `RoleManager` / `ROLE_HOME` | 29 | Clean "set as default launcher" prompt. Pre-29 fallback: fire a HOME intent (system chooser) or `Settings.ACTION_HOME_SETTINGS` (API 21+) | [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager), [ROLE_HOME](https://developer.android.com/reference/android/app/role/RoleManager#ROLE_HOME) |
| `UsageStatsManager` | 21 | Needs `PACKAGE_USAGE_STATS`, a special-access permission the user grants in Settings (`Settings.ACTION_USAGE_ACCESS_SETTINGS`); not a runtime permission | [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) |
| `NotificationListenerService` | 18 | User grants notification access in Settings | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| Package visibility | targetSdk 30+ behavior change | Apps targeting API 30+ see a filtered package list. A `<queries>` intent filter with `ACTION_MAIN` + `CATEGORY_LAUNCHER` exposes all launchable apps for the drawer — no broad permission needed | [Package visibility](https://developer.android.com/training/package-visibility), [Declaring](https://developer.android.com/training/package-visibility/declaring) |

**`QUERY_ALL_PACKAGES` on Play**: permitted only when core functionality requires broad visibility; Google's permitted-use list names device search, antivirus, file managers, browsers — launchers are not enumerated — and it requires the Permissions Declaration Form. The `<queries>` route avoids all of this. Source: [Play policy](https://support.google.com/googleplay/android-developer/answer/10158779).

## 3. minSdk of comparable launchers (from their build files)

| Launcher | minSdk | Source |
|---|---|---|
| KISS Launcher | 21 | [build.gradle](https://github.com/Neamar/KISS/blob/master/app/build.gradle) |
| Unlauncher | 21 | [build.gradle.kts](https://github.com/jkuester/unlauncher/blob/master/app/build.gradle.kts) |
| Olauncher | 24 | [build.gradle](https://github.com/tanujnotes/Olauncher/blob/master/app/build.gradle) |
| Lawnchair | 26 | [build.gradle](https://github.com/LawnchairLauncher/lawnchair/blob/15-dev/build.gradle) |
| mLauncher | 28 | [build.gradle.kts](https://github.com/DroidWorksStudio/mLauncher/blob/main/app/build.gradle.kts) |

## 4. What the decision turns on

- targetSdk is a separate Play requirement from minSdk: from 2026-08-31 new apps/updates must target API 36; existing apps must target 35+ to stay visible. Play does not constrain minSdk. Source: [target SDK requirements](https://developer.android.com/google/play/requirements/target-sdk).
- The only mechanism that pushes minSdk up is `RoleManager.createRequestRoleIntent(ROLE_HOME)` at API 29. Everything else Bodha needs is satisfied at API 21 or below.
- minSdk 29 → 91.1% coverage, no default-launcher fallback path. Dropping to 26 buys ~5 points of coverage at the cost of a second (worse-UX) set-default flow.
- The `<queries>` package-visibility declaration is required regardless of minSdk, because targetSdk will be 30+ either way.
