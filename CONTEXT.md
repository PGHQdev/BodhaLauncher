# Bodha

Intentional Android launcher. This glossary is the ubiquitous language for product and code; decisions behind these terms live in `docs/adr/`.

## Language

**Session**:
The span from device unlock to the device going non-interactive (screen off; always-on display counts as off). A re-unlock within the merge window resumes the same session.
_Avoid_: usage session, screen session, visit

**Peek**:
A screen-on that ends without an unlock — checking the lock screen or glancing at notifications. Not a session.
_Avoid_: pickup (Awareness may count raw unlocks separately; a peek is not one)

**Merge window**:
The 30 seconds after screen-off during which a re-unlock resumes the previous session instead of starting a new one.
_Avoid_: grace period, debounce

**Daily intention**:
The single intention shown on Today for the current day. Editable anytime; expires at the day boundary rather than carrying over.
_Avoid_: intent (that's the per-session Intent Prompt choice), goal

**Day boundary**:
4:00am local time — where one day ends and the next begins for daily state like the intention. Late-night use before 4am belongs to the previous day.
_Avoid_: midnight, day rollover

**Inbox digest**:
The deterministic at-a-glance card of categorized notification counts ("3 people reached out"). Part of the free inbox; involves no model.
_Avoid_: notification summary (that's the model-generated prose)

**Handled**:
A notification the user has dealt with from Bodha's inbox. Handling cancels the real notification, so the system shade and the inbox never disagree.
_Avoid_: read, dismissed (that's the user swiping it away elsewhere), archived

**Notification summary**:
Model-generated prose condensing notification threads. Pro, on-device model only; absent on devices without one.
_Avoid_: digest (that's the deterministic counts card)

**Open Check**:
The user-configured pause sheet before opening a ruled app — a choice point, never a block. Per-app rules pick when it fires.
_Avoid_: app blocker, app lock

**Grant window**:
The few seconds after passing an Open Check during which the granted launch flows back through the opening path without re-firing the check.
_Avoid_: grace period (that connotes leniency; the grant covers exactly one opening)

**Reflexive trigger**:
The default Intent Prompt trigger: a 3rd session starting within a 15-minute window, signalling autopilot use. After firing it rests for a cooldown.
_Avoid_: unlock trigger (it counts sessions, not unlocks)

**Per-query default**:
The user's explicit choice of which result wins for one query string. Set from a result's long-press, reversible there, and the second ranking tier.
_Avoid_: preferred app, favourite, shortcut

**Launch log**:
Bodha's own record of launches it mediates — app identity and timestamp, nothing else. The spine of Awareness's per-app views; needs no permission and keeps full history, unlike the system's few-day usage events.
_Avoid_: usage stats (that's the system's), history, analytics (nothing is transmitted)

**Intentional session**:
A session in which the user stated an intent — answered the Intent Prompt, wrote an Open Check intention, or ran a Focus session. Every other session is unclassified.
_Avoid_: unintentional session (the phone doesn't know), mindful session

**Focus session**:
A stretch of time with an activity label, a duration and a set of allowed apps. While it runs it is root, and any app off the allowed list fires an Open Check. Ends silently at its duration; the end moment waits until the user returns to root.
_Avoid_: session (that's the phone session), timed session (that's one checked app), focus mode

**Surface**:
A place you dwell in and look around: Home, Search, App Library, Awareness, Today, an active Focus session, Settings. Reached by a swipe, from within another surface, or by name in Search.
_Avoid_: screen (that's the display), page, tab

**Sheet**:
One decision you make and leave — single purpose, dismissible without consequence, at most two footer actions. Exactly one exists at a time; a new sheet replaces the open one.
_Avoid_: modal, dialog, popup

**Root**:
The surface back and the system Home button land on. Home, except while a Focus session runs, when Focus is root.
_Avoid_: home screen (Home is one surface; root is a role)
