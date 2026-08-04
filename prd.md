# Bodha — Technical Product Requirements Document

**Status:** Draft v1.0  
**Product:** Bodha — Intentional Android Launcher  
**Platform:** Android  
**Primary stack:** Kotlin + Jetpack Compose  
**Last updated:** 4 August 2026

---

## 1. Product Summary

Bodha is an Android launcher designed to turn unconscious smartphone use into intentional action.

It occupies the middle ground between:

- restrictive minimalist or “dumb phone” launchers; and
- conventional launchers designed around app visibility, notifications, engagement and consumption.

Bodha does not shame, block or control the user. It creates small moments of awareness, reduces unnecessary choices, surfaces relevant actions and helps users complete tasks faster.

### Core promise

> A phone that helps you remember why you picked it up.

### Product loop

> Unlock → remember intention → act quickly → leave the phone → review without judgment.

---

## 2. Goals

### 2.1 Product goals

1. Reduce unconscious opens, repeated app switching and reflexive checking.
2. Increase the percentage of phone sessions that begin with a clear intention.
3. Reduce time required to complete useful actions.
4. Preserve user agency and access to all installed apps.
5. Provide calm, factual awareness instead of judgment or gamification.
6. Make AI useful by removing steps rather than generating more content.
7. Keep sensitive behavioral data local by default.

### 2.2 Business goals

1. Ship a credible paid Android launcher with a free core experience.
2. Support subscriptions and lifetime access through Google Play and RevenueCat.
3. Avoid backend and integration complexity that does not directly improve intentional use.
4. Establish Bodha as a premium, privacy-respecting consumer technology brand.

### 2.3 Non-goals

Bodha will not:

- become a parental-control or device-management product;
- forcibly block applications by default;
- use streaks, points, trees, pets or productivity scores;
- become a general-purpose AI chatbot;
- ingest the user’s entire email, Drive, Slack or Notion history;
- require an account for core launcher functionality;
- provide an iOS launcher equivalent;
- replace the Android lock screen;
- optimize for time spent inside Bodha.

---

## 3. Target Users

### Primary user

A smartphone-dependent knowledge worker who:

- needs maps, messaging, calendar, media, camera and work apps;
- does not want a dumb phone;
- notices reflexive app opening and fragmented attention;
- values design, privacy and personal agency;
- will pay for a refined tool that improves daily behavior without moralizing.

### Secondary users

- students seeking calmer study sessions;
- creators and founders managing fragmented work;
- digital-minimalism users dissatisfied with crude launchers;
- users who want selective friction for distracting apps.

---

## 4. Product Principles

Every feature must satisfy at least one principle.

### 4.1 Agency over enforcement

The user always retains access and final control.

### 4.2 Awareness over guilt

Show what happened. Do not tell the user how to feel.

### 4.3 Friction only where useful

Do not slow down camera, calls, maps, authentication, emergencies or essential utilities.

### 4.4 Local before cloud

Sensitive behavioral data stays on device unless the user explicitly enables a cloud-dependent feature.

### 4.5 Fewer, better surfaces

No infinite feeds. No excessive dashboards. No suggestion overload.

### 4.6 Explainable intelligence

Every inferred suggestion must support “Why am I seeing this?” and source controls.

### 4.7 Beauty is functional

Calm typography, spacing and motion are part of the behavioral intervention.

---

## 5. Platform and Technical Decisions

### 5.1 Native Android

Use:

- Kotlin
- Jetpack Compose
- Android ViewModel
- Coroutines and Flow
- Room
- DataStore
- WorkManager
- Hilt
- Navigation Compose
- Baseline Profiles
- Macrobenchmark

Do not use Kotlin Multiplatform for v1. Bodha’s core capabilities depend on Android launcher roles, package discovery, usage access, notifications, contacts, calendar providers, app shortcuts and system settings.

### 5.2 Minimum Android version

**Recommended initial minimum:** Android 10 / API 29.

Reasons:

- broad enough device coverage;
- modern permission and background-execution behavior;
- fewer compatibility branches than older Android versions.

Reassess before implementation based on target-market device distribution and required APIs.

### 5.3 Launcher role

Bodha must declare a Home activity using `ACTION_MAIN`, `CATEGORY_HOME` and `CATEGORY_DEFAULT`, then guide the user through selecting Bodha as the default Home app.

Use `LauncherApps` and package APIs to discover launchable activities and shortcuts.

