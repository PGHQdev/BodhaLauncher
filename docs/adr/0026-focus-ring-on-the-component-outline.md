# Focus is a 2dp accent ring on the component's own outline, and touch never draws it

ADR 0022 settled where focus goes and what Enter does. ADR 0023 put a hint on the focused row and closed by admitting the hint "rides that indicator and cannot be fully specified until it exists". This is the indicator.

## The first prototype failed, and the reason is not that the treatments were bad

Issue #124's first attempt drew four treatments — an accent rule at the leading edge, a tinted block, an outline ring, an ink shift with a rule beneath — and they came back indistinguishable. ADR 0025 has already recorded the diagnosis: they were drawn on a row shape nobody had decided.

The honest version is narrower and worse. The prototype defined one CSS class, `.row`, and gave Home's pins and the Library's apps the same class, differing by `justify-content: center`. Three of the four treatments were then the same move — a 1–2px accent stroke on the boundary of an identical transparent box — placed at the leading edge, all four edges, and the bottom edge. So the specimen asked one question four times, and every stated failure mode came back as a question about the row rather than about the treatment: does the box have an axis, does the design have fills, does the box match the content or the 48dp floor, what is a row's resting ink. It was not a comparison. It was a coin flip with four faces.

The second attempt is `prototypes/focus-state.html`. Two things changed. Every treatment is drawn on the four real shapes from `BodhaComponents.kt` with their real geometry — a `CardRow` (14dp radius, `surface` fill, 1dp hairline border), a `ListRow` (a 1dp hairline above and no box at all), and `BodhaPill` / `BodhaField` (fully round, `surface`, hairline border). And all five candidates render **at once, side by side**, in both themes and all three free accents, with a toggle that puts every node back at rest. The first attempt used a switcher, which is how four indistinguishable things went unnoticed for a whole prototype.

## Three of five candidates cannot be told from an unfocused row

That was the disqualifying test, and it disqualified on sight rather than on argument.

- **A ground step with no accent** — the focused thing rises one step off its ground. On a `CardRow` it cannot exist at all: a card is *already* the `surface` step, so the focused card would need a third ground that does not exist in the palette. In dark, where `surface` is `#2A2721` against a `#221F1A` ground, it is invisible on every shape. It was the only candidate immune to a bad accent, and it is immune because it shows nothing.
- **A rule beneath the row** — a 2dp accent line on the bottom edge. On a `ListRow` it lands one pixel from the *next* row's own hairline, so it reads as that row's divider thickening. Inside a pill it is a stripe across the bottom of a round shape.
- **A bar at the leading edge** — a 3dp accent bar inside the shape's start edge. It is the most disciplined of the five and the hardest to see: a sliver roughly one-hundredth of the row's width, sitting immediately beside a 34dp app icon that outweighs it. In dark at slate it disappears. It also fights `IconChip` for the leading slot, which rule 5 already owns.

## The ring takes the component's own shape

**A focused actionable component draws a 2dp ring in the accent, inset within its own bounds, following its own shape.** A `CardRow` rings at 14dp radius. A `BodhaPill` and a `BodhaField` ring fully round. A `ListRow`, which has no box, rings **square** — a rectangle at the row's own bounds, which in a `fillMaxWidth` list means edge to edge.

The ring **replaces** the 1dp hairline the component already draws rather than stacking inside it: a Plain `CardRow`, pill and field suppress their hairline border while focused, and a `ListRow` suppresses the hairline above it, because the ring's top edge occupies that pixel. So on three of four shapes the treatment is a recolour and a thickening of an edge that already exists, which is why it costs no layout: `Modifier.border` draws inside the bounds, nothing reflows, and no padding is consulted. That matters because ADR 0020 guarantees 48dp on both axes and nothing else — a treatment that needed a row to have padding would work on `CardRow` and fail on `ListRow`.

Drawing the `ListRow`'s ring at the card's 14dp radius was the version tried first and rejected. It looks slightly better and it says the wrong thing: rule 1 makes a rounded box mean *a block acted on once*, so a scrolling row that becomes card-shaped on focus conflates the two idioms the rule exists to keep apart. A square ring reads as a band across the list, which is what a focused list row is.

**Fill was never available.** `Emphasis` has three members and both non-default fills are spent — tinted is the current or summarising item, solid is the screen's one primary action — and `BodhaTheme.kt`'s own comment already says neither is available for anything else, "which is why focus cannot use them". The first prototype's tinted block was eliminated by ADR 0025 rather than judged here.

**The colour is `LocalBodhaColors.current.accent`, read at the call site.** ADR 0021's type roles carry no colour, so this could not have lived anywhere else. A separate `focusRing` token was rejected: it would be a second value a shade off the accent, which is a colour decision ADR 0010 reserves to the identity, and it would have four callers that all want the same answer.

The cost is real and is the extension this ADR makes to rule 2: **an accent stroke now means focus, and nothing else may claim one.** Rule 2 spends the two accent *fills*; the accent *outline* was unspent, and this spends it. A later control that wants an accent border is choosing against a vocabulary already allocated, exactly as rule 2 intended for fills.

