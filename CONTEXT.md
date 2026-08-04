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
