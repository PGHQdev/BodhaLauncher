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

**Reflexive trigger**:
The default Intent Prompt trigger: a 3rd session starting within a 15-minute window, signalling autopilot use. After firing it rests for a cooldown.
_Avoid_: unlock trigger (it counts sessions, not unlocks)