**A focus-revealed affordance takes the ring too.** ADR 0022's Home gestures appear when focused, and the argument for exempting them is that they already indicate themselves. They do not indicate the same fact: appearing says *this control exists*, the ring says *this is where focus is*. A floor with one exception is what ADR 0020 refused, and the same reasoning applies to a treatment that is supposed to be the answer to "where am I".

## The ring does not touch the trailing slot, and the trailing collision is still there

The ring is an outline, so ADR 0023's "→ for actions" — trailing, `caption`, muted ink — is unobstructed. Muted ink against an accent outline is not a competition; the prototype renders both on the same focused row in every theme and accent.

What the ring does not solve is the slot itself. `CardRow` and `ListRow` have one `trailing` slot, rule 3 puts a `TrailingChevron` in it on any row that navigates, and the hint wants the same place. That collision is **live in principle and empty today**: the two rows carrying the hint are Home pins and Library apps, and both *act in place* under rule 3 — opening an Android app is not navigating within Bodha — so neither draws a chevron. When a row does need both, they sit side by side in the trailing slot, hint then chevron in reading order. That is a layout call recorded here so it is not re-decided per screen; it is not load-bearing on this decision.

## Touch never shows focus — verified, not assumed

ADR 0023 asserted that Compose does not request focus on a touch click of a `clickable` and recorded the claim as unverified. It is now verified, on this repo's own toolchain.

A throwaway Robolectric probe composed two `Text`s with bare `clickable`, read `SemanticsProperties.Focused` off each, and reported:

```
rest              one=false two=false
afterTouchClick   one=false two=false   (performClick on "two")
afterTab          one=true  two=false
afterTab2         one=false two=true
```

A touch click focuses nothing. So the ring is drawn for keyboard and accessibility users only, and a touch-only user never sees it. Three consequences follow and none of them is small:

- ADR 0023's hint and its persisted retirement flag are **keyboard-only features**. The flag will remain false forever on a device that is never docked, which is correct behaviour rather than a leak, but it means the boolean's population is small.
- The ring cannot regress the touch design, because touch never draws it. That removes the objection that killed the first prototype's ink-shift treatment — dimming every resting row for a keyboard-only benefit — and it is why a treatment that only ever appears under a keyboard can afford to be as loud as a 2dp accent ring.
- Screenshot fixtures capture at rest, so **the design gallery must render a deliberately focused specimen** or no golden will ever contain the ring. That is a fixture obligation, not an optional nicety: without it the treatment is outside both guards that ADR 0025 built the component layer to be inside.

The same probe answers a question ADR 0022 and #26 both flagged as unverified: **Robolectric key injection does drive Compose focus traversal.** `performKeyInput { pressKey(Key.Tab) }` moved focus from the first focusable to the second. ADR 0022's traversal guard does not need its grep fallback, and ADR 0024 has since superseded that fallback anyway.

## Sheets and dialogs draw it identically

No variation, and the reason is structural rather than aesthetic.

ADR 0011 permits exactly one sheet at a time and a new sheet replaces the open one, so the situation that normally forces a distinct treatment — a live indicator in front and a stale one behind a scrim — cannot arise. And a sheet is not a different ground: `AppActionsSheet` composes `ModalBottomSheet(containerColor = colors.ground)`, so a sheet's ground is the screen's ground, and a treatment drawn against `surface`-on-`ground` behaves the same in both. This is the argument the ground-step candidate could not have made, and it is a second reason not to want a contrast-based treatment.

The cost is inherited rather than created: ADR 0021 records that four of six text fields sit inside sheets or dialogs that compose `ModalBottomSheet` or `Dialog` directly, so no gallery fixture reaches them. The ring is guarded on the components and unguarded inside those four until a content composable is split out of each, the way `OpenCheckSheetContent` and `SessionEndSheetContent` already were. Deciding a sheet-specific treatment would not have closed that hole; it would have added a second unguarded thing.

## What this ADR does not settle

**Whether the ring is legible in clay and slate.** ADR 0019 settles three free accents and only sage has built colour values, so the prototype approximates the other two — as the first one did. The ring reads clearly in all six combinations *as approximated*, which is evidence about the treatment and not about the accents. When ADR 0019's accents are implemented, the ring is one of the things that has to be looked at, and it is the reason a focus treatment whose only signal is hue would have been the wrong bet.

**Focus is invisible on every real screen until the ADR 0025 migration lands.** No screen consumes the component layer yet. A device check of this decision today shows the gallery and nothing else — which is the cost ADR 0025 accepted when it said the migration does not gate #124, stated here so nobody reads the empty screens as a bug.

**Nothing here has been seen on hardware.** No docked keyboard, no TalkBack device pass. A ring that looks right in a macOS-rendered Roborazzi golden is not evidence a docked user can see where they are.

**Where focus goes when Home has six focus-revealed affordances and a pin's actions node all reachable by Right.** ADR 0022 left that collision open and this decision does not close it; the ring makes the answer *visible*, which is a strictly better position to argue it from.

Resolved in issue #124.
