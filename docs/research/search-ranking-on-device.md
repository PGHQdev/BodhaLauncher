# On-device ranking and suggestion mechanisms for a universal search surface (API 29–36)

- Date: 2026-08-05
- Question: What ranking and suggestion mechanisms are available fully on-device for a universal search surface in an Android launcher (minSdk 29, targetSdk 36), and what do they cost?
- Method: primary sources only — developer.android.com reference/guides, AOSP source (android.googlesource.com), androidx source (android.googlesource.com/platform/frameworks/support), Google Play policy pages (support.google.com). Quotes are verbatim from the cited page/file. Cloud AI and telemetry are out of scope by product constraint; mechanisms requiring either are marked OUT rather than recommended.

## 1. Platform affordances

### 1.1 AppSearch

AppSearch is the only first-party full-text index Android ships for app use. From the [AppSearch guide](https://developer.android.com/guide/topics/search/appsearch):

> "AppSearch is a high-performance on-device search solution for managing locally stored, structured data. It contains APIs for indexing data and retrieving data using full-text search. Applications can use AppSearch to offer custom in-app search capabilities, allowing users to search for content even while offline."

Documented feature list, verbatim:

> "- A fast, mobile-first storage implementation with low I/O use
> - Highly efficient indexing and querying over large data sets
> - Multi-language support, such as English and Spanish
> - Relevance ranking and usage scoring"

**Storage backends and availability.** Three exist ([guide](https://developer.android.com/guide/topics/search/appsearch)):

- `LocalStorage` — "With `LocalStorage`, your application manages an app-specific index that lives in your application data directory." Available Android 5.0+. The androidx class carries no `@RequiresApi`: "An AppSearch storage system which stores data locally in the app's storage space using a bundled version of the search native library." ([LocalStorage.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch-local-storage/src/main/java/androidx/appsearch/localstorage/LocalStorage.java)) — **usable at minSdk 29 with no version gate.**
- `PlatformStorage` — "An AppSearch storage system which stores data in the central AppSearch service, available on Android S+", annotated `@RequiresApi(Build.VERSION_CODES.S)` ([PlatformStorage.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch-platform-storage/src/main/java/androidx/appsearch/platformstorage/PlatformStorage.java)) — i.e. API 31+, so it cannot be the only backend at minSdk 29.
- `PlayServicesStorage` — index "hosted in Google Play Service's storage" ([guide](https://developer.android.com/guide/topics/search/appsearch)). Requires GMS; a launcher that must work on non-GMS devices cannot depend on it.

**Index size limits.** Only the shared backends are capped: "With `PlatformStorage`, AppSearch limits the number of documents and size of documents an application can index to ensure an efficient central index. `PlayServicesStorage` also has the same limitations as `PlatformStorage`" ([guide](https://developer.android.com/guide/topics/search/appsearch)). The guide does not publish the numeric limits, and no numeric per-app limit is documented for `LocalStorage` — that index is bounded by app data directory space.

**Latency.** The only comparative claim published: "Due to lower I/O use, AppSearch offers lower latency for indexing and searching over large datasets compared to SQLite." For `PlatformStorage` there is an explicit added cost: "Because `PlatformStorage` wraps Jetpack APIs over the AppSearch system service, the APK size impact is minimal compared to using LocalStorage. However, this also means AppSearch operations incur additional binder latency when calling the AppSearch system service." ([guide](https://developer.android.com/guide/topics/search/appsearch)). No absolute millisecond figures are documented anywhere in primary sources.

`search()` itself is cheap; paging is where the work is: "This method is lightweight. The heavy work will be done in `SearchResults#getNextPageAsync`." ([AppSearchSession.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/AppSearchSession.java))

Concurrency, verbatim from [LocalStorage.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch-local-storage/src/main/java/androidx/appsearch/localstorage/LocalStorage.java): "Queries are executed multi-threaded, but a single thread is used for mutate requests (put, delete, etc..)." Writes serialise; reads do not.

**Persistence.** Not automatic per-write. `requestFlushAsync()`: "Flush all schema and document updates, additions, and deletes to disk if possible. The request is not guaranteed to be handled and may be ignored by some implementations." `close()`: "Closes the AppSearchSession to persist all schema and document updates, additions, and deletes to disk." ([AppSearchSession.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/AppSearchSession.java)) The guide adds: "Updates to a database should be periodically persisted to disk by calling `requestFlush()`". A launcher process killed without a flush can lose recent index writes.

**Built-in ranking strategies.** From [SearchSpec.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/SearchSpec.java), verbatim:

| Constant | Javadoc |
|---|---|
| `RANKING_STRATEGY_NONE` (0) | "No Ranking, results are returned in arbitrary order." |
| `RANKING_STRATEGY_DOCUMENT_SCORE` (1) | "Ranked by app-provided document scores." |
| `RANKING_STRATEGY_CREATION_TIMESTAMP` (2) | "Ranked by document creation timestamps." |
| `RANKING_STRATEGY_RELEVANCE_SCORE` (3) | "Ranked by document relevance score." |
| `RANKING_STRATEGY_USAGE_COUNT` (4) | "Ranked by number of usages, as reported by the app." |
| `RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP` (5) | "Ranked by timestamp of last usage, as reported by the app." |
| `RANKING_STRATEGY_SYSTEM_USAGE_COUNT` (6) | "Ranked by number of usages from a system UI surface." |
| `RANKING_STRATEGY_SYSTEM_USAGE_LAST_USED_TIMESTAMP` (7) | "Ranked by timestamp of last usage from a system UI surface." |
| `RANKING_STRATEGY_JOIN_AGGREGATE_SCORE` (8) | ordered by "the aggregated ranking signal of the joined documents" |
| `RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION` (9) | "Ranked by the advanced ranking expression provided." |

Usage counters are app-fed, not inferred: `reportUsageAsync()` — "Reports usage of a particular document by namespace and ID. A usage report represents an event in which a user interacted with or viewed a document." It feeds strategies 4 and 5 and is explicitly optional ([AppSearchSession.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/AppSearchSession.java)).

The advanced ranking expression (`setRankingStrategy(String)`) is a maths expression over per-document signals — arithmetic operators; `log`, `pow`, `sqrt`, `abs`, trigonometric functions; variadic `max`, `min`, `sum`, `avg`, `len`; document functions `documentScore()`, `creationTimestamp()`, `relevanceScore()`, `usageCount()`; `getScorableProperty()`; and list helpers `minOrDefault()`, `maxOrDefault()`, `filterByRange()`. "Syntax/type errors fail the search; evaluation errors assign default scores." ([SearchSpec.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/SearchSpec.java)) `setResultCountPerPage()` accepts 0–10,000, default 10.

This is the whole of what AppSearch gives for free: lexical relevance plus app-reported recency/frequency, composable in a declared arithmetic expression. There is no learned model, no personalisation, nothing that leaves the device.

### 1.2 UsageStatsManager

[UsageStatsManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStatsManager.java): "Provides access to device usage history and statistics. Usage data is aggregated into time intervals: days, weeks, months, and years."

**Permission.** `PACKAGE_USAGE_STATS`, a special access grant, not a runtime dialog: "Most methods on this API require the permission `android.permission.PACKAGE_USAGE_STATS`. However, declaring the permission implies intention to use the API and the user of the device still needs to grant permission through the Settings application. See `Settings.ACTION_USAGE_ACCESS_SETTINGS`." ([UsageStatsManager reference](https://developer.android.com/reference/android/app/usage/UsageStatsManager)) `queryEventsForSelf` and `getAppStandbyBucket` (own app only) need no permission.

**Two shapes of data.**

Aggregates — `queryUsageStats(intervalType, begin, end)`: "Gets application usage stats for the given time range, aggregated by the specified interval", with `INTERVAL_DAILY` / `WEEKLY` / `MONTHLY` / `YEARLY` / `INTERVAL_BEST` ("An interval type that will use the best fit interval for the given time range"). `queryAndAggregateUsageStats(begin, end)`: "A convenience method that queries for all stats in the given range … merges the resulting data, and keys it by package name."

Per-package fields, verbatim from [UsageStats.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStats.java):

- `getLastTimeUsed()` — "Get the last time this package's activity was used, measured in milliseconds since the epoch."
- `getLastTimeVisible()` — "Get the last time this package's activity is visible in the UI, measured in milliseconds since the epoch."
- `getTotalTimeInForeground()` — "Get the total time this package spent in the foreground, measured in milliseconds. When in the foreground, the user is actively interacting with the app."
- `getTotalTimeVisible()` — "Get the total time this package's activity is visible in the UI, measured in milliseconds. Note: An app may be visible but not considered foreground. Apps in the foreground must be visible, so visible time includes time in the foreground."
- `getLastTimeForegroundServiceUsed()` — "Get the last time this package's foreground service was used, measured in milliseconds since the epoch."

`getLastTimeVisible`, `getTotalTimeVisible` and `getLastTimeForegroundServiceUsed` are API 29 additions, so all are available at this project's floor. There is **no launch-count field** in `UsageStats`; counts must be derived from the event log.

Event log — `queryEvents(begin, end)`: "Query for events in the given time range. Events are only kept by the system for a few days." ([UsageStatsManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStatsManager.java)). Additional API-30 restriction from the [reference](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long)): "Starting from Android R, if the user's device is not in an unlocked state (as defined by `UserManager.isUserUnlocked()`), then null will be returned."

Event types relevant to ranking, verbatim from [UsageEvents.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageEvents.java):

- `ACTIVITY_RESUMED` (1) — "An event type denoting that an Activity moved to the foreground. … This event is corresponding to `Activity#onResume()` of the activity's lifecycle." (`MOVE_TO_FOREGROUND` is deprecated in its favour.)
- `ACTIVITY_PAUSED` (2) — the `onPause()` counterpart.
- `USER_INTERACTION` (7) — "An event type denoting that a package was interacted with in some way by the user."
- `SHORTCUT_INVOCATION` (8) — "An event type denoting that an action equivalent to a `ShortcutInfo` is taken by the user."
- `NOTIFICATION_INTERRUPTION` (12) and `APP_COMPONENT_USED` (31) are `@SystemApi`/`@hide` — **not readable by a third-party launcher.**

Granularity is per activity, timestamped in ms: "If a package has multiple activities, this event is reported for each activity that moves to foreground."

**Prediction signals.** The only prediction-adjacent value exposed is the standby bucket, and only for the calling app: `getAppStandbyBucket()` — "Returns the current standby bucket of the calling app". Buckets ([UsageStatsManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStatsManager.java)):

- `STANDBY_BUCKET_ACTIVE` (10) — "The app was used very recently, currently in use or likely to be used very soon."
- `STANDBY_BUCKET_WORKING_SET` (20) — "The app was used recently and/or likely to be used in the next few hours."
- `STANDBY_BUCKET_FREQUENT` (30) — "The app was used in the last few days and/or likely to be used in the next few days."
- `STANDBY_BUCKET_RARE` (40) — "The app has not be used for several days and/or is unlikely to be used for several days."
- `STANDBY_BUCKET_RESTRICTED` (45), `STANDBY_BUCKET_NEVER` (50), `STANDBY_BUCKET_EXEMPTED` (5).

The buckets do encode the system's own likelihood estimate, but `getAppStandbyBucket()` with no argument answers only for the caller — the per-package overload is not public API. So a launcher **cannot read other apps' buckets** and cannot borrow the system's prediction.

**No public app-prediction API.** [AppPredictionManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/prediction/AppPredictionManager.java) — "Class that provides methods to create prediction clients" — is annotated `@SystemApi` and `@hide`. AOSP Launcher3's predicted-apps row uses it as a bundled system app; a Play-distributed launcher cannot. OUT.

### 1.3 LauncherApps and ShortcutManager

From [LauncherApps.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/LauncherApps.java):

- `getActivityList()` — "Retrieves a list of activities that specify `Intent#ACTION_MAIN` and `Intent#CATEGORY_LAUNCHER`, across all apps, for a specified user." This is the launcher's app corpus.
- `getShortcuts()` — "Returns `ShortcutInfo`s that match `query`. Callers must be allowed to access the shortcut information, as defined in `hasShortcutHostPermission()`." Access is gated on role, not a permission: the caller must be "the current launcher (or default launcher if there is no set current launcher)" or "the currently active voice interaction service".
- `ShortcutQuery` flags: `FLAG_MATCH_DYNAMIC` ("Include dynamic shortcuts in the result"), `FLAG_MATCH_PINNED` ("Include pinned shortcuts in the result"), `FLAG_MATCH_MANIFEST` ("Include manifest shortcuts in the result"), `FLAG_MATCH_CACHED` ("Include cached shortcuts in the result"), `FLAG_MATCH_PINNED_BY_ANY_LAUNCHER` ("Include all pinned shortcuts by any launchers, not just by the caller, in the result" — requires assistant role or `ACCESS_SHORTCUTS`).
- `FLAG_GET_KEY_FIELDS_ONLY` — "Requests 'key' fields only", for cheap staleness checks against a launcher-side cache.
- `cacheShortcuts()` — "Mark shortcuts as cached for a package. Only dynamic long lived shortcuts can be cached." Requires `ACCESS_SHORTCUTS` (signature-level) — **not available to a third-party launcher.**

`ShortcutQuery` has **no text-query parameter**: filters are package, activity, shortcut IDs, changed-since timestamp and the flags above. All text matching against shortcut labels is the launcher's own work.

**Rank.** [ShortcutInfo.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/ShortcutInfo.java): rank is "a non-negative, sequential value that's unique for each `getActivity` for each of the two types of shortcuts (static and dynamic)"; "Floating shortcuts, or shortcuts that are neither static nor dynamic, will all have rank 0, because they aren't sorted." Rank is therefore the **publishing app's** declared display order within one activity — an ordinal, not a score, not comparable across packages, and absent (0) for pinned/floating shortcuts. It is usable as a within-app tiebreak only.

**Usage reporting.** The reporting side belongs to publishing apps, and the platform explicitly hands the resulting signal to launchers. [ShortcutManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/ShortcutManager.java), `reportShortcutUsed`: "Apps that publish shortcuts should call this method whenever the user selects the shortcut containing the given ID or when the user completes an action in the app that is equivalent to selecting the shortcut." The javadoc continues: "The information is accessible via `UsageStatsManager#queryEvents`" and "Typically, launcher apps use this information to build a prediction model so that they can promote the shortcuts that are likely to be used at the moment."

That is the platform's own statement of the intended design: **shortcut prediction is the launcher's job, computed from `SHORTCUT_INVOCATION` events read out of `UsageStatsManager`.** It requires `PACKAGE_USAGE_STATS`, and it inherits the "only kept … for a few days" retention.

### 1.4 Contacts

`READ_CONTACTS` (runtime, dangerous) is required ([Contacts Provider guide](https://developer.android.com/guide/topics/providers/contacts-provider)). For a search surface the relevant entry point is `CONTENT_FILTER_URI`, which provides "type-to-filter" capability matching contact name components ([ContactsContract.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/ContactsContract.java)) — matching happens inside the provider, so no local contact index is needed for name lookup.

**Affinity is gone since Android 10.** Verbatim from [ContactsContract.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/ContactsContract.java):

- `TIMES_CONTACTED` — "The number of times a contact has been contacted." → "@deprecated Contacts affinity information is no longer supported as of Android version `Build.VERSION_CODES#Q`. This column always contains 0."
- `LAST_TIME_CONTACTED` — same deprecation, "This column always contains 0."
- `CONTENT_FREQUENT_URI` — "@deprecated Frequent contacts are no longer supported as of Android version `Build.VERSION_CODES#Q`. This URI always returns an empty cursor."
- `CONTENT_STREQUENT_URI` / `CONTENT_STREQUENT_FILTER_URI` — "Frequent contacts are no longer included in the result as of Android version `Build.VERSION_CODES#Q`."

Since minSdk is 29 (= Q), **every contact-frequency signal the platform ever offered is dead on every supported device.** Contact ranking must be either alphabetical/lexical, starred (`Contacts.STARRED` still works), or computed by the launcher from its own observed openings.

Query cost: "By convention, the column `DATA1` is indexed. The Contacts Provider always uses this column for the data that the provider expects will be the most frequent target of a query." Bulk reads should use entities — "the Contacts Provider processes a query against an entity in a single transaction, which ensures that the retrieved data is internally consistent" — and batching via `applyBatch()`. Every read is a cross-process ContentProvider call; there is no shared-memory path.

### 1.5 Calendar

`READ_CALENDAR` (runtime, dangerous). The searchable/agenda view is `CalendarContract.Instances`, "a single occurrence of an event including time zone specific start and end days and minutes", read-only, queried via `Instances.query(cr, projection, begin, end)` which "returns all visible instances in the given range" over `content://.../instances/when/<begin>/<end>` ([CalendarContract.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/CalendarContract.java)).

Documented cost, verbatim: "This will cause an expansion of recurring events to fill this time range if they are not already expanded and will slow down for larger time ranges with many recurring events." A narrow window (today ± a few days) is cheap; a year-wide window is the documented slow path. This makes calendar a poor candidate for synchronous per-keystroke querying at wide ranges.

### 1.6 Settings surfacing

[SettingsSlicesContract.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/SettingsSlicesContract.java) — "Provides a contract for platform-supported Settings Slices. Contains definitions for the supported SliceProvider authority, authority Uri, and key constants." (API 28+.)

- `AUTHORITY = "android.settings.slices"`, `BASE_URI = content://android.settings.slices`.
- `PATH_SETTING_ACTION = "action"` — "Uri path indicating that the requested Slice should have inline controls for the corresponding setting. This path will only contain Slices defined by keys in this class."
- `PATH_SETTING_INTENT = "intent"` — "Uri path indicating that the requested Slice should be Intent-only. Slices with actions should use the `PATH_SETTING_ACTION` path."
- The complete platform-guaranteed key set is five: `KEY_AIRPLANE_MODE`, `KEY_BATTERY_SAVER`, `KEY_BLUETOOTH`, `KEY_LOCATION`, `KEY_WIFI`.

Five toggles is the entire contractual surface. Any broader settings search must be built from `android.provider.Settings` `ACTION_*` intents resolved against the device — a static, launcher-authored list, not a queryable index.

## 2. Contextual relevance and inferred suggestions: platform vs app

What the platform provides:

| Signal | Provided by | Cost |
|---|---|---|
| Lexical relevance over indexed text | AppSearch `RANKING_STRATEGY_RELEVANCE_SCORE` | index maintenance only |
| Recency / frequency over indexed documents | AppSearch `RANKING_STRATEGY_USAGE_*` — but only from the app's own `reportUsageAsync` calls | app must report every open |
| Per-package last-used timestamp and foreground duration | `UsageStats` | `PACKAGE_USAGE_STATS` |
| Timestamped per-activity and per-shortcut events | `UsageEvents` (`ACTIVITY_RESUMED`, `SHORTCUT_INVOCATION`, `USER_INTERACTION`) | `PACKAGE_USAGE_STATS`; retention "a few days" |
| Within-app shortcut display order | `ShortcutInfo.getRank()` | free (needs launcher role) |
| System likelihood estimate per app | `getAppStandbyBucket()` — **calling app only** | not usable for other apps |
| Contact frequency | **none** — removed in Q | n/a |
| App prediction model | `AppPredictionManager` — `@SystemApi` `@hide` | OUT |

Everything else the app computes. Specifically, time-of-day bucketing, co-occurrence, session-scoped boosts, and any decay curve have **no platform primitive at all**; they are derived by the launcher from `UsageEvents` timestamps (or from its own launch log, which needs no permission and no retention limit). Note that the `queryEvents` retention — "Events are only kept by the system for a few days" — makes `UsageStatsManager` unsuitable as the *store* for a time-of-day model; it can bootstrap one, but a durable model must be persisted locally by the launcher. A launcher-owned launch log is fully on-device and carries zero telemetry implication.

**What Android documents about its own launcher's ranking.** AOSP Launcher3's all-apps search is deliberately unranked. [DefaultAppSearchAlgorithm.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/allapps/search/DefaultAppSearchAlgorithm.java) defines `MAX_RESULTS_COUNT = 5`, lowercases the query, iterates the app list in its existing order, and appends any app for which `StringMatcherUtility.matches(...)` is true until the cap is hit. Its comment: "Do an intersection of the words in the query and each title, and filter out all the apps that don't match all of the words in the query." There is no scoring pass and no sort — first five matches in list order.

The matcher itself ([StringMatcherUtility.java](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/search/StringMatcherUtility.java)) is a prefix-at-break-point match: "Returns true if query is a prefix of target" via a `Collator` set to `PRIMARY` strength with `CANONICAL_DECOMPOSITION` (accent- and case-insensitive), tested at each break point in the target. Break points are after space separators, at digit transitions, at uppercase-after-lowercase/digit and uppercase-before-lowercase (camelCase), and at symbol/punctuation characters; Chinese text falls back to a `contains()` fuzzy match.

So the AOSP baseline for launcher search is: accent-insensitive prefix match on any word boundary, all query words required, top 5, no ranking. Ranked/predicted content in shipping Google launchers comes from `AppPredictionManager`, which is system-only.

## 3. What a truthful "why this result" line can say

A "why" line is only truthful if the signal it names is readable at query time and actually entered the score. Readable values, by source:

**Free, no permission, no risk of staleness:**
- Whether and where the query matched: exact/prefix/word-boundary/substring, and which field (app label, shortcut label, contact name, setting title). The launcher computes the match itself in every case except AppSearch full-text, and even there `RANKING_STRATEGY_RELEVANCE_SCORE` yields a number per result.
- Result kind (app, shortcut, contact, calendar instance, setting).
- `ShortcutInfo.getRank()` — but only meaningful as "app's own order within <activity>", and 0 for pinned/floating shortcuts, so it is not safe to render as a general "rank".
- The launcher's own recorded opens: "you opened this 3 times today", "last opened 12 minutes ago" — fully explainable because the launcher owns both the counter and the decay.

**Readable with `PACKAGE_USAGE_STATS`:**
- `getLastTimeUsed()` / `getLastTimeVisible()` — exact epoch-ms timestamps, so "last used at 08:14" is literally true.
- `getTotalTimeInForeground()` / `getTotalTimeVisible()` — exact ms, so "2h 14m in the foreground this week" is true for the queried interval.
- Counts derived from `ACTIVITY_RESUMED` / `SHORTCUT_INVOCATION` events — true, but only over the "few days" the system retains, so the sentence must be scoped to that window ("5 launches in the last 3 days"), not stated open-endedly.

**Not readable, so cannot be claimed:**
- Any statement about how likely the user is to want something, sourced from the system — `getAppStandbyBucket()` answers only for the launcher itself, and `AppPredictionManager` is `@hide`.
- Contact frequency or "you contact them often" — the columns "always contain 0" since Q.
- Aggregate cross-package rank ordering from the platform; there is none.

Consequence: a "why" line built from (a) match kind and matched field, (b) launcher-owned recency/frequency counters, and (c) optionally `UsageStats` timestamps is fully truthful, and each term is a number the launcher can print. A line phrased as prediction ("we think you'll want this") is not backed by any readable value and would be a claim about a model, not a signal.

## 4. Cost

**Latency.** No absolute figures are published for AppSearch, `UsageStatsManager`, or the content providers. Documented relative statements only:
- AppSearch "offers lower latency for indexing and searching over large datasets compared to SQLite"; `PlatformStorage` adds "binder latency when calling the AppSearch system service" ([guide](https://developer.android.com/guide/topics/search/appsearch)).
- AppSearch `search()` is "lightweight"; cost lands in `getNextPageAsync` ([AppSearchSession.java](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/AppSearchSession.java)).
- Calendar `Instances` queries "will slow down for larger time ranges with many recurring events" ([CalendarContract.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/CalendarContract.java)).
- Contacts: `DATA1` is the indexed column; entity queries run "in a single transaction" ([Contacts Provider guide](https://developer.android.com/guide/topics/providers/contacts-provider)).

Anything more precise has to be measured on-device; primary sources do not supply numbers.

**Index size.** No numeric per-app cap is documented for `LocalStorage`. The shared backends are capped but the numbers are not published: "AppSearch limits the number of documents and size of documents an application can index to ensure an efficient central index" ([guide](https://developer.android.com/guide/topics/search/appsearch)). A phone-scale launcher corpus — a few hundred launchable activities, low thousands of shortcuts, contacts, a settings list — is small; there is no primary source suggesting it approaches any limit.

**Battery / background work.** For a launcher the important fact is that a *foreground* search surface is unaffected by Doze and standby; the exposure is only to whatever indexing runs in the background.

Doze restrictions, verbatim from [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby):
> "Suspends network access. Ignores wake locks. Defers standard `AlarmManager` alarms, including `setExact()` and `setWindow()`, to the next maintenance window. … Doesn't perform Wi-Fi scans. Doesn't let sync adapters run. Doesn't let `JobScheduler` run."
and "`WorkManager` uses `JobScheduler` internally, so `WorkManager` tasks don't run." Also: "Neither `setAndAllowWhileIdle()` nor `setExactAndAllowWhileIdle()` can fire alarms more than once per nine minutes, per app."

App Standby buckets ([App Standby Buckets](https://developer.android.com/topic/performance/appstandby)): "The system dynamically assigns each app to a priority bucket, reassigning the apps as needed. The system might rely on a preloaded app that uses machine learning to determine how likely each app is to be used, and assigns apps to the appropriate buckets." Also: "Every manufacturer can set their own criteria for how non-active apps are assigned to buckets. Don't try to influence which bucket your app is assigned to."

A launcher spends most of its life in **active**: "An app is in the active bucket while it is used, is very recently used, or when it does any of the following: Launches an activity. Runs a long running foreground service. Is tapped by the user from a notification", and "If an app is in the active bucket, the system places minimal restrictions on the app's jobs or alarms." On API 36: "Beginning with Android 16 (API level 36), background jobs have a generous runtime quota if they're started by an app in the active bucket."

Worst case is **restricted**: "You can run jobs once per day in a 10-minute batched session" and "Your app can invoke one alarm per day."

Practical consequence: index maintenance driven by user interaction or by `LauncherApps` package-change callbacks while the launcher is foreground is unconstrained; index maintenance scheduled as periodic background work is subject to Doze and bucket quotas and cannot be relied on for freshness.

## 5. Constraints

**Package visibility (API 30+).** From [Package visibility filtering](https://developer.android.com/training/package-visibility):
> "When an app targets Android 11 (API level 30) or higher and queries for information about the other apps that are installed on a device, the system filters this information by default. This filtering behavior means that your app can't detect all the apps installed on a device, which helps minimize the potentially sensitive information that your app can access but doesn't need to fulfill its use cases."

Filtering affects `queryIntentActivities()`, `getPackageInfo()`, `getInstalledApplications()`. Automatically-visible packages ([Automatic visibility](https://developer.android.com/training/package-visibility/automatic)) are: the app itself, certain core system packages, the installer, apps that interacted with the app's components, apps granted URI permissions, and IME apps — **the launchable-app corpus is not on that list.**

`<queries>` supports intent-signature declarations, with the restriction that each `<intent>` "Must include exactly one `<action>` element" and cannot use `path`/`pathPrefix`/`pathPattern`/`port` in `<data>` ([Declaring package visibility needs](https://developer.android.com/training/package-visibility/declaring)). The `<queries>` docs do not publish a launcher example, and the [use-cases page](https://developer.android.com/training/package-visibility/use-cases) covers URLs, files, services and custom functionality — it does not cover launchers or app listings. The escape hatch is stated plainly: "In the rare cases where the `<queries>` element doesn't provide adequate package visibility, you can use the `QUERY_ALL_PACKAGES` permission. If you publish your app on Google Play, your app's use of this permission is subject to approval." ([Package visibility filtering](https://developer.android.com/training/package-visibility))

Note that `LauncherApps.getActivityList()` is the launcher's documented corpus API — "Retrieves a list of activities that specify `Intent#ACTION_MAIN` and `Intent#CATEGORY_LAUNCHER`, across all apps, for a specified user" ([LauncherApps.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/LauncherApps.java)) — and is not among the methods the visibility guide names as filtered. Whether the deployed app in fact needs `QUERY_ALL_PACKAGES` therefore depends on which APIs the corpus is built from; that is a testable question on API 30+ devices, not one primary docs answer outright.

**Play policy on `QUERY_ALL_PACKAGES`** ([Permissions and APIs that access sensitive information](https://support.google.com/googleplay/android-developer/answer/16558241), [QUERY_ALL_PACKAGES policy](https://support.google.com/googleplay/android-developer/answer/10158779)):
- Permitted core-purpose examples given are "device search, antivirus apps, file managers, and browsers". **Launchers are not named.**
- "You may not use `QUERY_ALL_PACKAGES` if your app can operate with a more targeted scoped package visibility declaration."
- Declaration is mandatory: developers must "declare this and any other high-risk permissions using the Permissions Declaration Form in Play Console".
- "The inventory of installed apps queried from a device are regarded as personal and sensitive user data" and "App inventory data queried from Play-distributed apps may never be sold nor shared for analytics or ads monetization purposes." (A zero-analytics, zero-cloud product satisfies this trivially.)

**Play policy on usage access.** `PACKAGE_USAGE_STATS` does **not** appear on Play's restricted-permission declaration list; the declared set is SMS/Call Log, background location, `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, Accessibility, Body Sensors, and Health Connect ([Permissions and APIs that access sensitive information](https://support.google.com/googleplay/android-developer/answer/16558241)). The general obligation still applies: "Request permissions and APIs that access sensitive information to access data in context (via incremental requests), so that users understand why your app is requesting the permission" ([User Data](https://support.google.com/googleplay/android-developer/answer/9888170)). The real cost of usage access is user-facing, not policy-facing: it is granted through a Settings screen (`Settings.ACTION_USAGE_ACCESS_SETTINGS`), never a runtime dialog.

**Play policy on contacts — a live deadline.** From [Understanding Restricted Permissions with minimum scope alternatives](https://support.google.com/googleplay/android-developer/answer/16935362) and [Permissions and APIs that access sensitive information](https://support.google.com/googleplay/android-developer/answer/16558241):
- A Contacts Permissions policy governing `READ_CONTACTS` was announced April 15, 2026; the recommended alternative is the "Android Contact Picker, a more secure, easy-to-integrate alternative that minimizes data collection and improves user safety".
- Scope trigger is target SDK: apps targeting **Android 17 / API 37+** may request `READ_CONTACTS` only where the Contact Picker is insufficient for core functionality.
- "Android Contact Picker is not backported to previous versions and only exists for Android 17 or later (API level 37+)."
- Enforcement date: **October 28, 2026** — before which in-scope developers must submit a Play Console declaration naming the user-facing feature and justifying why the picker is insufficient, or drop the permission.
- "Private and enterprise device management apps are exempt from this policy requirement."

At targetSdk 36 the project is currently out of scope, but a contacts-in-search feature acquires a declaration obligation the moment targetSdk moves to 37. A one-shot picker is structurally incompatible with type-ahead contact search, so the justification would have to be made rather than avoided.

## 6. Facts bearing on the decision

Ordered by how much they constrain the design; no recommendation is made here.

1. **Nothing on this list requires a server or telemetry.** AppSearch `LocalStorage`, `UsageStatsManager`, `LauncherApps`, and the content providers are entirely on-device. `AppPredictionManager` is the one prediction API Android ships, and it is `@SystemApi @hide` — unavailable to a Play-distributed launcher regardless of cloud policy. `PlayServicesStorage` requires GMS. Both are OUT.
2. **AppSearch is usable at minSdk 29 only via `LocalStorage`** (no `@RequiresApi`; index in the app data directory, no documented size cap). `PlatformStorage` is `@RequiresApi(S)` = API 31 and carries both a documented (unnumbered) document/size cap and extra binder latency. Writes are single-threaded, reads are multi-threaded, and durability requires an explicit `requestFlush()`/`close()`.
3. **AppSearch's built-in ranking covers exactly three axes** — lexical relevance, app-reported usage count, app-reported last-used time — composable through an arithmetic ranking expression (`log`/`pow`/`sqrt`/`max`/`min`/`sum`/`avg` over `documentScore()`, `creationTimestamp()`, `relevanceScore()`, `usageCount()`). Usage counters only exist if the launcher calls `reportUsageAsync` on every open. Time-of-day and co-occurrence have no platform primitive whatsoever.
4. **AOSP's own launcher does not rank search results.** `DefaultAppSearchAlgorithm` returns the first 5 matches in list order with no scoring; matching is accent-insensitive prefix-at-word-boundary requiring all query words. Any ranking at all is a step beyond the AOSP baseline, and the ranked/predicted surfaces in Google's launchers come from a system-only API.
5. **All contact-affinity signals are dead at minSdk 29.** `TIMES_CONTACTED` and `LAST_TIME_CONTACTED` "always contain 0"; `CONTENT_FREQUENT_URI` "always returns an empty cursor" — all as of Q. Contact ranking must be lexical, starred, or launcher-computed. Separately, `READ_CONTACTS` acquires a Play declaration obligation the moment targetSdk reaches 37 (enforcement October 28, 2026), and the prescribed alternative (one-shot Contact Picker) does not support type-ahead search.
6. **`PACKAGE_USAGE_STATS` buys real signal but is the heaviest user-facing cost.** It yields exact per-package `lastTimeUsed` / `lastTimeVisible` / foreground and visible durations, plus timestamped `ACTIVITY_RESUMED` / `SHORTCUT_INVOCATION` / `USER_INTERACTION` events — the platform explicitly designates `SHORTCUT_INVOCATION` as the input launchers use to "build a prediction model". It is not on Play's restricted-permission list, but it is granted only through a Settings screen, and events are "only kept by the system for a few days", so any durable time-of-day or co-occurrence model must be persisted by the launcher itself. A launcher-owned launch log needs no permission, has no retention limit, and is the only source with unlimited history.
7. **Shortcut `rank` is not a ranking signal across the corpus.** It is the publishing app's ordinal within one activity, per type, and 0 for pinned/floating shortcuts. `ShortcutQuery` has no text parameter, so all shortcut label matching is the launcher's work. Shortcut access requires the default-launcher (or assistant) role, not a permission; `cacheShortcuts` requires `ACCESS_SHORTCUTS`, which a third-party launcher cannot hold.
8. **Every signal needed for a truthful "why" line is a readable number** — match kind and matched field (computed locally), launcher-owned open counts and recency, and with usage access, exact epoch-ms timestamps and ms durations. Counts derived from `UsageEvents` must be phrased within the few-day retention window. No readable value supports a phrasing framed as prediction.
9. **Cost figures are not published.** Primary sources give only relative statements (AppSearch lower-latency-than-SQLite, `search()` lightweight, `PlatformStorage` adds binder hops, calendar `Instances` slows with wide ranges and many recurrences, contacts `DATA1` indexed). Absolute latency and index size for a phone-scale corpus must be measured on-device.
10. **Background work is the fragile part, foreground search is not.** A launcher sits in the active bucket while in use, where "the system places minimal restrictions on the app's jobs or alarms" (and API 36 grants a "generous runtime quota"). Doze suspends network, ignores wake locks, defers alarms, and blocks `JobScheduler` and therefore `WorkManager`; the restricted bucket allows one 10-minute job session and one alarm per day. Index freshness driven by foreground interaction or `LauncherApps` callbacks is safe; index freshness driven by scheduled background work is not guaranteed.
11. **Package visibility is an open question, not a settled blocker.** Targeting 30+ filters `queryIntentActivities()` / `getPackageInfo()` / `getInstalledApplications()`, and launchable apps are not automatically visible. `LauncherApps.getActivityList()` is the documented launcher corpus API and is not named among the filtered methods. Play permits `QUERY_ALL_PACKAGES` for "device search, antivirus apps, file managers, and browsers" — launchers are not listed — requires a Permissions Declaration Form, and forbids it when "a more targeted scoped package visibility declaration" suffices. App inventory is "personal and sensitive user data" that "may never be sold nor shared for analytics or ads monetization purposes"; a zero-analytics product meets that automatically.
12. **Settings search has almost no contract.** `SettingsSlicesContract` guarantees exactly five keys (airplane mode, battery saver, Bluetooth, location, Wi-Fi) at API 28+. Anything broader is a launcher-authored list of `Settings.ACTION_*` intents resolved per device.

## Sources

- https://developer.android.com/guide/topics/search/appsearch
- https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/SearchSpec.java
- https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch/src/main/java/androidx/appsearch/app/AppSearchSession.java
- https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch-local-storage/src/main/java/androidx/appsearch/localstorage/LocalStorage.java
- https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/appsearch/appsearch-platform-storage/src/main/java/androidx/appsearch/platformstorage/PlatformStorage.java
- https://developer.android.com/reference/android/app/usage/UsageStatsManager
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStatsManager.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStats.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageEvents.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/prediction/AppPredictionManager.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/LauncherApps.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/ShortcutManager.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/pm/ShortcutInfo.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/ContactsContract.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/CalendarContract.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/SettingsSlicesContract.java
- https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/allapps/search/DefaultAppSearchAlgorithm.java
- https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/search/StringMatcherUtility.java
- https://developer.android.com/guide/topics/providers/contacts-provider
- https://developer.android.com/training/package-visibility
- https://developer.android.com/training/package-visibility/automatic
- https://developer.android.com/training/package-visibility/declaring
- https://developer.android.com/training/package-visibility/use-cases
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/topic/performance/appstandby
- https://support.google.com/googleplay/android-developer/answer/10158779 (QUERY_ALL_PACKAGES)
- https://support.google.com/googleplay/android-developer/answer/16558241 (Permissions and APIs that access sensitive information)
- https://support.google.com/googleplay/android-developer/answer/16935362 (Restricted permissions with minimum scope alternatives — Contacts)
- https://support.google.com/googleplay/android-developer/answer/9888170 (User Data)
