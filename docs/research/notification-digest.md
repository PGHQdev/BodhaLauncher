# Producing a notification digest deterministically (no model) on Android API 29–36

- Date: 2026-08-05
- Question: How can a useful notification digest be produced WITHOUT any model — purely deterministically — on Android API 29–36? What does `NotificationListenerService` actually expose, which signals does the platform already compute, what breaks across 29→36, how reliable is the listener, and what does Play policy require?
- Method: primary sources only — developer.android.com reference/guides, AOSP source (android.googlesource.com, `refs/heads/main`), Google Play policy pages on support.google.com. Quotes are verbatim from the cited page/file. Where the public SDK reference and AOSP disagree (e.g. auto-group threshold), both are cited.

## 1. What a `NotificationListenerService` actually exposes on API 29–36

### 1.1 Binding, permission, lifecycle contract

From [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) (class added API 18):

- "A service that receives calls from the system when new notifications are posted or removed, or their ranking changed."
- "To extend this class, you must declare the service in your manifest file with the `Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE` permission and include an intent filter with the `SERVICE_INTERFACE` action."
- "The service should wait for the `onListenerConnected()` event before performing any operations. The `requestRebind(ComponentName)` method is the only one that is safe to call before `onListenerConnected()` or after `onListenerDisconnected()`."
- "Notification listeners cannot get notification access or be bound by the system on low-RAM devices running Android Q (and below)." — i.e. on API 29 low-RAM devices the feature simply does not exist.
- "The system also ignores notification listeners running in a work profile. A `DevicePolicyManager` might block notifications originating from a work profile."
- "From `Build.VERSION_CODES.N` onward all callbacks are called on the main thread." — so every callback below is main-thread on 29–36; parsing/persisting must be moved off it.

Callbacks and their added-in levels (same page):

| Member | Added | Verbatim note |
|---|---|---|
| `onNotificationPosted(sbn, rankingMap)` | 21 | "The current ranking map that can be used to retrieve ranking information for active notifications, including the newly posted one." |
| `onNotificationRemoved(sbn, rankingMap, reason)` | 26 | "The `StatusBarNotification` object you receive will be 'light'; that is, the result from `StatusBarNotification.getNotification` may be missing some heavyweight fields such as `Notification.contentView` and `Notification.largeIcon`. However, all other fields on `StatusBarNotification`, sufficient to match this call with a prior call to `onNotificationPosted(StatusBarNotification)`, will be intact." |
| `onNotificationRankingUpdate(rankingMap)` | 21 | "Implement this method to be notified when the notification ranking changes." |
| `onListenerConnected()` | 21 | "You are safe to call `getActiveNotifications()` at this time." |
| `onListenerDisconnected()` | 24 | "You will not receive any events after this call, and may only call `requestRebind(ComponentName)` at this time." |
| `getActiveNotifications()` | 18 | "Request the list of outstanding notifications (that is, those that are visible to the current user). … An array of active notifications, sorted in natural order." |
| `getActiveNotifications(String[] keys)` | 21 | "Request one or more notifications by key. Useful if you have been keeping track of notifications but didn't want to retain the bits, and now need to go back and extract more data out of those notifications." |
| `getSnoozedNotifications()` | 26 | "Like `getActiveNotifications()`, but returns the list of currently snoozed notifications, for all users this listener has access to." |
| `setNotificationsShown(String[] keys)` | 23 | "Inform the notification manager that these notifications have been viewed by the user. This should only be called when there is sufficient confidence that the user is looking at the notifications, such as when the notifications appear on the screen due to an explicit user interaction." |
| `snoozeNotification(key, durationMs)` | 26 | "the notification manager will actually remove the notification and you will get an `onNotificationRemoved(StatusBarNotification)` callback. When the snoozing period expires, you will get a `onNotificationPosted(...)` callback" |

`onNotificationRemoved`'s `reason` (API 26+) is the single most useful deterministic signal for "did the user deal with this?" — the constants are `REASON_CLICK` ("Notification was canceled by the status bar reporting a notification click"), `REASON_CANCEL` ("…a user dismissal"), `REASON_CANCEL_ALL` ("…a user dismiss all"), `REASON_APP_CANCEL`, `REASON_TIMEOUT`, `REASON_SNOOZED`, `REASON_GROUP_OPTIMIZATION` ("Notification was canceled because it was an invisible member of a group"), `REASON_LISTENER_CANCEL`, `REASON_CHANNEL_BANNED`, `REASON_PACKAGE_SUSPENDED`, `REASON_PROFILE_TURNED_OFF`, `REASON_UNAUTOBUNDLED`, `REASON_CLEAR_DATA`, `REASON_ASSISTANT_CANCEL`, plus `REASON_LOCKDOWN` (API 34) and `REASON_BUNDLE_DISMISSED` (version 36.1) — all from the same page.

`REASON_LOCKDOWN` (API 34) carries an obligation: "Notification was canceled when entering lockdown mode, which turns off Smart Lock, fingerprint unlocking, and notifications on the lock screen. **All the listeners shall ensure the canceled notifications are indeed removed on their end to prevent data leaking.**"

### 1.2 `StatusBarNotification` payload

From [StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification):

- `getKey()` (API 20) — "A unique instance key for this notification record." The identity used everywhere else (RankingMap keys, `getActiveNotifications(keys)`).
- `getGroupKey()` (API 21) — "A key that indicates the group with which this message ranks."
- `getOverrideGroupKey()` (API 24) — "Returns the override group key."
- `getPackageName()` (API 18) — "The package that the notification belongs to."
- `getOpPkg()` (API 29) — "The package that posted the notification. Might be different from `getPackageName()` if the app owning the notification has a notification delegate."
- `getUid()` (API 29) — "The notifying app's (`getPackageName()`'s) uid."
- `getPostTime()` (API 18) — "The time (in `System.currentTimeMillis` time) the notification was posted, **which may be different than `Notification.when`**."
- `getId()` / `getTag()` (API 18) — the app-supplied `notify(int, Notification)` identifiers.
- `getUser()` (API 21) — "The `UserHandle` for whom this notification is intended." (`getUserId()` deprecated in 21.)
- `isGroup()` (API 24) — "Returns true if this notification is part of a group." `isAppGroup()` (API 30) — "Returns true if **application** asked that this notification be part of a group." The delta between the two is exactly the system's auto-grouping (§2.1).
- `isOngoing()` (API 18) — checks `FLAG_ONGOING_EVENT`. `isClearable()` (API 18) — "checks the notification's flags for either `Notification.FLAG_ONGOING_EVENT` or `Notification.FLAG_NO_CLEAR`."