### 5.4 Architecture

Use a modular, layered architecture with unidirectional data flow.

```text
app
├── core:model
├── core:common
├── core:designsystem
├── core:database
├── core:datastore
├── core:system
├── core:search
├── core:recommendation
├── core:ai
├── core:billing
├── core:auth
├── core:network
├── core:analytics
├── feature:onboarding
├── feature:home
├── feature:intent
├── feature:today
├── feature:search
├── feature:library
├── feature:opencheck
├── feature:focus
├── feature:notifications
├── feature:awareness
├── feature:reflection
├── feature:context
├── feature:settings
└── feature:subscription
```

### 5.5 State model

Each feature exposes:

- immutable `UiState`;
- user and system `UiAction` events;
- one-way state updates through a ViewModel;
- repositories that isolate local, Android-system and network sources.

Avoid feature-to-feature direct dependencies. Shared domain concepts belong in `core:model` or dedicated domain modules.

---

## 6. Screen Requirements

## 6.1 Home

### Purpose

Provide the smallest useful set of actions for the current moment.

### Elements

- large time and date;
- daily intention;
- current context label;
- maximum four suggested or pinned actions;
- calm notification summary;
- active focus indicator;
- universal search field.

### Gestures

- swipe down: Search;
- swipe up: App Library;
- swipe left: Awareness;
- swipe right: Today;
- double-tap empty area: lock device;
- long-press empty area: edit Home;
- long-press suggestion: pin, hide or explain.

### Rules

- user-pinned items outrank inferred suggestions;
- no app grid;
- no red badges;
- no animated attention cues;
- no more than four primary actions;
- Home must render from local state without network access.

### Acceptance criteria

- cold-start Home is interactive within the agreed startup performance budget;
- app suggestions never prevent access to Search or Library;
- Home remains useful with every optional permission denied.

---

## 6.2 Intent Prompt

### Purpose

Create a brief moment of awareness before action.

### Presentation

Use a dismissible bottom sheet rather than a blocking full-screen dialog.

### Options

- Continue something
- Communicate
- Capture
- Find something
- Browse
- Just looking
- Optional free-text intention

### Trigger engine

Support configurable triggers:

- selected unlocks;
- repeated unlocks within a time window;
- no active task or intent;
- opening a user-selected distracting app;
- explicit “ask every time” mode.

Suppress the prompt when:

- a call is active or incoming;
- navigation is active;
- camera was launched through a system shortcut;
- an emergency or utility flow is detected;
- the user is returning to an active focus task.

### Data

Store:

- selected intent category;
- optional user text;
- timestamp;
- source trigger;
- linked session ID.

---

## 6.3 Today

### Purpose

Show what matters immediately without becoming a task manager.

### Elements

- daily intention;
- next calendar event;
- one next task;
- active focus session;
- time-sensitive item;
- resume cards;
- maximum six cards.

### Gestures

- swipe right on card: complete or dismiss;
- swipe left: snooze;
- long-press: pin, hide or manage source;
- tap source/time: deep-link to source app.

### Sources

MVP:

- Calendar Provider;
- local Bodha intentions;
- recently opened documents known to Bodha;
- active focus session;
- notification-derived actions.

Later:

- Todoist;
- Home Assistant;
- optional Google Calendar API.

---

## 6.4 Universal Search

### Purpose

Become the primary way to navigate and act.

### Search domains

- installed apps;
- launcher shortcuts;
- contacts;
- calendar events;
- Android settings actions;
- files explicitly opened through Bodha;
- focus actions;
- local intentions and recent tasks;
- supported connector actions;
- optional web search fallback.

### Ranking order

1. exact text matches;
2. explicit user defaults and pins;
3. contacts and immediate actions;
4. contextual relevance;
5. recency and frequency;
6. inferred suggestions;
7. cloud or web results.

### Requirements

- local results appear immediately;
- network results never block local results;
- no sponsored content;
- no public search-history screen by default;
- user can hide or set defaults per result;
- result explanations available for inferred ranking.

### Performance target

Local search should feel instantaneous. Define a measured p95 query-to-first-result target during implementation and test on a mid-range device.

---

## 6.5 App Library

### Purpose

Ensure complete, predictable access to every launchable app.

### Default layout

Alphabetical text-first list.

### Optional layouts

- compact icons;
- categories;
- recent apps;
- user groups.

### App actions sheet

