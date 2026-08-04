# Awareness: own launch log, four views, no judgement

The event log cannot serve Awareness. Its `EventType` enum is app-name-free by construction (#25, ADR 0009) — no field could carry an app name — so per-app anything must come from elsewhere. Bodha keeps its own **launch log**: one record per launch it mediates, app identity and timestamp, written into `RetentionCategory.RawUsageEvents` and rolled up into `AggregatedUsage` (#19). It needs no permission and has full history, where the system keeps `UsageEvents` only a few days (#88). Where usage access is granted, `UsageStatsManager` fills in what Bodha didn't mediate — launches from notifications, recents and app-to-app switches — plus foreground durations. Without it, the App view and every duration degrade to a named state through the existing capability-education sheet; they never show a misleading zero. ADR 0009 is unaffected: it governs transmission, and none of this leaves the device.

Four views: Today, Week, Session, App. Intent is not a view — it is an attribute of a session (ADR 0001, ADR 0004) that colours the other four. A view reviewing intentions over time is most of what Reflection (#12) is for, and Reflection is Pro where Awareness is free; building it here would decide Reflection's job before its ticket exists.

A session is **intentional** when the user stated something during it: answered the Intent Prompt, wrote an Open Check intention (#76), or ran a Focus session. Everything else is **unclassified** — never "unintentional", because the phone does not know. Implicit signals (going straight to a pinned app, having a daily intention set) were rejected: inferring intent the user never stated is the AI-overreach failure #28 names.

Retention governs what exists; entitlement governs what renders. A free user's data accumulates under the normal retention config while Awareness renders 7 days (ADR 0005); upgrading reveals history that is already there. Pruning to the free window was rejected — it makes Pro's first week empty at exactly the wrong moment. Export and the privacy dashboard (#24) reach everything regardless, so nothing is hidden from the user, only unrendered.

The no-judgement rule (#11) is four prohibitions, checkable without a judgement call: **no signed deltas, no direction words** (more, less, up, down, better, worse), **no valence colour** (no red/green; one ink), **no ranking**. Periods may sit adjacent as bare numbers — "this week 3.1h/day · last week 3.4h/day" — because a signed number is a score with extra steps.

Excluding an app or a session hides it from every view and every metric, reversibly. Records stay until retention or the privacy dashboard removes them; deletion is a separate deliberate act, and keeping the records is what makes "raw event data available behind summaries" (#11) true.

Unlock counting follows the glossary: sessions are the reliable unit, raw unlocks appear only where the signal exists, and a peek is never counted as an unlock. The metrics in `ProductMetrics` are Awareness's computed vocabulary; there is no second parallel set.

The launch log stores app identity, which the event log deliberately cannot. That is a new row for the privacy dashboard and wants checking against #24 rather than assuming compatibility.

Resolved in issue #87.