`getPostTime()` vs `Notification.when` matters for digest ordering. [`Notification.when`](https://developer.android.com/reference/android/app/Notification#when) is app-chosen and explicitly may be in the future: "Choose a timestamp that will be most relevant to the user. … Notification of an upcoming meeting should be stamped with the time the meeting will begin (that is, in the future)." `getPostTime()` is the only monotone-ish, system-stamped arrival time.

### 1.3 `Notification.extras` — which keys are reliable

[`Notification.extras`](https://developer.android.com/reference/android/app/Notification#extras) (API 19): "Additional semantic data to be carried around with this Notification. **The extras keys defined here are intended to capture the original inputs to `Builder` APIs, and are intended to be used by `NotificationListenerService` implementations to extract detailed information from notification objects.**" — extras are the sanctioned read surface for a listener; there is no other structured content API.

Reliable because they are written by `Notification.Builder` itself (all from the [Notification reference](https://developer.android.com/reference/android/app/Notification)):

| Key | Added | Verbatim |
|---|---|---|
| `EXTRA_TITLE` = `"android.title"` | 19 | "this is the title of the notification, as supplied to `Builder.setContentTitle(CharSequence)`" |
| `EXTRA_TEXT` = `"android.text"` | 19 | "this is the main text payload, as supplied to `Builder.setContentText(CharSequence)`" |
| `EXTRA_SUB_TEXT` = `"android.subText"` | 19 | "this is a third line of text, as supplied to `Builder.setSubText(CharSequence)`" |
| `EXTRA_BIG_TEXT` = `"android.bigText"` | 21 | "this is the longer text shown in the expanded form of a `BigTextStyle` notification" |
| `EXTRA_TEXT_LINES` = `"android.textLines"` | 19 | "An array of `CharSequence`s to show in `InboxStyle` expanded notifications, each of which was supplied to `InboxStyle.addLine(CharSequence)`" |
| `EXTRA_SUMMARY_TEXT` = `"android.summaryText"` | 19 | "a line of summary information intended to be shown alongside expanded notifications" |
| `EXTRA_INFO_TEXT` = `"android.infoText"` | 19 | "a small piece of additional text as supplied to `Builder.setContentInfo(CharSequence)`" |
| `EXTRA_TEMPLATE` = `"android.template"` | 21 | "A string representing the name of the specific `Notification.Style` used to create this notification." |
| `EXTRA_MESSAGES` = `"android.messages"` | 24 | "an array of `Notification.MessagingStyle.Message` bundles provided by a `MessagingStyle` notification. This extra is a parcelable array of bundles." |
| `EXTRA_HISTORIC_MESSAGES` = `"android.messages.historic"` | 26 | "an array of historic `Notification.MessagingStyle.Message` bundles" |
| `EXTRA_MESSAGING_PERSON` = `"android.messagingUser"` | 28 | "the person to be displayed for all messages sent by the user including direct replies" |
| `EXTRA_CONVERSATION_TITLE` = `"android.conversationTitle"` | 24 | "a `CharSequence` to be displayed as the title to a conversation represented by a `Notification.MessagingStyle`" |
| `EXTRA_IS_GROUP_CONVERSATION` = `"android.isGroupConversation"` | 28 | "whether the `MessagingStyle` notification represents a group conversation" |
| `EXTRA_PEOPLE_LIST` = `"android.people.list"` | 28 | "An arrayList of `Person` objects containing the people that this notification relates to." |
| `EXTRA_PROGRESS` / `_MAX` / `_INDETERMINATE` | 19 | the values "supplied to `Builder.setProgress(int,int,boolean)`" |
| `EXTRA_SHOW_WHEN` = `"android.showWhen"` | 19 | "whether `when` should be shown, as supplied to `Builder.setShowWhen(boolean)`" |

Deprecated / not to be relied on: `EXTRA_PEOPLE` ("This constant was deprecated in API level 28. the actual objects are now in `EXTRA_PEOPLE_LIST`"), `EXTRA_LARGE_ICON` and `EXTRA_SMALL_ICON` (deprecated 26).

Two hard caps, from AOSP [Notification.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Notification.java): `private static final int MAX_CHARSEQUENCE_LENGTH = 1024;` applied via `safeCharSequence()` in `setContentTitle`/`setContentText`/`setSubText`; `public static final int MAX_ACTION_BUTTONS = 3;`. [`MessagingStyle.MAXIMUM_RETAINED_MESSAGES`](https://developer.android.com/reference/android/app/Notification.MessagingStyle#MAXIMUM_RETAINED_MESSAGES) (API 24) = 25: "The maximum number of messages that will be retained in the Notification itself (the number displayed is up to the platform)."

Note extras values are `CharSequence` (often `SpannableString`), not `String` — reading them as `String` via `getString()` returns null for spanned values; use `getCharSequence()`.

### 1.4 `Ranking` and `RankingMap` — what the platform hands you

[`RankingMap`](https://developer.android.com/reference/android/service/notification/NotificationListenerService.RankingMap) (API 21): "Provides access to ranking information on currently active notifications. **Note that this object represents a ranking snapshot that only applies to notifications active at the time of retrieval.**"

- `getOrderedKeys()` (21) — "Request the list of notification keys in their current ranking order."
- `getRanking(key, outRanking)` (21) — "Populates `outRanking` with ranking information for the notification with the given key." Returns false for unknown keys.

[`Ranking`](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking) (API 21): "Stores ranking related information on a currently active notification. **`Ranking` objects aren't automatically updated as notification events occur. Instead, ranking information has to be retrieved again via the current `RankingMap`.**"

Public methods and the API level they became public — this is the exact ceiling of what is available to a minSdk-29 app:

| Method | Added | Verbatim |
|---|---|---|
| `getKey()` | 21 | "Returns the key of the notification this Ranking applies to." |
| `getRank()` | 21 | "the rank of the notification, that is the 0-based index in the list of active notifications" |
| `isAmbient()` | 21 | "Returns whether the notification is an ambient notification, that is a notification that doesn't require the user's immediate attention." |
| `matchesInterruptionFilter()` | 21 | "true if the notification is allowed by the filter, or false if it is blocked" |
| `getImportance()` | 24 | "Returns the importance of the notification, which dictates its modes of presentation" — one of `IMPORTANCE_UNSPECIFIED/NONE/MIN/LOW/DEFAULT/HIGH` |
| `getImportanceExplanation()` | 24 | "**If the importance has been overridden by user preference, then this will be non-null.** … the explanation for the importance, or null if it is the natural importance" |
| `getOverrideGroupKey()` | 24 | "**If the system has overridden the group key, then this will be non-null, and this key should be used to bundle notifications.**" |
| `getSuppressedVisualEffects()` | 24 | "Returns the type(s) of visual effects that should be suppressed for this notification." |
| `getChannel()` | 26 | "Returns the notification channel this notification was posted to, which dictates notification behavior and presentation." |
| `canShowBadge()` | 26 | "true if the notification can be displayed as a badge" |
| `getUserSentiment()` | 28 | "Returns how the system thinks the user feels about notifications from the channel provided by `getChannel()`. You can use this information to expose controls to help the user block this channel's notifications, if the sentiment is `USER_SENTIMENT_NEGATIVE`, or emphasize this notification if the sentiment is `USER_SENTIMENT_POSITIVE`." |
| `isSuspended()` | 28 | "Returns whether the app that posted this notification is suspended, so this notification should be hidden." |
| `canBubble()` | 29 | "whether the user has allowed bubbles globally, at the app level, and at the channel level" |
| `getLastAudiblyAlertedMillis()` | 29 | "Returns the last time this notification alerted the user via sound or vibration." |
| `getSmartActions()` | 29 | "Returns a list of smart `Notification.Action` that **can be added by the notification assistant**." |
| `getSmartReplies()` | 29 | "Returns a list of smart replies that **can be added by the notification assistant**." |
| `getConversationShortcutInfo()` | **31** | "Returns the shortcut information associated with this notification, if it is a conversation notification. **This might be null even if the notification is a conversation notification, if the posting app hasn't opted into the full conversation feature set yet.**" |
| `isConversation()` | **31** | "Returns whether this notification is a conversation notification, and would appear in the conversation section of the notification shade, on devices that separate that type of notification." |
| `getLockscreenVisibilityOverride()` | 31 | user/DPM lockscreen visibility override, or `VISIBILITY_NO_OVERRIDE` |
| `getSummarization()` | **version 36.1** | "Returns a summary of the content in the notification, or potentially of the current notification and related notifications (for example, if this is provided for a group summary notification it may be summarizing all the child notifications)." |

Not public API despite appearing in AOSP: `isNoisy()`, `getSnoozeCriteria()`, `getRankingAdjustment()`, `isBubble()`, `isTextChanged()`, `getProposedImportance()`, `hasSensitiveContent()`. They are absent from the public reference above and are not callable from an app.

`getSmartActions()` / `getSmartReplies()` / `getSummarization()` are populated only by a `NotificationAssistantService` — one privileged, usually-OEM/Google component per device. In AOSP these arrive as `Adjustment` signals applied to the record ([NotificationRecord.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationRecord.java), `applyAdjustments` handling `Adjustment.KEY_SENSITIVE_CONTENT`, `Adjustment.KEY_TYPE`, etc.). A third-party app cannot be the assistant, and an assistant may not be installed at all — so these fields must be treated as optional decoration, never as the digest's backbone.

### 1.5 Groups and summary notifications

- [`Notification.getGroup()`](https://developer.android.com/reference/android/app/Notification#getGroup()) (API 20): "Get the key used to group this notification into a cluster or stack with other notifications on devices which support such rendering."
- [`FLAG_GROUP_SUMMARY`](https://developer.android.com/reference/android/app/Notification#FLAG_GROUP_SUMMARY) (API 20, value 512): "should be set if this notification is the group summary for a group of notifications. Grouped notifications may display in a cluster or stack on devices which support such rendering. Requires a group key also be set using `Builder.setGroup`."
- [`getSortKey()`](https://developer.android.com/reference/android/app/Notification#getSortKey()) (API 20): "Get a sort key that orders this notification among other notifications from the same package. … Notifications will be sorted lexicographically using this value … This sort key can also be used to order members of a notification group."
- [`getGroupAlertBehavior()`](https://developer.android.com/reference/android/app/Notification#getGroupAlertBehavior()) (API 26): "Returns which type of notifications in a group are responsible for audibly alerting the user" — `GROUP_ALERT_ALL` / `GROUP_ALERT_CHILDREN` / `GROUP_ALERT_SUMMARY`.

The system auto-groups on the app's behalf. [Create a group of notifications](https://developer.android.com/develop/ui/views/notifications/group): "If your app sends four or more notifications and doesn't specify a group, the system automatically groups them on Android 7.0 and higher." The actual threshold is a device config resource, and AOSP `main` now ships **2**, not 4 — [config.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/config.xml): `<!-- Default number of notifications from the same app before they are automatically grouped by the OS --> <integer translatable="false" name="config_autoGroupAtCount">2</integer>`, read by `NotificationManagerService.getGroupHelper()` and compared as `children.size() >= mAutoGroupAtCount` in [GroupHelper.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/GroupHelper.java). The synthesised group key is `protected static final String AUTOGROUP_KEY = "ranker_group";` and synthesised summaries carry `BASE_FLAGS = FLAG_AUTOGROUP_SUMMARY | FLAG_GROUP_SUMMARY | FLAG_LOCAL_ONLY`. So:

- `sbn.getGroupKey()` may be a system invention, not an app intent; `sbn.isAppGroup()` (API 30) distinguishes the two, and `Ranking.getOverrideGroupKey()` is non-null exactly when the system overrode it.
- The threshold is OEM-tunable and has changed between AOSP revisions; do not hardcode 2 or 4.

Also relevant to counting: AOSP caps per-app active notifications at `static final int MAX_PACKAGE_NOTIFICATIONS = 50;` ([NotificationManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java); enforced as `if (count >= MAX_PACKAGE_NOTIFICATIONS) { … "Package has already posted or enqueued " + count + " notifications.  Not showing more." }`) and enqueue rate at `DEFAULT_MAX_NOTIFICATION_ENQUEUE_RATE = 5f` per second. A digest can therefore see at most ~50 live items per app; anything beyond that was never delivered.

### 1.6 `MessagingStyle` and conversation notifications

[`Notification.MessagingStyle`](https://developer.android.com/reference/android/app/Notification.MessagingStyle) (API 24): "Helper class for generating large-format notifications that include multiple back-and-forth messages of varying types between any number of people. … If the app is targeting Android `Build.VERSION_CODES.P` and above, it is required to use the `Person` class in order to get an optimal rendering. … From Android `Build.VERSION_CODES.UPSIDE_DOWN_CAKE`, messaging style notifications that are associated with a valid conversation shortcut (via `Notification.Builder.setShortcutId(String)`) are displayed in a dedicated conversation section in the shade above non-conversation alerting and silence notifications."

Per-message accessors, from [`MessagingStyle.Message`](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message): `getText()` (24), `getTimestamp()` (24) — "Get the time at which this message arrived", `getSenderPerson()` (28) — "Get the sender associated with this message. … This value may be null", `getDataUri()`/`getDataMimeType()` (24), and crucially `getMessagesFromBundleArray(Parcelable[])` (**API 30**) — "Returns a list of messages read from the given bundle list, e.g. `Notification.EXTRA_MESSAGES` or `Notification.EXTRA_HISTORIC_MESSAGES`." On API 29 this helper does not exist; the `EXTRA_MESSAGES` bundles must be parsed by hand (keys `"text"`, `"time"`, `"sender_person"`, …).

[`Person`](https://developer.android.com/reference/android/app/Person) exposes `getKey()`, `getName()`, `getUri()`, `isBot()`, `isImportant()` — `isImportant()` is an app-declared salience hint, not a system judgement.

What makes something a conversation, from [People and conversations](https://developer.android.com/develop/ui/views/notifications/conversations): "A notification is considered as a conversation notification if the following are true: The notification uses `MessagingStyle`. (Only if the app targets Android 11 or higher) The notification is associated with a valid long-lived dynamic or cached sharing shortcut. … The user hasn't demoted the conversation from the conversation section via notification channel settings, at the time of posting." Fallbacks on the same page: `MessagingStyle` without a shortcut (targeting ≤10) still renders in conversation style; and "If a notification doesn't use `MessagingStyle` but the app is recognized by the platform as a messaging app, and the notification's `category` parameter is set to `msg`, the notification is shown in the conversation space".

`Ranking.isConversation()` collapses all of that into one boolean — but only from **API 31**. On 29–30 the app must derive it (see §2.2).

### 1.7 Category constants

[`Notification.category`](https://developer.android.com/reference/android/app/Notification#category) (API 21): "One of the predefined notification categories (see the `CATEGORY_*` constants) that best describes this Notification. **May be used by the system for ranking and filtering.**" It is app-declared and optional; a null category is common.

Available at minSdk 29: `CATEGORY_CALL` ("incoming call (voice or video) or similar synchronous communication request"), `CATEGORY_MESSAGE` = `"msg"` ("incoming direct message (SMS, instant message, etc.)"), `CATEGORY_EMAIL` ("asynchronous bulk message (email)"), `CATEGORY_EVENT` ("calendar event"), `CATEGORY_REMINDER` (23, "user-scheduled reminder"), `CATEGORY_ALARM`, `CATEGORY_PROGRESS`, `CATEGORY_SOCIAL`, `CATEGORY_ERROR`, `CATEGORY_TRANSPORT`, `CATEGORY_SYSTEM` ("Reserved for system use"), `CATEGORY_SERVICE`, `CATEGORY_STATUS`, `CATEGORY_RECOMMENDATION`, `CATEGORY_PROMO` ("promotion or advertisement"), `CATEGORY_NAVIGATION` (28).

Added above the floor: `CATEGORY_MISSED_CALL`, `CATEGORY_STOPWATCH`, `CATEGORY_WORKOUT`, `CATEGORY_LOCATION_SHARING` (all **31**), `CATEGORY_VOICEMAIL` (**35**). All are plain strings, so an app on minSdk 29 can compare against the literal values (`"missed_call"`, `"voicemail"`, …) without a version guard; only the constants are gated.

## 2. Deterministic grouping and urgency, built on the above

### 2.1 Signals the platform already computes — do not re-derive

| Signal | Where | Level | What it saves you |
|---|---|---|---|
| Global ranking order | `RankingMap.getOrderedKeys()`, `Ranking.getRank()` | 21 | The system's own ordering across all apps, incorporating channel importance, recency, interaction history, assistant adjustments. |
| Effective importance | `Ranking.getImportance()` | 24 | Post-user-override channel importance. `NotificationChannel.getImportance()` is the channel's; `Ranking`'s is the one that actually applied. |
| Whether the user overrode importance | `Ranking.getImportanceExplanation()` non-null | 24 | Direct evidence of an explicit user preference on this app/channel — a strong, free salience signal. |
| Grouping | `sbn.getGroupKey()`, `Ranking.getOverrideGroupKey()`, `sbn.isGroup()`/`isAppGroup()` | 21/24/24/30 | Group membership including system auto-grouping (`"ranker_group"`). |
| Conversation-ness | `Ranking.isConversation()`, `Ranking.getConversationShortcutInfo()` | 31 | Whether the shade itself treats it as a conversation, and the identity behind it. |
| Ambient / low-attention | `Ranking.isAmbient()` | 21 | "a notification that doesn't require the user's immediate attention" — a ready-made demotion signal. |
| DND state per notification | `Ranking.matchesInterruptionFilter()`, `getSuppressedVisualEffects()` | 21/24 | Whether the user's current interruption filter let this through. |
| Whether it actually alerted | `Ranking.getLastAudiblyAlertedMillis()` | **29** | The device already knows which notifications made noise; this is the cleanest "was this interruptive?" bit available without heuristics. |
| Channel-level user sentiment | `Ranking.getUserSentiment()` | 28 | System's read on whether the user likes this channel. |
| Suspended / badge-able | `Ranking.isSuspended()`, `canShowBadge()` | 28/26 | Hide/demote candidates. |
| Removal cause | `onNotificationRemoved(..., reason)` | 26 | `REASON_CLICK` vs `REASON_CANCEL` vs `REASON_APP_CANCEL` vs `REASON_TIMEOUT` distinguishes *handled* from *dismissed* from *withdrawn* without inference. |
| Per-listener type filter | `META_DATA_DEFAULT_FILTER_TYPES` / `META_DATA_DISABLED_FILTER_TYPES` manifest meta-data, `migrateNotificationFilter()` | **31** | Ask the OS to only deliver `conversations|alerting` and never `ongoing|silent`, so the noise never reaches the app. |

`NotificationChannel` importance values ([NotificationManager](https://developer.android.com/reference/android/app/NotificationManager), all API 24) give a fixed ordinal ladder to bucket on: `IMPORTANCE_NONE` 0 ("does not show in the shade"), `IMPORTANCE_MIN` 1 ("only shows in the shade, below the fold"), `IMPORTANCE_LOW` 2 ("Shows in the shade, and potentially in the status bar …, but is not audibly intrusive"), `IMPORTANCE_DEFAULT` 3 ("shows everywhere, makes noise, but does not visually intrude"), `IMPORTANCE_HIGH` 4 ("shows everywhere, makes noise and peeks. May use full screen intents"), `IMPORTANCE_UNSPECIFIED` −1000 ("should never be associated with an actual notification").

### 2.2 Signals the app must derive itself

- **Conversation-ness on API 29–30.** `Ranking.isConversation()` is API 31. Below that, reproduce the platform's own definition from the conversations guide: `EXTRA_TEMPLATE == "android.app.Notification$MessagingStyle"` (or `EXTRA_MESSAGES` present) **and** `Notification.getShortcutId() != null`, with the documented fallbacks (`MessagingStyle` without shortcut; `category == "msg"`). `getShortcutId()` is API 26: "Returns the id that this notification supersedes, if any. Used by some Launchers that display notification content to hide shortcuts that duplicate notifications."
- **Conversation identity.** `getConversationShortcutInfo()` is API 31 and "might be null even if the notification is a conversation notification". A stable conversation key must be synthesised: prefer `shortcutId` → else `Person.getKey()` of the non-self participant(s) from `EXTRA_MESSAGES` senders / `EXTRA_PEOPLE_LIST` → else `EXTRA_CONVERSATION_TITLE` → else `(package, channelId, groupKey)`.
- **Sender salience.** Nothing on the platform ranks *people*. `Person.isImportant()` is an app claim. `EXTRA_IS_GROUP_CONVERSATION` distinguishes 1:1 from group. Anything beyond that (frequency, reciprocity, recency per sender) has to be accumulated locally from observed `getPostTime()` + sender key + removal reason.
- **Message-level deltas.** A `MessagingStyle` notification is re-posted in full on every new message; `EXTRA_MESSAGES` holds up to 25 messages. "New since last digest" requires diffing on `(senderKey, timestamp, text)` against a locally stored watermark. `Ranking.isTextChanged()` would answer this directly but is not public API.
- **Unread vs seen.** No platform read state exists for a listener. `setNotificationsShown(keys)` (API 23) is a *write*: "Inform the notification manager that these notifications have been viewed by the user. This should only be called when there is sufficient confidence that the user is looking at the notifications" — it does not report state back.
- **Per-app aggregation.** Grouping by `getPackageName()` is trivially deterministic but note `getOpPkg()` differs under notification delegation (API 29+); the app the user thinks of is `getPackageName()`.
- **Noise suppression.** Ongoing/service notifications must be filtered by `isOngoing()` / `FLAG_ONGOING_EVENT` / `FLAG_FOREGROUND_SERVICE` / `category ∈ {progress, service, transport, status, sys}` / `getChannelId()` importance ≤ `IMPORTANCE_LOW`. On API 31+ prefer pushing this into `META_DATA_DISABLED_FILTER_TYPES` so the events never arrive.
- **Deduplication across updates.** The same `key` is re-posted on every update; the digest's unit must be `key` with last-write-wins plus a first-seen timestamp, not one row per `onNotificationPosted`.
- **Group summary suppression.** A summary (`FLAG_GROUP_SUMMARY`) duplicates its children's content. Deterministic rule: within a `groupKey`, if ≥1 non-summary child is present, drop the summary; if the summary is the only member (or children were coalesced away with `REASON_GROUP_OPTIMIZATION`), keep it.

## 3. API-level differences across 29–36

| Level | Change | Effect on a listener | Source |
|---|---|---|---|
| 29 | Listeners unavailable on low-RAM devices | "Notification listeners cannot get notification access or be bound by the system on low-RAM devices running Android Q (and below)." | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| 29 | `Ranking.getSmartActions/getSmartReplies/getLastAudiblyAlertedMillis/canBubble/isSuspended`, `sbn.getOpPkg/getUid` become public | New signals, all optional | [Ranking](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking), [StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification) |
| 30 | Conversations shade section; `sbn.isAppGroup()`; `MessagingStyle.Message.getMessagesFromBundleArray()`; `Notification.getContextualActions()` | Conversation semantics become first-class; message parsing gets a helper | [conversations guide](https://developer.android.com/develop/ui/views/notifications/conversations), [StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification#isAppGroup()) |
| 31 | **Notification trampoline restriction** | "apps that target Android 12 or higher can't start activities from services or broadcast receivers that are used as notification trampolines. In other words, after the user taps on a notification, or an action button within the notification, your app cannot call `startActivity()` inside of a service or broadcast receiver." Logcat: "Indirect notification activity start (trampoline) from PACKAGE_NAME, this should be avoided for performance reasons." | [Android 12 behavior changes (targeting)](https://developer.android.com/about/versions/12/behavior-changes-12) |
| 31 | `Ranking.isConversation()`, `getConversationShortcutInfo()`, `getLockscreenVisibilityOverride()` | Conversation detection stops needing heuristics | [Ranking](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking) |
| 31 | Listener type filters | `META_DATA_DEFAULT_FILTER_TYPES` ("pipe separated list of default integer notification types or 'ongoing', 'conversations', 'alerting', or 'silent' … An absent value means 'allow all types'. A present but empty value means 'allow no types'."), `META_DATA_DISABLED_FILTER_TYPES` ("Types provided in this list will appear as 'off' and 'disabled' in the user interface"), and `migrateNotificationFilter(int, List<String>)` | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| 31 | New categories `missed_call`, `stopwatch`, `workout`, `location_sharing` | More precise deterministic classification | [Notification](https://developer.android.com/reference/android/app/Notification#CATEGORY_MISSED_CALL) |
| 33 | **`POST_NOTIFICATIONS` runtime permission** | "Android 13 (API level 33) introduces a runtime notification permission: `POST_NOTIFICATIONS`." This gates an app **posting** its own notifications; it does **not** gate listening. A digest app still needs it if the digest itself posts a notification, and fewer third-party notifications will exist overall to digest. | [Android 13 behavior changes (all apps)](https://developer.android.com/about/versions/13/behavior-changes-all) |
| 34 | `requestUnbind(ComponentName)` static overload; `META_DATA_DEFAULT_AUTOBIND` ("a boolean value that is used to decide if this listener should be automatically bound by default. If the value is 'false', the listener can be bound on demand using `requestRebind(ComponentName)`. An absent value means that the default is 'true'"); `REASON_LOCKDOWN` | Lets a listener opt out of always-on binding and bind on demand; lockdown mandates local deletion | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) |
| 34 | `FLAG_ONGOING_EVENT` notifications become user-dismissible | "If your app shows non-dismissable foreground notifications to users, Android 14 has changed the behavior to allow users to dismiss such notifications." Ongoing notifications now generate `REASON_CANCEL` removals they previously could not. | [Android 14 behavior changes (all apps)](https://developer.android.com/about/versions/14/behavior-changes-all) |
| **35** | **OTP redaction for untrusted listeners** | "Android will stop untrusted apps that implement a `NotificationListenerService` from reading unredacted content from notifications where an OTP has been detected. Trusted apps such as companion device manager associations are exempt from these restrictions." | [Android 15 behavior changes (all apps)](https://developer.android.com/about/versions/15/behavior-changes-all) |
| 35 | `CATEGORY_VOICEMAIL` | — | [Notification](https://developer.android.com/reference/android/app/Notification#CATEGORY_VOICEMAIL) |
| 36 | `FLAG_PROMOTED_ONGOING`, `Notification.hasPromotableCharacteristics()` | New "promoted ongoing" class of notification to recognise and exclude | [Notification](https://developer.android.com/reference/android/app/Notification#hasPromotableCharacteristics()) |
| **36.1** | `Ranking.getSummarization()`, `Notification.hasSummarizedContent()`, `REASON_BUNDLE_DISMISSED` ("Notification was canceled because it was in a bundle (e.g. `NotificationChannel#PROMOTIONS_ID`) that was dismissed") | The OS itself starts supplying model-generated summaries and bundling notifications into system-reserved channels; `getChannel()` may return a system classification channel rather than the app's | [Ranking](https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking#getSummarization()), [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService#REASON_BUNDLE_DISMISSED) |

The Android 15 redaction is the one that materially degrades a launcher's digest. AOSP shows the exact mechanism ([NotificationManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java)): `addToListIfNeeded` does `if (mListeners.hasSensitiveContent(r) && !mListeners.isUidTrusted(info.uid)) { notifications.add(mListeners.redactStatusBarNotification(sbn)); }`, and `redactStatusBarNotification` replaces the title with the app label, the text with `R.string.redacted_notification_message`, nulls sub-text, retitles all actions, and replaces `MessagingStyle`/`BigTextStyle` content wholesale, then removes `EXTRA_SUB_TEXT`, `EXTRA_TEXT_LINES`, `EXTRA_LARGE_ICON_BIG`. "Trusted" is defined by `isAppTrustedNotificationListenerService(uid, pkg)`: `RECEIVE_SENSITIVE_NOTIFICATIONS` permission granted, **or** `mPackageManagerInternal.isPlatformSigned(pkg)`, **or** `AppOpsManager` `OP_RECEIVE_SENSITIVE_NOTIFICATIONS` allowed, **or** a non-revoked CompanionDeviceManager association for that package. A third-party launcher meets none of these by default and is therefore untrusted — it will receive redacted content for any notification the assistant flags as containing an OTP, both in live callbacks and in `getActiveNotifications()`. Redaction is silent: there is no public API telling the listener that a given `StatusBarNotification` was redacted (`Ranking.hasSensitiveContent()` exists in AOSP but is not public).

Trampolines (31+) do not affect *receiving*, but they do affect a digest UI that lets the user act: tapping a digest row must fire the original notification's `contentIntent` `PendingIntent` directly, not route through a service or broadcast receiver of the launcher's own.

## 4. Reliability: disconnection, rebind, reboot, history

- **Disconnection is explicit and one-way.** `onListenerDisconnected()` (API 24): "You will not receive any events after this call, and may only call `requestRebind(ComponentName)` at this time." `requestUnbind()` (API 24): "Once this is called, you will no longer receive updates and no method calls are guaranteed to be successful, until you next receive the `onListenerConnected()` event. **The service will likely be killed by the system after this call.**"
- **Rebind after process death is delayed and single-shot.** From [ManagedServices.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/ManagedServices.java): `private static final int ON_BINDING_DIED_REBIND_DELAY_MS = 10000;` and `onBindingDied` → `unbindService(...)`, then `if (!mServicesRebinding.contains(servicesBindingTag)) { mServicesRebinding.add(...); mHandler.postDelayed(() -> reregisterService(name, userid), ON_BINDING_DIED_REBIND_DELAY_MS); } else { Slog.v(TAG, getCaption() + " not rebinding in user " + userid + " as a previous rebind attempt was made: " + name); }`. The flag is cleared only in `onServiceConnected` (`mServicesRebinding.remove(servicesBindingTag);`). So: one automatic retry after 10 s; if that retry also fails to connect, the system does **not** keep retrying — recovery then depends on the app calling `requestRebind(ComponentName)`, or on a `rebindServices()` trigger (user toggling access, package change, user switch, boot). Binding uses `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_ALLOW_WHITELIST_MANAGEMENT`.
- **`requestRebind` is the only safe call outside connection.** (`requestRebind(ComponentName)`, API 24: "Request that the listener be rebound, after a previous call to `requestUnbind()`. This method will fail for listeners that have not been granted the permission by the user.") It is a `static` method, so it can be called from anywhere in the app — e.g. from the launcher activity's `onResume` as a self-heal.
- **Reboot loses everything.** `getActiveNotificationsFromListener` reads `mNotificationList`, declared in [NotificationManagerService.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java) as `final ArrayList<NotificationRecord> mNotificationList = new ArrayList<>();` — an in-memory list with no restore path at boot. Posted notifications are not persisted across reboot; only snoozed records (`SnoozeHelper`) and the system's own `NotificationHistoryManager` store survive, and `NotificationHistory` has **no public API** (it is absent from the public [NotificationManager](https://developer.android.com/reference/android/app/NotificationManager) reference).
- **`getActiveNotifications()` is a live snapshot, not history.** "Request the list of outstanding notifications (that is, those that are visible to the current user)." Anything the user or the app already dismissed is gone. There is no lookback, no time-range query, no count limit documented — but per-package the system never holds more than `MAX_PACKAGE_NOTIFICATIONS = 50`.
- **Ranking snapshots go stale immediately.** "`Ranking` objects aren't automatically updated as notification events occur"; "this object represents a ranking snapshot that only applies to notifications active at the time of retrieval." Ranking must be read from the `RankingMap` handed to the current callback, never cached.
- **Consequence:** a digest that spans more than the current shade contents *must* maintain its own local store, written on every `onNotificationPosted`, and reconciled against `getActiveNotifications()` at every `onListenerConnected()` (which is also the only reliable point to detect what was missed while disconnected — by key set difference).
- **Work profiles and multi-user.** "The system also ignores notification listeners running in a work profile. A `DevicePolicyManager` might block notifications originating from a work profile." `sbn.getUser()` must be carried through so profile notifications are not merged into the personal digest.

## 5. Storage and policy constraints

### 5.1 Play does not list notification access as a restricted permission — but the definition reaches it

[Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170) has no Notification Access section; its sections are Restricted Permissions, minimum-scope alternatives, Photo/Video, SMS and Call Log, Location, All Files Access, Package Visibility, Accessibility API, Request Install Packages, Body Sensors, Health Connect, VPN Service, Exact Alarm, Full-Screen Intent, Age Signals. There is likewise no notification-listener article among the Play Console "Use of app permissions and APIs" declaration-form articles, and no occurrence of "notification" in the [Developer Policy Center](https://play.google/developer-content-policy/) index.

However the definition is open-ended, verbatim: "In addition to the above, restricted permissions are permissions that are designated as **Dangerous, Special, Signature**, or as documented below. These permissions are subject to the following additional requirements and restrictions: User or device data accessed through Restricted Permissions is considered as personal and sensitive user data. **The requirements of the User Data policy apply.**" `BIND_NOTIFICATION_LISTENER_SERVICE` is signature-protected and notification access is a Settings-granted special app access, so it falls inside that wording. Treat the User Data policy as applying. (Google does not say this about notification access anywhere; it is what the definition's text covers.)

Top-level rule, verbatim: "You may only request permissions and APIs that access sensitive information that are necessary to implement current features or services in your app that are promoted in your Google Play listing. You may not use permissions or APIs that access sensitive information that give access to user or device data for undisclosed, unimplemented, or disallowed features or purposes. **Personal or sensitive data accessed through permissions or APIs that access sensitive information may never be sold nor shared for a purpose facilitating sale.**"

The declaration form itself is triggered by an enumerated list that does not include notification access — [Declare permissions](https://support.google.com/googleplay/android-developer/answer/9214102): "If your app requests the use of high-risk or sensitive permissions (for example, SMS or Call Log), you may be required to complete the Permissions Declaration Form and receive approval from Google Play. … You must specify your app's core functionality from the list of supported use cases."

### 5.2 Prominent disclosure is required

[User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), verbatim trigger: "In cases where your app's access, collection, use, or sharing of personal and sensitive user data may not be within the reasonable expectation of the user of the product or feature in question (**for example, if data collection occurs in the background when the user is not engaging with your app**), you must meet the following requirements". A notification listener is background collection by construction.

The requirement, verbatim: "**Prominent disclosure:** You must provide an in-app disclosure of your data access, collection, use, and sharing. The in-app disclosure: Must be within the app itself, not only in the app description or on a website; Must be displayed in the normal usage of the app and not require the user to navigate into a menu or settings; Must describe the data being accessed or collected; Must explain how the data will be used and/or shared; Cannot only be placed in a privacy policy or terms of service; and Cannot be included with other disclosures unrelated to personal and sensitive user data collection."

And: "**Consent and runtime permissions:** Requests for in-app user consent and runtime permission requests must be immediately preceded by an in-app disclosure that meets the requirement of this policy. The app's request for consent: Must present the consent dialog clearly and unambiguously; Must require affirmative user action (for example, tap to accept, tick a check-box); Must not interpret navigation away from the disclosure (including tapping away or pressing the back or home button) as consent; Must not use auto-dismissing or expiring messages as a means of obtaining user consent; and **Must be granted by the user before your app can begin to collect or access the personal and sensitive user data.**" Recommended wording, verbatim: "'[This app] collects/transmits/syncs/stores [type of data] to enable ["feature"], [in what scenario].'"

Named common violation, verbatim: "An app that uses restricted permissions in the background of the app including for tracking, research, or marketing purposes and does not comprehensively disclose its use and obtain consent in accordance with the above requirements."

The definition of personal and sensitive data is non-exhaustive and does not enumerate notification content: "Personal and sensitive user data includes, but isn't limited to, personally identifiable information, financial and payment information, authentication information, phonebook, contacts, device location, SMS and call-related data, health data, Health Connect data, inventory of other apps on the device, microphone, camera, and other sensitive device or usage data."

### 5.3 A purely local store is explicitly outside "collection"

[Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469), verbatim: "**Data collection** — 'Collect' means transmitting data from your app off a user's device." And: "**Not in scope for data collection** — The following use cases do not need to be disclosed as collected: **On-device access/processing:** User data accessed by your app that is only processed locally on the user's device and not sent off device does **not** need to be disclosed."

Two caveats that bear directly on a zero-analytics, zero-cloud product:

- On-device transfer still counts as *sharing*, verbatim: "'Sharing' refers to transferring user data collected from your app to a third party. This includes user data transferred: … **On-device transfer to another app.** Transferring user data from your app to another app directly on the device. In this case, you must disclose data sharing in your Data safety section declarations even if your app does not transmit the data off the user's device." Any share sheet, intent hand-off, or export of digest content out of the app is a disclosable share.
- The form is still mandatory, verbatim: "Even developers with apps that do not collect any user data must complete this form and provide a link to their privacy policy. In this case, the completed form and privacy policy can indicate that no user data is collected or shared."

There is no "notifications" data type in the Data safety taxonomy; the nearest are "Messages — Emails / SMS or MMS / Other in-app messages" and "Other user-generated content". No Google statement maps notification content to a specific type; that mapping only matters if content ever leaves the device.

### 5.4 The SMS/Call Log clause is the sharpest edge for a launcher

[Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170), SMS and Call Log section, verbatim: "Apps may only use the permission (and any data derived from the permission) to provide approved core app functionality … **You may not use alternative methods (including other permissions, APIs, or third-party sources) to derive data attributed to Call Log or SMS related permissions.**"

[Use of SMS or Call Log permission groups](https://support.google.com/googleplay/android-developer/answer/10208820) lists invalid use cases including, verbatim: "**SMS or calls appearing in wallpaper, launcher, and other tools**" and "**SMS or phone notification enhancement and alerts (when the app is not the default handler or an eligible exception)**", closing with "Note: This list is not exhaustive."

Those clauses are written against the SMS/Call Log *permissions*, not notification access. But a launcher digest that surfaces the body of an SMS app's or dialer's notification is reading SMS/call data by an alternative method and is describable by both named invalid use cases. There is no published carve-out for reading it via `NotificationListenerService`.

Other policies that touch a notification surface: [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379), verbatim — "Apps that circumvent Android sandbox protections in order to derive user activity or user identity from other apps" and "For security and privacy purposes, all apps distributed on Google Play are required to respect the `FLAG_SECURE` declaration of other apps." [Stalkerware](https://support.google.com/googleplay/android-developer/answer/9888380), verbatim — "Code that collects personal or sensitive user data from a device and transmits the data to a third party (enterprise or another individual) for monitoring purposes"; a local, single-user, non-transmitting digest does not meet that definition (no transmission, no third party).

There is **no** published list of acceptable use cases for notification access, and a launcher is not named as one. The only appearance of "launcher" in this policy neighbourhood is the negative one above.

## 6. Facts bearing on the decision

Capability:

1. A fully deterministic digest is buildable at minSdk 29 from `sbn.getKey/getPackageName/getGroupKey/getPostTime`, `Notification.extras` (`EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_MESSAGES`, `EXTRA_TEMPLATE`, `EXTRA_PEOPLE_LIST`), `Notification.category`, `flags`, `getChannelId`/`getGroup`/`getSortKey`/`getShortcutId`, and `Ranking` (`getRank`, `getImportance`, `getImportanceExplanation`, `isAmbient`, `getOverrideGroupKey`, `matchesInterruptionFilter`, `getLastAudiblyAlertedMillis`, `getChannel`, `getUserSentiment`).
2. The platform already computes: global rank order, post-override importance, whether the user overrode importance, group membership including auto-grouping, DND match, whether the notification audibly alerted, channel sentiment, suspension — none of these need re-derivation.
3. The platform does **not** compute, at any level: unread/seen state, per-sender salience, message-level deltas across re-posts, or a "new since last digest" boundary. Those are the app's, and they need a local store.
4. Conversation-ness is free from API 31 (`Ranking.isConversation()`, `getConversationShortcutInfo()`); on 29–30 it must be reconstructed from `MessagingStyle` + `shortcutId` + `category == "msg"`, exactly as the conversations guide defines it.
5. Hard content caps: title/text/subtext truncated at 1024 chars by `Notification.safeCharSequence`; ≤25 retained `MessagingStyle` messages; ≤3 actions; ≤50 active notifications per package.
6. From API 31, `META_DATA_DEFAULT_FILTER_TYPES` / `META_DATA_DISABLED_FILTER_TYPES` can push ongoing/silent filtering into the OS so those events never reach the process.
7. From version 36.1 the OS itself supplies `Ranking.getSummarization()` — a model-generated summary produced by the system assistant, available for free, present only on 36.1+ and only where an assistant supplies it.

Reliability:

8. `getActiveNotifications()` is a live snapshot of the shade. Notifications are not persisted across reboot (`mNotificationList` is a plain in-memory `ArrayList` with no boot restore), and `NotificationHistory` has no public API. Any digest wider than "what is on screen now" requires the app's own store.
9. Automatic rebind after binding death happens once, after 10 s (`ON_BINDING_DIED_REBIND_DELAY_MS`); the retry flag clears only on successful connect. `requestRebind(ComponentName)` is static and is the only safe call while disconnected — a self-heal call from the launcher activity is the practical mitigation.
10. Every callback runs on the main thread from API 24 onward.
11. `Ranking` snapshots go stale the instant they are taken and must be re-read from the current `RankingMap`.
12. Listeners do not work on low-RAM devices running API 29 or below, and are ignored inside work profiles.
13. `onNotificationRemoved(..., reason)` (API 26+) distinguishes user-click from user-dismiss from app-withdrawal from timeout without inference — the cleanest available "has the user dealt with this" signal. `REASON_LOCKDOWN` (34) obliges the listener to delete its local copy: "All the listeners shall ensure the canceled notifications are indeed removed on their end to prevent data leaking."

Degradation and policy:

14. On Android 15+, an untrusted listener — which a third-party launcher is, lacking `RECEIVE_SENSITIVE_NOTIFICATIONS`, platform signature, the `OP_RECEIVE_SENSITIVE_NOTIFICATIONS` app-op, or a CDM association — receives content-redacted notifications where the assistant detected an OTP, with no public API to detect that redaction occurred.
15. Notification access is not a Play-listed restricted permission and has no declaration form or approved-use-case list; a launcher is not named as an accepted use case anywhere.
16. Play's User Data prominent-disclosure and consent requirements apply and are unconditional for background collection: in-app disclosure shown in normal usage (not in a menu, not only in a privacy policy), followed immediately by affirmative consent, **before** any access begins.
17. A purely local store is explicitly out of scope for Data safety "collection" ("'Collect' means transmitting data from your app off a user's device"; "On-device access/processing … does not need to be disclosed"). The Data safety form is still mandatory and may state that nothing is collected. Any on-device hand-off of notification content to another app is disclosable *sharing*, even without leaving the device.
18. Surfacing SMS or call notification content in a launcher is described by two named invalid use cases in the SMS/Call Log policy ("SMS or calls appearing in wallpaper, launcher, and other tools"; "SMS or phone notification enhancement and alerts") plus the "no alternative methods to derive data attributed to Call Log or SMS related permissions" clause. Those clauses are scoped to the SMS/Call Log permissions and there is no published statement extending or exempting `NotificationListenerService`.
19. Trampoline restriction (target 31+): a digest row's tap must fire the original `contentIntent` `PendingIntent` directly; routing it through the launcher's own service or broadcast receiver is blocked.
20. `POST_NOTIFICATIONS` (33+) gates posting, not listening — it matters only if the digest itself posts a notification.

## Sources

- https://developer.android.com/reference/android/service/notification/NotificationListenerService
- https://developer.android.com/reference/android/service/notification/NotificationListenerService.Ranking
- https://developer.android.com/reference/android/service/notification/NotificationListenerService.RankingMap
- https://developer.android.com/reference/android/service/notification/StatusBarNotification
- https://developer.android.com/reference/android/app/Notification
- https://developer.android.com/reference/android/app/Notification.MessagingStyle
- https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message
- https://developer.android.com/reference/android/app/Person
- https://developer.android.com/reference/android/app/NotificationManager
- https://developer.android.com/develop/ui/views/notifications/conversations
- https://developer.android.com/develop/ui/views/notifications/group
- https://developer.android.com/about/versions/12/behavior-changes-12
- https://developer.android.com/about/versions/13/behavior-changes-all
- https://developer.android.com/about/versions/14/behavior-changes-all
- https://developer.android.com/about/versions/15/behavior-changes-all
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationManagerService.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/ManagedServices.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/GroupHelper.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/notification/NotificationRecord.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Notification.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/values/config.xml
- https://support.google.com/googleplay/android-developer/answer/9888170
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10208820
- https://support.google.com/googleplay/android-developer/answer/9888379
- https://support.google.com/googleplay/android-developer/answer/9888380
- https://support.google.com/googleplay/android-developer/answer/9214102
- https://play.google/developer-content-policy/