- Open
- Launch shortcut
- Pin
- Hide
- Pause
- Set Open Check
- App info

### Requirements

- support work profiles where Android permits;
- handle package install, update, removal and profile changes;
- app metadata cache updates incrementally;
- hidden apps remain searchable when configured.

---

## 6.6 Open Check

### Purpose

Add deliberate, user-controlled friction before selected applications.

### Trigger modes

- always;
- repeated opening threshold;
- during Focus;
- after daily usage threshold;
- within a user-defined schedule;
- never.

### Elements

- app name and icon;
- last opened time;
- today’s use duration;
- optional intention field;
- Open;
- Open for a selected duration;
- Go back.

### Principles

- no countdown before opening;
- no guilt language;
- no red warning state;
- Back dismisses immediately;
- emergency and utility apps bypass checks by default;
- user can always continue.

### Timed session ending

When a temporary session ends, show:

- Close app;
- Add five minutes;
- Continue without timer.

Implementation must respect Android platform limitations. Bodha may guide, overlay where policy-compliant, or bring the launcher forward when possible, but must not claim absolute app blocking.

---

## 6.7 Focus

### Purpose

Provide a calm working state with reduced choice.

### Setup

- activity label;
- duration;
- selected allowed apps;
- optional linked task;
- optional end condition, such as next meeting.

### Active screen

- task name;
- remaining time;
- Continue;
- Search;
- Pause;
- End;
- allowed apps.

### Behavior

- Home returns to Focus while session is active;
- non-allowed apps use Open Check rather than hard blocking;
- interruption count may be recorded locally;
- no streak or focus score.

---

## 6.8 Notifications

### Purpose

Turn a notification stream into a calm, actionable inbox.

### Source

Use `NotificationListenerService` only after explicit permission and a clear explanation.

### Sections

- People
- Time-sensitive
- Work
- Updates
- Silent

### Actions

- open original;
- mark handled locally;
- snooze locally;
- mute source in Bodha;
- open system notification settings.

### Intelligence

On-device logic may:

- group duplicates;
- extract sender and app;
- detect likely action language;
- suppress repeated low-value updates;
- identify time-sensitive calendar and travel items.

Cloud processing is opt-in and must send only the minimum selected content.

### Restrictions

- no automatic replies;
- no bulk server upload of notification history;
- inferred urgency must be explainable;
- original notifications remain accessible.

---

## 6.9 Awareness

### Purpose

Show a neutral record of behavior.

### Views

- Today
- Week
- App
- Session
- Intent

### Data shown

- session timeline;
- app transitions;
- unlock count when reliably available;
- foreground duration estimates;
- repeated opens;
- short sessions;
- intentional versus unclassified sessions.

### Rules

- no score;
- no ranking against other users;
- no shame or celebration copy;
- raw event data available behind summaries;
- user can exclude apps or sessions;
- retention period configurable.

---

## 6.10 Reflection

### Purpose

Offer one short, useful observation about the day or week.

### Elements

- one primary observation;
- up to two supporting facts;
- Review timeline;
- Adjust one habit;
- Add a note;
- Dismiss.

### Frequency

- daily;
- weekly;
- never.

Do not send a reflection notification unless the user explicitly enables it.

---

## 6.11 AI Assist

### Product rule

AI appears as compact actions and observations, not as a permanent chatbot destination.

### Initial capabilities

- resume unfinished work;
- summarize notification groups;
- detect repeated app switching;
- propose an immediate action before a meeting;
- generate a concise reflection from structured local metrics;
- classify search and intent queries;
- convert a command into an Android action when deterministic.

### Explainability

Every suggestion supports:

- Why am I seeing this?
- Useful
- Not useful
- Never suggest this
- Manage data source

### AI execution order

1. deterministic rule;
2. local heuristic;
3. on-device model where available;
4. cloud model only with consent and minimal context.

### Prohibited behavior

- inventing actions that cannot be completed;
- sending messages without explicit confirmation;
- silently reading broad personal datasets;
- using generated content to increase engagement;
- treating model output as authoritative urgency.

---

## 6.12 Context

### Built-in contexts

- Automatic
- Work
- Home
- Commute
- Travel
- Evening
- Focus
- Sleep
- Custom

### Inputs

- time;
- Calendar Provider;
- location, optional;
- connected Bluetooth device;
- current media session;
- active focus state;
- recent app usage;
- manual override.

### Rules

