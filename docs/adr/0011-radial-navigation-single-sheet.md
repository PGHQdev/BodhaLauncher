# Navigation: radial from Home, one sheet at a time

Four swipes are the only places: down opens Search, up the App Library, left Awareness, right Today. Each swipe's target is reassignable among surfaces; double-tap (lock) and long-press (edit mode) stay fixed, and pinch is dropped — long-press already reaches layout editing and Settings, so a second gesture to the same place is vocabulary without purpose. Remapping is free (ADR 0005 puts gestures in the uncapped tier).

Everything past the four is reached by what it is, not by a new gesture. Focus is a state, not a destination: entered from a sheet, ending itself, and while it runs it is the root — back and the system Home button land on Focus, the four swipes keep working so Search stays reachable (#9), and Home resumes as root when the session ends. The notifications inbox lives inside Today, which owns the day: daily intention, calendar, inbox digest. Settings hangs off long-press → edit mode, where Android users already look for launcher settings. Search resolves any surface by name as the universal fallback.

Navigation is radial, never a stack. Sheets dismiss first; then back from any surface returns Home regardless of the route taken, and back on Home does nothing. The cost is accepted: opening Settings from a Search result loses the query. A stack was rejected because it lets a user wander several surfaces deep inside a launcher, which is the opposite of leaving the phone.

A sheet is one decision you make and leave — single purpose, dismissible without consequence, at most two footer actions. A screen is a place you dwell in. Focus setup is a sheet; an active Focus session is a screen. At most one sheet exists at any moment: a new sheet replaces the open one, which satisfies "avoid nested modal stacks" (#17) structurally rather than by discipline, and is right for the case that matters — Open Check firing from an app-actions launch, where the check is a precondition of what the user just asked for. Queueing was rejected (a pause sheet arriving after the user has moved on is the mistimed interruption Open Check exists not to be) and so was suppression (it silently skips a guardrail the user configured). Any open sheet dismisses when the session ends, so nothing survives a screen-off into the next session.

Implementation note, not a decision: swipe-down near the top edge is the system notification shade, so Home's swipe-down threshold must clear it.

Resolved in issue #85.
