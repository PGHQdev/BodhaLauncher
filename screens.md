# Bodha — Screen & Interaction Specification

## 0. Lock Screen Handoff

Bodha does not replace Android’s lock screen.

After unlock, it opens directly into **Home** or restores the user’s active focus session.

Optional setting:

* Ask intent after selected unlocks
* Never ask during navigation, calls, camera, or emergencies

---

# 1. Home

The default surface.

## Elements

* Time and date
* Daily intention
* Current context: Work, Home, Commute, Evening
* Up to four suggested actions
* Universal search field
* Small notification summary
* Subtle focus-session indicator

Example:

```text
10:42
Tuesday, 4 August

Today
Finish Bodha prototype

Continue writing
Next meeting · 11:30
Call Mom
Camera

Nothing urgent

Search
```

## Gestures

* **Swipe down:** Search
* **Swipe up:** App library
* **Swipe left:** Awareness
* **Swipe right:** Today
* **Long-press empty space:** Edit Home
* **Long-press suggestion:** Hide, pin, or explain suggestion
* **Double-tap empty space:** Lock phone

## Decisions

* No app grid
* No badges
* No feed
* Maximum four suggestions
* Suggestions never animate for attention
* User-pinned actions outrank AI suggestions

---

# 2. Intent Prompt

A lightweight pause before action.

## Trigger

Not shown on every unlock.

Show when:

* User unlocks repeatedly within a short period
* User has no active task
* User opens a distracting app reflexively
* User explicitly enables “Ask every time”

## Elements

```text
What are you here for?

Continue something
Communicate
Capture
Find something
Browse
Just looking
```

Optional text input:

```text
Something else…
```

## Interaction

Selecting an intent changes Home suggestions immediately.

Examples:

* **Communicate:** contacts, Messages, Slack
* **Capture:** camera, notes, voice recorder
* **Find:** search opens automatically
* **Browse:** asks for a duration or topic
* **Just looking:** opens Home without judgment

## Modal behaviour

Appears as a bottom sheet, not a full-screen interruption.

Dismiss by:

* Swiping down
* Tapping outside
* Selecting “Just looking”

---

# 3. Today

The user’s immediate operating context.

## Elements

* Daily intention
* Next calendar event
* Current focus session
* One next task
* Time-sensitive items
* Resume cards
* Optional task-service connector

Example:

```text
Today

Finish Bodha prototype

11:30
Design review

Next
Review onboarding flow

Resume
PRD · Last opened 24 min ago
```

## Gestures

* **Swipe right from Home:** Open Today
* **Swipe card right:** Complete or dismiss
* **Swipe card left:** Snooze
* **Long-press card:** Pin, hide, or change source
* **Tap time:** Open source app

## Rules

* Maximum six cards
* Sorted by urgency and relevance
* No infinite scrolling
* Completed items disappear quietly

---

# 4. Universal Search

The primary navigation mechanism.

## Search targets

* Apps
* Contacts
* Calendar events
* Settings
* Files explicitly opened through Bodha
* App shortcuts
* System actions
* Connected tasks
* Home Assistant actions
* Web search

## Example

```text
mom

Call Mom
Message Mom
Photos with Mom
```

```text
meeting

Design review · 11:30
Open Calendar
Turn on Focus until meeting
```

## Gestures

* **Swipe down anywhere on Home:** Open Search
* **Press keyboard Enter:** Launch first result
* **Swipe result right:** Quick action
* **Long-press result:** Pin, hide, or choose default
* **Back gesture:** Return to Home with query cleared

## Search rules

* Instant local results
* Cloud suggestions appear later
* Exact matches outrank recommendations
* No sponsored results
* No search history shown by default

---

# 5. App Library

All installed apps remain accessible.

## Layout

Default: alphabetical text list.

Optional views:

* Compact icons
* Categories
* Recently used
* User-created groups

## Elements

* Search
* Alphabet scrubber
* Hidden apps section
* Screen-time context, shown subtly