- manual selection temporarily overrides automatic inference;
- context inference must be visible and reversible;
- location is opt-in and must support coarse-location mode;
- no background-location request for MVP unless a validated use case requires it.

---

## 6.13 Settings

### Sections

- Appearance
- Home
- Gestures
- Intentionality
- Focus
- Notifications
- Awareness
- Intelligence
- Integrations
- Privacy and data
- Account and sync
- Subscription
- About

### Requirements

- searchable settings;
- permission state visible;
- each permission includes purpose and consequence;
- destructive actions require confirmation;
- export and delete controls are easy to find.

---

## 7. Global Navigation and Gestures

| Gesture | Default action |
|---|---|
| Swipe down | Search |
| Swipe up | App Library |
| Swipe left | Awareness |
| Swipe right | Today |
| Double-tap Home | Lock device |
| Long-press Home | Edit Home |
| Back | Return or dismiss |
| Pinch Home | Layout editing |

All gestures except Android system gestures must be remappable.

Use bottom sheets for:

- app actions;
- intent selection;
- focus setup;
- Open Check;
- suggestion explanation;
- context selection;
- snooze duration;
- permission explanation;
- connector setup.

Avoid nested modal stacks.

---

## 8. Onboarding

Maximum five primary steps.

1. **Promise**  
   “A phone that helps you remember why you picked it up.”

2. **Choose essentials**  
   Select four to eight important apps.

3. **Choose friction**  
   Select apps that should use Open Check.

4. **Permissions**  
   Explain each optional permission before opening the Android system screen.

5. **First intention**  
   Ask what matters today.

Then guide the user to select Bodha as the default Home app.

### Onboarding rules

- account creation is optional;
- permissions are requested progressively;
- denial never blocks core launcher use;
- no subscription paywall before the user has experienced core value;
- permission copy must describe local versus cloud processing.

---

## 9. Permissions and Android Capabilities

### Core

- Home role / launcher selection
- Query launchable applications within Play policy constraints
- Package change callbacks
- Launcher shortcuts

### Optional

- Contacts: contact search and direct communication actions
- Calendar Provider: next-event surfaces
- Notification access: grouped notification inbox
- Usage access: awareness and repeated-open detection
- Location: context inference
- Storage Access Framework: user-selected document resume
- Microphone: only for an explicit voice command feature

### Permission strategy

For every permission:

1. show a Bodha explanation screen;
2. state exact data accessed;
3. state whether processing is local;
4. state what feature is unavailable without it;
5. open the relevant Android system screen;
6. handle denial without repeated nagging.

Avoid Accessibility Service in the MVP unless a necessary feature cannot be implemented with safer platform APIs and the use complies with Google Play policy.

---

## 10. Local Data Model

Suggested Room entities:

```text
AppEntity
AppShortcutEntity
UserPinEntity
HiddenAppEntity
IntentSessionEntity
PhoneSessionEntity
AppUsageEventEntity
FocusSessionEntity
FocusAllowedAppEntity
OpenCheckRuleEntity
TimedAppSessionEntity
NotificationSnapshotEntity
NotificationGroupEntity
CalendarCacheEntity
ResumeItemEntity
ContextObservationEntity
ContextOverrideEntity
SuggestionEntity
SuggestionFeedbackEntity
ReflectionEntity
IntegrationAccountEntity
SyncEnvelopeEntity
```

### Data retention defaults

- raw usage events: 30 days;
- aggregated usage: 12 months;
- notification content: shortest practical retention, default seven days or less;
- reflections: retained until user deletes;
- search queries: not persistently stored by default;
- AI request payloads: not stored locally beyond debugging-safe metadata unless user opts in.

Retention must be configurable.

### DataStore

Use DataStore for:

- appearance preferences;
- gesture mappings;
- feature toggles;
- prompt frequency;
- privacy settings;
- permission education state;
- active context and lightweight state.

Use Room for relational and historical data.

---

## 11. Search and Recommendation Engine

### 11.1 Search index

Build a local index containing:

- app labels and package names;
- app shortcuts;
- contact names and normalized phone/email fields;
- calendar titles and participants where permission allows;
- supported settings actions;
- Bodha actions;
- user-selected documents;
- connector items.

### 11.2 Suggestion pipeline

```text
Candidate sources
→ eligibility filters
→ safety/permission filters
→ deterministic relevance
→ user pins and suppressions
→ contextual scoring
→ diversity limit
→ explanation generation
→ maximum four Home suggestions
```

