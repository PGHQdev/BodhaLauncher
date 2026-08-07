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
The single intention shown on Today for the current day. Owned and edited on Today; Home renders it read-only. Editable anytime; expires at the day boundary rather than carrying over.
_Avoid_: intent (that's the per-session Intent Prompt choice), goal

**Day boundary**:
4:00am local time — where one day ends and the next begins for daily state like the intention. Late-night use before 4am belongs to the previous day. Governs all of Today, including which day the day slot reads.
_Avoid_: midnight, day rollover

**Day slot**:
Today's calendar slot: the current day's not-yet-ended events in time order, from visible calendars, declined instances dropped. Falls back to one Tomorrow row when the day is spent.
_Avoid_: agenda, schedule, next event (that's one row, not the slot)

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

**Context mode**:
A named set of Home pins the user built, optionally taking over during a time window. Switches Home's pins and nothing else. The unnamed default arrangement is not a mode.
_Avoid_: context (too broad), profile, theme

**Per-query default**:
The user's explicit choice of which result wins for one query string. Set from a result's long-press, reversible there, and the second ranking tier.
_Avoid_: preferred app, favourite, shortcut

**Launch log**:
Bodha's own record of launches it mediates — app identity and timestamp, nothing else. The spine of Awareness's per-app views; needs no permission and keeps full history, unlike the system's few-day usage events.
_Avoid_: usage stats (that's the system's), history, analytics (nothing is transmitted)

**Unmediated launch**:
An opening Android saw and Bodha did not — from a notification, from recents, from another app. Filled in from the system's usage events where usage access is granted, and carrying no session, because no session id was read at the moment it happened.
_Avoid_: external launch, background launch, missed launch (nothing was missed; Bodha was not in the path)

**Foreground duration**:
An app's time in front over a rendered window, from Android's usage statistics. Needs usage access, covers the main profile only, and where it is absent the field states the absence in words and never resolves to 0.
_Avoid_: screen time, usage time (both read as a verdict)

**Entitlement window**:
How much of Awareness renders: seven days free, everything retention kept on Pro. Applied in the render path only, so the records exist either way and export and the privacy dashboard reach all of them. It governs Bodha's own records, not a reading taken from Android — which is why a period rate renders at every tier.
_Avoid_: retention window (that's what exists, not what renders), history cap, paywall

**Intentional session**:
A session in which the user stated an intent — answered the Intent Prompt, wrote an Open Check intention, or ran a Focus session. Every other session is unclassified.
_Avoid_: unintentional session (the phone doesn't know), mindful session

**Focus session**:
A stretch of time with an activity label, a duration and a set of allowed apps. While it runs it is root, and any app off the allowed list fires an Open Check. Ends silently at its duration; the end moment waits until the user returns to root.
_Avoid_: session (that's the phone session), timed session (that's one checked app), focus mode

**Surface**:
A place you dwell in and look around: Home, Search, App Library, Awareness, Today, an active Focus session, Settings. Reached by a swipe, from within another surface, or by name in Search.
_Avoid_: screen (that's the display), page, tab

**Today**:
The day surface, one swipe right from Home: three fixed slots — daily intention, day slot, inbox digest — under the day key's date. Never ranked, never a task manager. Awareness's day view is also called Today (ADR 0013) and is a view *within* the Awareness surface — one of two positions on its switch, beside Week — not this surface.
_Avoid_: agenda, dashboard, feed, at-a-glance

**Sheet**:
One decision you make and leave — single purpose, dismissible without consequence, at most two footer actions. Exactly one exists at a time; a new sheet replaces the open one.
_Avoid_: modal, dialog, popup

**Root**:
The surface back and the system Home button land on. Home, except while a Focus session runs, when Focus is root.
_Avoid_: home screen (Home is one surface; root is a role)

**Onboarding**:
The five-step first-run flow — promise, essentials, friction, first intention, become home. A flow, not a surface: linear, exempt from the radial model, and never shown again once complete.
_Avoid_: setup wizard, tutorial, walkthrough

**Home role**:
Android's `ROLE_HOME` — the one grant Bodha asks for. Held or not; without it Bodha is an app you open, which is a named state rather than a broken one.
_Avoid_: default launcher permission (it's a role, not a permission)

**Earns a setting**:
The test a control passes to exist in Settings: the choice is the user's and not identity, Bodha is the only place to make it, and it has no in-context home. All three, or it isn't a setting.
_Avoid_: configurable, option (says nothing about whether it belongs)

**Accessibility floor**:
What every surface owes regardless of any other rule: actionable nodes at least 48dp on both axes and never unnamed, every core action reachable only by gesture also tappable, a control too dense for per-element targets exposing one named node with per-element actions, and a keyboard route for every outcome. It overrides the test above, which is why Settings lists surfaces it doesn't configure.
_Avoid_: a11y fallback, alternative navigation

**Keyboard route**:
How a docked user reaches an outcome: focus, then Enter. Ranges over outcomes rather than controls, and must be at least as fast as the touch control it answers — which is why the letter rail owes none (typing beats it) and long-press owes one (nothing reaches it). A platform key may accelerate a route but is never the only one; Escape-as-back is the single exception, because back has no node to focus.
_Avoid_: keyboard shortcut, hotkey (both suggest an invented chord)

**Focus-revealed affordance**:
A control that draws nothing at rest and appears when it takes focus. How a gesture-only action gets a node without adding anything to a screen at rest, and how that node stays real enough to meet the 48dp floor.
_Avoid_: hidden control, invisible target (an invisible actionable node is the thing this exists instead of)

**Actions node**:
What a focused row reveals to answer long-press — a Home pin's options, an app's actions. Reached by the Right arrow and never by Tab, so a three-hundred-row library stays one stop per app; the Menu key performs the same actions in one press where a keyboard has one. The focused row carries "→ for actions" until the first time the key is pressed, which is the only keyboard convention Bodha teaches, because it is the only one whose absence costs an outcome rather than keystrokes.
_Avoid_: context menu, overflow (both name a menu; this is the node that opens one), kebab

**Voice**:
Text Bodha authored and means — the clock, the intention, a question it asks, a closing line. Set in the serif. Which face a string takes is decided by who wrote it, not by how big it is.
_Avoid_: display type, heading (says something about rank, nothing about authorship)

**Machinery**:
Operational text — controls, data, labels, and anything a third party wrote, an app's own name included. Set in the sans, which is also the inherited default. An app name is machinery even when it's the largest thing on the sheet.
_Avoid_: UI text, body copy, chrome

**Focus ring**:
What a focused actionable component draws: a 2dp accent outline inset within its own bounds, following its own shape — 14dp on a card, round on a pill or field, square on a hairline row — replacing the 1dp hairline rather than stacking inside it. It is the accent's outline, and the outline now means focus and nothing else, the way tinted and solid fills mean the current thing and the one primary action. A touch click focuses nothing, so only a keyboard or accessibility user ever sees it.
_Avoid_: highlight, selection (selection is state that persists; focus is where the keyboard is), active state

**Visual vocabulary**:
The parts of `bodhalauncher.png` that are binding: an element belongs if it encodes something a reader decodes — card versus hairline row (does this scroll), tinted versus solid fill (the current thing versus the one primary action), a trailing chevron (this navigates), pill versus card, chip versus bare icon. The picture decides these; ADRs decide what goes inside them and win wherever they speak.
_Avoid_: design system, style guide (both claim more than the picture settles), mockup (claims less)
