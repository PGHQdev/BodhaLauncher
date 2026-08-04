# The accessibility floor: 48dp both axes, every actionable node named

ADR 0019 put "an accessibility floor" beneath the test for what earns a setting and let it override all three clauses. This is that floor, stated in full, and it is wider than the one row ADR 0019 needed from it.

**Every actionable node is at least 48dp on both axes, and there are no exceptions.** 48dp is Android's own number: what `minimumInteractiveComponentSize` already applies everywhere a Material component is used, and what Accessibility Scanner reports against. WCAG 2.5.8's 24dp was rejected for being below the platform's number on the platform's own devices — it would let Bodha ship targets Android's tooling flags. Relaxing the floor on a control's narrow axis was rejected too: that is the precision spec #26's story 14 exists to not require, and the only argument for it was that one existing control is inconvenient to fix. A floor with an exception list is not a floor.

The nodes this immediately indicts are the ones that hand-roll `combinedClickable` and so skip the Material default: `LibraryScreen`'s letter rail (no declared width — a 16dp start padding and an 11sp glyph, so roughly 24dp), `AppRow` and `IconCell`.

**Every actionable node carries a non-empty name.** This is the general statement of what #111 fixed by hand for one surface. A node exposing `OnClick` with nothing to read aloud is unusable by a screen reader whether or not the gesture behind it works, so labelling is a property of being actionable rather than a per-screen courtesy.

## A control too dense for per-element targets exposes one node with per-element actions

The letter rail is the case that forced the rule, and the rule generalises past it. The rail is `fillMaxHeight()` with one `weight(1f)` slot per present letter — about 27 slots over roughly 700dp on a phone, so **26dp each**. Making each letter its own focusable, clickable node would satisfy story 10 by breaking story 14, and no phone is tall enough for 27 × 48dp, so that is not a matter of finding room.

So: **the rail is one semantics node, named, carrying one custom accessibility action per letter.** Mechanically that is `clearAndSetSemantics` — a plain `semantics {}` block does not merge, so the 27 letter `Text`s would go on being focused individually as bare characters. It is named "Jump to letter" and each action is labelled by its letter alone, because TalkBack already prefixes the menu with the node's name and "Jump to A" … "Jump to Z" would say it twenty-seven times. Its touch area widens to 48dp with transparent padding; the letters stay drawn exactly where they are, so there is no visual change.

Two alternatives were rejected. **An adjustable control** — one node with a value and `SetProgress`, the shape Compose's `Slider` uses and the platform's answer for index bars — is announced correctly but makes A→W twenty-two increments, which is worse than the list it exists to skip. **Clearing the rail from accessibility entirely** was defensible: it is a shortcut, the list scrolls normally, the search field is the alternative story 16 asks for, and #111 set the precedent of dropping an affordance rather than advertising a poor one. It was rejected because a skip-ahead over three hundred rows is worth most to the users who cannot see the rows, and conceding it is the one outcome that makes their path strictly longer than everyone else's.

**The rail announces its name and no state.** A `stateDescription` tracking the list's top letter was rejected: `AlphabetScrubber` only calls `onJump` today, so it would have to observe scroll in both layout branches, and the sighted rail shows no position indicator either — the destination announces itself when focus follows the list. The asymmetry is real and accepted: a sighted user sees where they are, a user on the rail node does not.

## The guard is the design gallery, walked

Spec #26 says accessibility checks ride "the critical-flow Compose UI tests (#27)". **Those tests do not exist.** The only Compose fixtures in the repo are the design gallery, two sheet screenshot tests, and #111's `Box`. An assertion written against the tested flows would have almost nothing to run over, which is how the floor would quietly become advisory.

So the guard is **one tree-walk over the design gallery**: from the root, every node carrying `OnClick` has bounds of at least 48dp on both axes and a non-empty name. One assertion with both clauses, because it is one walk and because #26's Testing Decisions ask for labels *and* touch targets — half of that sentence is not worth a separate mechanism.

For the walk to mean anything, **the gallery renders the actionable components that today exist only inside screens** — the letter rail, `AppRow`, `IconCell`. #26 already says the gallery renders every core component and that shared components carry semantics and minimum touch targets by construction; those three are core components that happen to be private to `LibraryScreen`, and lifting them out is that stated approach actually happening rather than being asserted. A new actionable component is then covered the moment it is shared, rather than the moment someone remembers to assert it.

Per-screen fixtures were rejected as the alternative: covering today's real violations that way means adding a fixture per screen, which is building #27's critical-flow suite under another name. Deferring the assertion until that suite lands was rejected because it reproduces exactly the complaint #114 raised about #111 — a fix with nothing to catch the next one.

Two costs are accepted. The gallery's goldens are re-recorded, all sixteen of which already moved for #90. And the walk proves a *component* meets the floor, not that a screen composes it correctly: a screen wrapping a compliant component in a 20dp box still passes. That residual belongs to #27's flow tests when they exist, and is not a reason to have no guard now.

## What this ADR does not settle

Keyboard operability (#26 story 17) is untouched here — custom accessibility actions serve TalkBack and Switch Access, not a hardware keyboard. **Settled since, by ADR 0022**, which amends this floor rather than standing beside it: a keyboard route is focus plus Enter, parity ranges over outcomes gated by a speed test, and the guard becomes a Tab traversal over the same gallery. Two of this ADR's conclusions are qualified there. The rail is *out* of the keyboard's reach by design — the argument above for not conceding it does not transfer, because a docked user types faster than they could tab — and the 48dp clause is what rules out invisible focusable nodes as the answer to Home's gestures. Whether a lint rule can catch the next `pointerInput` that ships without semantics is open; this ADR defines the rule such a check would enforce, not whether it is expressible. And #111's fix has still not been run against TalkBack on a physical device: the tests assert the semantic properties are set, which is not the same claim as reachability.

Resolved in issue #114.