### 11.3 Initial scoring inputs

- time of day;
- day of week;
- current context;
- next calendar event;
- recency;
- frequency;
- previous intent category;
- focus state;
- connected device;
- user pins;
- negative feedback.

Do not use opaque cloud ranking for MVP.

---

## 12. Integrations

## 12.1 Native-first integrations

Ship first:

- Calendar Provider;
- Notification Listener;
- Contacts Provider;
- Launcher shortcuts and deep links;
- UsageStatsManager;
- media sessions;
- Storage Access Framework.

## 12.2 External connectors

### Todoist — later

Read-only initial scope:

- today and overdue tasks;
- next task;
- complete task;
- deep-link to source.

### Home Assistant — later

User-selected entities only:

- scripts;
- scenes;
- switches;
- location-relevant controls.

### Google Calendar API — optional later

Use only when device Calendar Provider is insufficient for a validated cross-device or write-action requirement.

### Deferred

- Gmail API;
- Google Drive broad indexing;
- Slack API;
- Notion;
- TickTick.

For deferred products, begin with notification summaries, app shortcuts, deep links or user-selected files instead of full OAuth ingestion.

### Connector interface

```kotlin
interface ContextSource {
    val id: String
    suspend fun observeCandidates(context: UserContext): List<SuggestionCandidate>
}
```

Each connector must be:

- independently enabled;
- revocable;
- read-only by default;
- locally cached minimally;
- explicit about permissions and retention.

---

## 13. Authentication

Authentication is optional and required only for:

- encrypted backup and sync;
- account-level subscription association where needed;
- cloud AI quota or paid features;
- future multi-device preferences.

### Method

Use Sign in with Google through Android Credential Manager.

### Backend flow

1. Android obtains a Google ID token.
2. Backend validates issuer, signature, audience and expiration.
3. Backend creates or resolves a Bodha account.
4. Backend issues a short-lived Bodha access token and rotating refresh token.
5. Tokens are stored using Android secure storage practices.

Do not add a general authentication framework solely for one OAuth provider.

### Account deletion

Provide in-app deletion that removes:

- Bodha account;
- server metadata;
- encrypted backups;
- refresh tokens;
- connector tokens.

Local behavioral data deletion remains a separate clear option.

---

## 14. Billing and Entitlements

Use RevenueCat with Google Play Billing.

### Initial entitlement

```text
bodha_pro
```

### Candidate products

- monthly subscription;
- annual subscription;
- optional lifetime non-consumable purchase.

Exact packaging and price are separate product decisions.

### RevenueCat responsibilities

- purchase flow integration;
- entitlement state;
- restoration;
- subscription lifecycle;
- grace periods and refunds;
- offerings and paywall experiments if later required.

### App behavior

- cache entitlement state for offline use;
- never block the launcher because RevenueCat is temporarily unavailable;
- free core functions remain stable;
- restore purchases is always visible;
- paywall explains value without urgency manipulation.

### Possible free versus Pro boundary

**Free:**

- Home;
- Search;
- App Library;
- basic gestures;
- limited Open Check rules;
- basic Focus;
- seven-day Awareness view.

**Pro:**

- unlimited Open Check rules;
- advanced context suggestions;
- notification summaries;
- longer Awareness history;
- reflections;
- advanced themes;
- optional encrypted sync;
- cloud AI features;
- external connectors.

This boundary must be validated through user testing.

---

## 15. Backend

### Principle

The core launcher works without a backend.

### Recommended services

- Cloudflare Workers + Hono: API
- D1: accounts, device metadata, entitlement mirror, sync metadata
- R2: encrypted backup blobs
- Queues: deferred AI or account jobs
- AI Gateway: cloud model routing and cost controls

Use Durable Objects only when a real coordination requirement appears.

### Backend responsibilities

- validate Google identity;
- issue Bodha tokens;
- associate optional account with RevenueCat identity;
- accept encrypted sync blobs;
- proxy explicit cloud-AI requests;
- remote configuration with safe local defaults;
- account deletion;
- minimal operational analytics.

### Backend exclusions

Do not store:

- full app-usage timelines;
- complete notification history;
- installed-app inventory unless strictly needed and explicitly enabled;
- contact database;
- raw calendar database;
- local search history.

---

## 16. Sync and Backup

Not required for MVP.

When added:

