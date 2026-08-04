# Settings: three tests for a setting, six sections, an accessibility floor

A setting earns its place only if **all three** hold: the choice is genuinely the user's and not identity, Bodha is the only place it can be made, and it has no in-context home on the surface it affects. Four ADRs had already been applying this rule without naming it — ADR 0010 refused a font switcher and a free-form colour picker (identity), ADR 0017 refused a calendar picker because the user's calendar app owns that toggle (only place), ADR 0018 refused a re-run-setup row because the four things it configures are ordinary surfaces (in context), and ADR 0016 refused pre-made context modes. Naming it makes the next surface's answer derivable instead of re-argued.

Beneath the three tests sits a **floor that overrides them**: every core action reachable only by gesture also appears in Settings, per #26's story 16. Settings is therefore small by rule and complete by accessibility, and the two kinds of row are distinguishable — one is configuration, the other is a route.

The rule deletes most of spec #15. **Typography** goes (ADR 0010 fixes the faces). **Motion** goes (`BodhaMotion` already reads `ANIMATOR_DURATION_SCALE`, so a Bodha-side toggle would be a second control over one value). **Intelligence** goes (ADR 0007 ships no cloud AI; ADR 0008 puts summaries in beta, Pro, on-device). **Integrations** goes (its four entries are either permissions, which belong with privacy, or Pro connectors that do not exist). All four **Home** entries go — the daily intention is Today's (ADR 0017), pins and gestures are edited in long-press edit mode.

## The sections

Six, with the home-role row sitting **ungrouped above them**: whether Bodha is the home app is the app's most consequential state, not a member of a category, and ADR 0018 made Settings the only way back after a decline.

- **Appearance** — theme, accent, clock format, date format. The last two are content settings, not identity (ADR 0010).
- **Navigation** — the four swipe assignments, plus every surface as a tappable row.
- **Intentionality** — Intent Prompt frequency, Open Check defaults.
- **Privacy and data** — the privacy dashboard, the permissions list, export, delete.
- **Bodha Pro** — state, what Pro adds, the three prices, restore, manage.
- **About** — version, open-source licences.

**Accents**: sage plus one warm (clay) and one cool (slate) are free, so the free set spans the range and Pro adds depth rather than restoring a missing dimension. Three settles ADR 0010's open "one or two" and mirrors ADR 0005's reasoning for Open Check rules — three proves the feature — so the number is consistent across the product rather than picked per feature.

## Searchable

ADR 0014 already made **settings actions** one of Search's seven domains. Every individual **row** is a target, matched on its label by prefix-at-word-boundary; sections are not, since people search "theme" or "export", not "appearance". The catalogue is a static list in the engine beside the row definitions, so adding a row adds its search entry by construction rather than by remembering. Rows inside the privacy dashboard are included flat, so "delete" finds delete-local-data. Settings itself already resolves as a surface (ADR 0011).

## Permissions: two lists, two questions

The built `PrivacyDashboard` reducer emits **only granted** permissions and stays that way — #24's stories are written about auditing active grants, and the dashboard answers "what can Bodha see right now". Beside it sits a **Permissions** screen listing all six capabilities with their state, each row opening the existing education screen with `EducationEntry.UserRequest`, whose own documentation already anticipates Settings as an entry point. That satisfies #18's story 12, which the granted-only list cannot: a user who denied notification access and changed their mind has somewhere to say so. The accepted cost is that a granted capability appears twice — once as an audit fact, once as a control. Teaching the dashboard about ungranted state was rejected because it blurs "what does Bodha know" with "what could it know", which is the distinction the dashboard exists to make sharp, and it would change a built and tested reducer.

## Retention and deletion

Retention windows are edited **on the dashboard row that displays them** — `DashboardRow.Data` already carries `retentionDays`, so the row showing the value is where it changes. That is the third test applied to Settings' own content; a separate retention screen would render the same numbers in a second place. The control offers a small fixed set of choices, not free numeric entry, which invites 3650 and means nothing. Categories with a null default render "until you delete it" and are not editable, because no window can apply.

**Delete-local-data clears behavioural history only** — the five retention categories. Preferences, pins, Open Check rules, theme and the intention survive. That is #24's story 10 read literally, and it is the useful action: "I want my history gone" almost never means "and set my launcher back to empty". One confirmation names exactly what goes and what stays. Per-category delete lives on the dashboard row beside its window control, so a category is governed in one place. A full reset was rejected — it destroys configuration nobody asked to lose, and ADR 0018 already ruled that onboarding never re-runs.

`canDeleteAccount` falls out of `signedIn`, so with #21 unbuilt the action is simply absent. Export writes through SAF's `ACTION_CREATE_DOCUMENT`, which needs no capability, so it does not resurrect `Capability.Documents` — still slated for removal by ADR 0017.

## Back

Settings is **one surface with one level of internal depth**: back from the dashboard or the permissions list returns to the Settings root; back from the root returns Home. This is a bounded exemption to ADR 0011 — exactly one level, no deeper nesting permitted — and not the wandering stack that ADR rejected. ADR 0015's precedent, where the inbox is its own surface and back lands on Home, is deliberately not followed here: the inbox is a place you deal with one thing and leave, while Settings is a place people adjust several things in one visit, and returning to Home after each would mean re-walking long-press, edit mode, Settings, section every time.

## The accessibility floor, and what it exposed

The floor needs **two independent routes**, because they serve different people. Home's gesture modifier gains custom accessibility actions for all six gestures, so an accessibility service can invoke each by name; and Settings' Navigation section lists every surface as an ordinary tappable row, for someone who cannot swipe reliably with no service running. Either alone leaves a gap.

Deciding this exposed a live defect rather than a design gap: `homeGestures()` uses raw `pointerInput` with `detectDragGestures` and `detectTapGestures`, and Compose auto-exposes semantics only for `clickable`/`combinedClickable`. All six of Home's gestures are therefore invisible to TalkBack and Switch Access **in shipped code**, and since the swipes are the only route to the four surfaces and long-press the only route to Settings, a screen-reader user can reach nothing beyond Home. That is a violation of #26's stories 10, 11, 16 and 17, filed as issue #111 — it is Home's bug, not Settings' decision, and it is very unlikely to be confined to Home, since `SWIPE_THRESHOLD` is documented as used everywhere a surface hand-rolls a swipe.

Resolved in issue #95.