Example:

```text
Instagram
Last used 8 minutes ago
```

## Gestures

* **Swipe up from Home:** Open library
* **Fast vertical drag:** Alphabet scrub
* **Long-press app:** App actions
* **Swipe app right:** Pin to Home
* **Swipe app left:** Hide

## App menu

Bottom sheet:

* Open
* App shortcut
* Pin
* Hide
* Pause
* App information
* Set opening check

---

# 6. Open Check

A gentle pause before selected apps.

## Trigger options

Per app:

* Always
* After repeated opening
* During focus
* After daily limit
* Never

## Elements

```text
Instagram

Last opened 8 minutes ago
Used 34 minutes today

Still want to open it?

Open
Open for 10 minutes
Go back
```

Optional intent field:

```text
What do you want to do there?
```

## Behaviour

* Default button is not automatically focused
* No countdown
* No guilt language
* No red warning colours
* Can be dismissed with back gesture
* Emergency and utility apps bypass it

## Session modal

When temporary access expires:

```text
Your 10 minutes are complete.

Close Instagram
Add 5 minutes
Continue without a timer
```

The user always retains final control.

---

# 7. Focus

A minimal working state.

## Elements

```text
Writing

42 minutes remaining

Continue writing
Search
Pause focus
End focus
```

Optional:

* Selected allowed apps
* Active task
* Upcoming meeting
* Ambient sound control

## Gestures

* **Swipe down:** Search allowed apps
* **Swipe left:** Session details
* **Long-press timer:** Extend or shorten
* **Back gesture:** Return to focused app
* **Home gesture:** Return to Focus screen

## Focus setup modal

```text
What are you doing?

Writing
Reading
Working
Resting
Custom

Duration
45 minutes

Allow
Messages
Phone
Calendar
```

## Rules

* Apps are deemphasized, not forcibly hidden
* Opening a blocked app shows Open Check
* No streaks
* No productivity score

---

# 8. Notifications

A calm notification inbox.

## Home summary

```text
3 people reached out
1 item needs attention
Nothing urgent
```

## Full screen sections

* People
* Time-sensitive
* Work
* Updates
* Silent

## Elements

Each notification includes:

* Source
* Sender
* Short summary
* Time
* Suggested action
* Original notification expansion

## Gestures

* **Swipe right:** Mark handled
* **Swipe left:** Snooze
* **Long-press:** Mute source or change priority
* **Tap summary:** Expand originals
* **Pull down:** Refresh local notification state

## AI use

AI may:

* Group duplicate notifications
* Detect urgency
* Summarize threads
* Surface action-required messages

AI may not:

* Send replies automatically
* Mark something urgent without explanation
* upload all notification contents by default

---

# 9. Awareness

A neutral record of phone behaviour.

## Elements

### Timeline

```text
09:10  WhatsApp     2 min
09:18  Instagram   43 sec
09:20  Camera
09:32  Chrome       Research
```

### Patterns

```text
Instagram opened 12 times
Most sessions lasted under 1 minute
```

### Intent comparison

```text
27 phone sessions
14 began with a clear intention
```

## Views

* Today
* Week
* App
* Session
* Intent

## Gestures

* **Swipe left from Home:** Open Awareness
* **Tap session:** View sequence
* **Pinch timeline:** Change time density
* **Long-press item:** Add context or exclude
* **Swipe pattern:** Dismiss insight

## Rules

* No scores
* No rankings
* No shame language
* No celebratory animations
* Raw data always visible behind AI interpretation

---

# 10. Reflection

A short daily or weekly summary.

## Elements

```text
Today

You mostly used your phone for work.

You opened Instagram 12 times,
but spent only 18 minutes there.

Your longest focused period was 47 minutes.
```

Actions:

* Review timeline
* Adjust one habit
* Dismiss
* Add a note

## Reflection modal

Optional prompt:

```text
Anything worth remembering?
```

One line only.

