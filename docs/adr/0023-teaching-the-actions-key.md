# Teaching one keyboard convention, on the row, until it is used

ADR 0022 closed by admitting that Right-arrow-to-actions is a convention nothing announces: the affordance reveals itself on focus, so the only hint arrives *after* the guess it was meant to prompt. This settles what to do about that.

The precedent cut toward teaching nothing. ADR 0018 fixes onboarding at five steps and one grant and deliberately does not teach Home's touch gestures either, so whatever the answer was, it was never going to be a sixth step.

## Only one convention is taught

Tab, Enter, Escape and Down are **not** taught. They are platform conventions a docked user tries unprompted, which is why ADR 0022 chose them over invented chords in the first place.

The reason the set is this small is **fallback, not discoverability**. The ticket's framing was that keyboard conventions cannot be discovered by flailing the way a swipe can — true, but it does not separate the four from the one. What separates them is that missing Escape or Down costs a docked user *keystrokes*, because ADR 0019's Navigation section already lists every surface as an ordinary tappable row and that whole path is keyboard-reachable under ADR 0022. Missing Right costs them an entire class of action with **no second route at all**: long-press is the only other way to a Home pin's options or an app's actions, and long-press is exactly what a keyboard cannot do.

That is the same shape of gap #111 and #114 each turned out to be, which is why it is worth a mechanism and the other four are not.

## The hint rides the focused row, not the actions node

**A focused row reveals the hint itself** — trailing, in `caption` at muted ink — on Home's pins and the Library's apps, the two places carrying per-item actions.

Putting it on the actions node was never possible: that node is what Right reveals, so a label there is the circularity the ticket identified. The row is focused first and the actions node second, so the row is the only place a hint arrives before the guess.

A **glyph alone** was rejected. A trailing `›` conventionally reads as "this opens", not "press Right for actions", so it would need decoding by exactly the user who could not guess the convention. A hint that must itself be interpreted teaches nothing, and an unexplained mark is decoration without a named purpose.

**A reference row in Settings** was rejected on ADR 0019's own terms. A teaching row is neither configuration nor a route, so it fails all three clauses of the test for what earns a setting, and the floor that overrides that test covers routes to surfaces rather than documentation. There is also no accessibility section to put it in — ADR 0019's six sections are Appearance, Navigation, Intentionality, Privacy and data, Bodha Pro, About — and bending the rule ADR 0019 spent its whole argument establishing, to hold one sentence, is not a trade worth making. The audience is wrong regardless: someone who does not know a convention exists does not go looking it up.

**A first-dock moment** was rejected as a new mechanism to teach one key.

## It stops when the key is used

The hint shows **until the first time the user presses Right**, then never again.

Always-on was rejected: repeating three words on every row while tabbing a three-hundred-row library is noise for anyone who already knows. Once-per-surface-visit was rejected as the no-persistence alternative — it trades a boolean for a hint that returns forever, which is the worse deal on a launcher whose character is getting out of the way.

The persisted flag is not a new *kind* of state. ADR 0018 already has onboarding completing once and never re-running, so both the precedent and the storage exist.

**The flag is set by the key being pressed, not by the hint being shown.** Teaching is proven by use, not by exposure, and a display counter would retire the hint from someone who never noticed it.

**Clearing behavioural history does not clear it.** ADR 0019's delete row clears behavioural history, and a record that the user knows how their launcher works is not behaviour — it is closer to onboarding's completion flag than to a launch log entry. Under ADR 0009 nothing about it is transmitted; it is a local boolean. This is recorded because the alternative is silent: a delete that re-teaches the hint would look like a bug rather than a policy.

**The hint is not an actionable node.** It draws on the row, is not focusable and carries no click action, so neither the 48dp floor nor ADR 0022's traversal applies to it.

The copy is **"→ for actions"**, set in `caption`. It is the shortest phrasing that names both the key and what it does, and it is copy rather than structure — it can move without reopening this ADR.

## What this ADR does not settle

**What focus looks like.** Nothing in Bodha draws a focus state, and ADR 0022 did not decide one: it settled where focus goes and what Enter does. A focus-revealed affordance indicates itself by appearing, but an ordinary focused row has nothing to show it holds focus, and the traversal guard asserts reachability rather than visibility. The hint decided here rides that indicator and cannot be fully specified until it exists. It is owed to all of ADR 0022 rather than to this hint, and it is a visual-identity question under ADR 0010, so it is tracked separately rather than settled here as a side effect.

Whether a touch user ever sees the hint depends on that same decision. Compose does not request focus on a touch click of a `clickable`, so in principle only a keyboard or accessibility user reaches a focused row — but that is a consequence of how focus is drawn, not a claim this ADR verifies.

Resolved in issue #121.
