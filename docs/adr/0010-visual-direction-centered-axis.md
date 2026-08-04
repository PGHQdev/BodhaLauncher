# Visual direction: Centered Axis

Bodha commits to the "Centered Axis" direction (variant E of the prototype on branch `prototype/visual-direction`, issue #43): a centered serif clock over a complication row of stroke glyphs (one 16px grid, hairline weight, nothing pictorial or emoji-like), the daily intention as a centered serif italic line, and a left-aligned hairline-rule list below — warm paper ground, sage accent, humanist sans for all operational text. Serif is the *voice* (clock, intention, closing line); sans is the *machinery*. Chosen over the anchor image's card-based layout (A), full book-typography (B, too austere), and all-sans (C, indistinct); direction only — full identity production is out of scope per the map.

## Customization policy

- Light/Dark/System theme: free. The dark theme is a designed warm-charcoal counterpart, never an inversion.
- Accent: a curated muted set (sage default, plus hues like clay/slate/ochre). Sage + one or two free; the full set is Pro "advanced themes" (ADR 0005). No free-form color picker — it would dissolve the identity.
- Fonts: not user-switchable at v1 — the serif/sans balance is the identity. A display-voice toggle (serif ↔ sans clock) may come later under advanced themes.
- Clock and date formats are content settings, not identity: Settings offers 12-hour, 24-hour, and NATO/military clock display, and configurable date/day formats. Free.

## Consequences

The weather complication needs a location: optional coarse location permission or a manually chosen city, with graceful degradation (the complication hides when neither is granted). The weather fetch is an outbound network call — declared appropriately in Play Data Safety as app functionality; it carries no analytics, leaving ADR 0009 intact.