- client-side encrypt all behavioral and preference payloads;
- server stores opaque blobs;
- encryption keys must not be recoverable by the backend without an explicit recovery design;
- sync preferences before sensitive histories;
- conflict strategy must be deterministic and documented;
- users choose which categories sync.

Initial sync scope should be limited to:

- theme and appearance;
- pins and hidden apps by stable identifiers where possible;
- gesture settings;
- Open Check rules;
- Focus presets;
- connector configuration metadata.

---

## 17. Privacy and Security

### Privacy defaults

- local processing enabled;
- cloud AI disabled until explicitly selected;
- analytics minimal and opt-out available;
- no advertising SDK;
- no data sale;
- no cross-app behavioral profile on the server.

### Security controls

- TLS for all network traffic;
- encrypted token storage;
- short-lived access tokens;
- rotating refresh tokens;
- connector token revocation;
- strict backend schema validation;
- rate limiting;
- secrets kept server-side;
- no model API key embedded in client;
- dependency and supply-chain scanning;
- Play Integrity considered only for abuse-sensitive cloud endpoints, not core launcher use.

### Sensitive logging

Never log:

- notification body text;
- contact details;
- calendar titles;
- user intention text;
- search queries;
- OAuth tokens;
- AI payload content.

Provide a privacy dashboard showing:

- data stored locally;
- active permissions;
- active connectors;
- cloud features;
- export;
- delete local data;
- delete account.

---

## 18. Analytics

Measure product usefulness without reproducing surveillance.

### Allowed aggregate events

- onboarding step completed;
- permission enabled or skipped;
- Home rendered;
- Search used;
- Open Check displayed and selected outcome;
- Focus started, paused and completed;
- suggestion shown and feedback;
- paywall shown and purchase result;
- crash and performance metrics.

### Do not collect

- app names in analytics events;
- contact or calendar content;
- notification content;
- raw search terms;
- full behavioral timelines;
- user-entered intentions.

### Core product metrics

Prefer on-device calculation and user-visible metrics:

- intentional-session ratio;
- repeated-open frequency;
- median useful-action completion time;
- app-switching bursts;
- Focus completion;
- Open Check return rate;
- day-7 and day-30 launcher retention;
- free-to-paid conversion.

Avoid optimizing solely for daily active usage or time in app.

---

## 19. Design System

### Visual qualities

- calm;
- warm;
- precise;
- spacious;
- quietly intelligent.

### Rules

- warm neutral backgrounds;
- one muted accent per theme;
- text-first hierarchy;
- restrained iconography;
- soft depth, no gratuitous glassmorphism;
- red only for genuine errors or destructive actions;
- no bouncing or pulsing attention cues;
- most motion below 250 ms;
- reduced-motion support;
- subtle optional haptics;
- full dark theme;
- dynamic type support;
- accessibility contrast compliance.

### Typography

Use a high-quality serif for selected expressive headings and a highly legible sans-serif for controls and data. Ensure performance, licensing and multilingual coverage before final selection.

---

## 20. Accessibility

Requirements:

- screen-reader labels for every action;
- predictable focus order;
- large text and display scaling support;
- minimum touch-target compliance;
- color is never the only state indicator;
- reduced motion;
- high contrast mode compatibility;
- gesture alternatives for every core action;
- keyboard support where Android devices expose hardware keyboards;
- simple language and non-judgmental copy.

---

## 21. Performance and Reliability

### Required qualities

- Home must remain fast after device reboot;
- package and shortcut indexing runs incrementally;
- background jobs use WorkManager where appropriate;
- no continuous polling when callbacks or scheduled work suffice;
- offline-first operation;
- graceful behavior when permissions are revoked;
- battery usage must be measured on representative devices;
- launcher crashes must recover to a safe Home state.

### Testing

- unit tests for ranking, intent triggers and sessionization;
- Room migration tests;
- Compose UI tests for critical flows;
- integration tests for package events and permission state;
- screenshot tests for the design system;
- Macrobenchmark for startup, Home rendering, Search and Library scrolling;
- baseline profiles for startup-critical paths;
- OEM testing across Pixel, Samsung and at least one additional major Android vendor.

---

## 22. Release Scope

## Phase 0 — Prototype

- visual design system;
- launcher role and Home;
- app discovery and launch;
- Search for apps;
- App Library;
- basic gestures;
- static Today and Awareness prototypes.

## Phase 1 — Private Alpha