## Frequency

* Daily
* Weekly
* Never

No push notification unless explicitly enabled.

---

# 11. AI Assist

AI appears as actions, not a chat screen.

## Surfaces

* Home suggestions
* Search actions
* Notification summaries
* Resume cards
* Context detection
* App-switching intervention
* Reflection insights

## Example cards

```text
Resume reading page 37
```

```text
You have switched apps five times.
Looking for something?
```

```text
Your meeting starts in 12 minutes.
Open notes?
```

## Explanation modal

Every suggestion has:

```text
Why am I seeing this?
```

Example:

```text
You usually open Calendar before this meeting,
and you viewed these notes yesterday.
```

Actions:

* Useful
* Not useful
* Never suggest this
* Manage data source

## Core rule

AI must reduce steps, not generate more content.

---

# 12. Context Modes

Context changes what Home surfaces.

## Built-in modes

* Morning
* Work
* Home
* Commute
* Travel
* Evening
* Focus
* Sleep

## Inputs

* Time
* Calendar
* Location, optional
* Connected Bluetooth device
* Current app
* Media state
* Recent usage
* Manual selection

## Context selector

Tap the context label on Home:

```text
Current context

Automatic
Work
Home
Commute
Custom
```

Manual selection overrides AI temporarily.

---

# 13. Settings

Settings should remain small and understandable.

## Sections

### Appearance

* Theme
* Typography
* Density
* Icon style
* Motion
* Wallpaper behaviour

### Home

* Daily intention
* Suggestions
* Pinned actions
* Gesture mapping

### Intentionality

* Intent prompt frequency
* Opening checks
* Focus defaults
* Reflection frequency

### Intelligence

* On-device AI
* Cloud AI
* Suggestion sources
* Explanation history

### Integrations

* Calendar
* Notifications
* Contacts
* Todoist
* Home Assistant
* Future connectors

### Privacy

* Local data
* Export
* Delete
* Cloud sync
* Diagnostics

### Subscription

* Bodha Pro
* Restore purchases
* Manage subscription

---

# Global Modals

Use bottom sheets wherever possible.

## Required sheets

* App actions
* Intent selection
* Focus setup
* Open Check
* Suggestion explanation
* Context selection
* Snooze duration
* Permission explanation
* Connector setup

Avoid nested modals.

Maximum two actions in most modal footers.

---

# Global Gestures

| Gesture         | Default action    |
| --------------- | ----------------- |
| Swipe down      | Search            |
| Swipe up        | App library       |
| Swipe left      | Awareness         |
| Swipe right     | Today             |
| Double-tap      | Lock              |
| Long-press Home | Edit              |
| Back gesture    | Return or dismiss |
| Pinch Home      | Enter layout mode |

All gestures must be remappable.

---

# Onboarding

Maximum five screens.

## Flow

1. **Promise**
   “A phone that helps you remember why you picked it up.”

2. **Choose essentials**
   Select 4–8 important apps.

3. **Choose friction**
   Select distracting apps for Open Check.

4. **Grant permissions**
   Explain each permission before Android requests it.

5. **Set first intention**
   “What matters today?”

No account required.

---

# Visual Language

## Feel

* Calm
* Precise
* Warm
* Spacious
* Quietly intelligent

## UI rules

* Warm neutral backgrounds
* One muted accent
* Text-first navigation
* Minimal icon use
* Soft depth, not glassmorphism
* No red except genuine errors
* No bouncing elements
* No attention-seeking animation
* Motion under 250 ms
* Haptics subtle and optional

---

# MVP Screen Set

Build these first:

1. Home
2. Search
3. App Library
4. Intent Prompt
5. Open Check
6. Focus
7. Notifications
8. Awareness
9. Settings

Delay:

* AI Assist screen
* Full Reflection system
* Advanced context modes
* External connectors
* Cross-device sync

The central product loop is:

> **Unlock → remember intention → act quickly → leave the phone → review without judgment.**