- onboarding;
- daily intention;
- contacts and Calendar Provider;
- Open Check;
- basic Focus;
- usage-access timeline;
- settings and privacy controls;
- local suggestion rules;
- crash reporting.

## Phase 2 — Public Beta

- notification summary;
- context modes;
- reflections;
- RevenueCat and Bodha Pro;
- Google sign-in for optional account;
- Cloudflare account backend;
- improved search actions;
- performance hardening;
- accessibility review.

## Phase 3 — v1

- polished recommendation engine;
- explainable suggestions;
- encrypted preference backup;
- selective cloud AI;
- stable OEM behavior;
- localization readiness;
- Play Store launch assets and policy review.

## Post-v1

- Todoist connector;
- Home Assistant connector;
- optional Google Calendar API;
- additional themes;
- advanced Focus presets;
- wider document resume support.

---

## 23. MVP Priority

### Must have

1. Home
2. Universal Search
3. App Library
4. Intent Prompt
5. Open Check
6. Focus
7. Awareness
8. Settings
9. Onboarding
10. Launcher role and app lifecycle reliability

### Should have

- Calendar Provider;
- contacts;
- notification summary;
- basic context modes;
- RevenueCat entitlement;
- optional Google sign-in.

### Could have

- reflections;
- local AI classification;
- encrypted backup;
- advanced themes.

### Will not have in MVP

- Gmail API;
- Slack API;
- Notion;
- TickTick;
- broad Drive indexing;
- mandatory account;
- cross-platform UI;
- hard application blocking.

---

## 24. Key Risks

### OEM launcher behavior

Android vendors differ in launcher, gesture, battery and background behavior.

**Mitigation:** test early on physical Samsung and Pixel devices; maintain a compatibility layer and known-issues guide.

### Permission rejection

Users may deny notification, usage, contacts or calendar access.

**Mitigation:** progressive permission requests; useful degraded modes; never block core use.

### Play policy risk

Broad package visibility, notification access, usage access and any future Accessibility usage require careful disclosure and policy compliance.

**Mitigation:** request the smallest access necessary; document use; review Play policies before each release.

### AI overreach

Incorrect urgency or intrusive recommendations would break trust.

**Mitigation:** deterministic-first logic, explanation controls, local processing and easy suppression.

### Excessive scope

Integrations and automation could turn Bodha into a general personal assistant.

**Mitigation:** every feature must reduce phone interaction or improve intention; reject features that primarily increase engagement.

### Launcher reliability

A launcher is infrastructure. Crashes or latency are more damaging than in a normal app.

**Mitigation:** small startup path, offline defaults, rigorous performance testing and safe fallbacks.

---

## 25. Definition of Done for v1

Bodha v1 is ready when:

- it can be selected and reliably used as the default launcher;
- all installed launchable apps remain accessible;
- Home, Search and Library meet performance budgets on supported devices;
- Intent Prompt, Open Check, Focus and Awareness work without an account;
- optional permissions have clear explanations and graceful denial states;
- user data can be viewed, exported and deleted;
- RevenueCat purchases restore and entitlement state works offline;
- Google sign-in is optional and account deletion is functional;
- no sensitive content appears in logs or general analytics;
- critical flows pass accessibility review;
- baseline profiles and macrobenchmarks are in CI;
- crashes recover to a safe Home state;
- Play Store policy and privacy disclosures accurately match implementation.

---

## 26. Open Product Decisions

1. Android minimum version after device-market validation.
2. Free versus Pro feature boundary.
3. Monthly, annual and lifetime pricing.
4. Whether notification summaries ship in MVP or beta.
5. Exact daily-intention persistence behavior.
6. Default Intent Prompt frequency.
7. Reliable definition of a “phone session” across Android versions.
8. Whether cloud AI is needed at v1.
9. Analytics provider or fully in-house minimal telemetry.
10. Final typography and Bodha visual identity.

---

## 27. Technical References

- Android app architecture: https://developer.android.com/topic/architecture
- Jetpack Compose architecture: https://developer.android.com/develop/ui/compose/architecture
- Android intents and Home category: https://developer.android.com/reference/android/content/Intent
- LauncherApps: https://developer.android.com/reference/android/content/pm/LauncherApps
- UsageStatsManager: https://developer.android.com/reference/android/app/usage/UsageStatsManager
- NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- RevenueCat entitlements: https://www.revenuecat.com/docs/getting-started/entitlements

