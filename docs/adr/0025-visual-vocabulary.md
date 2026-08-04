# The visual reference is binding on vocabulary and superseded on content

`AGENTS.md` names `bodhalauncher.png` as the visual reference and says nothing about what that obliges. The built screens do not match it, and the gap is structural: in the reference every actionable element is a card or a hairline row with a leading icon, and on a device today Home and the Library are bare text separated by hairlines.

This settles which one is authoritative, and the answer is neither wholesale.

## Content is already superseded, by this map's own decisions

"The reference is authoritative" was not available. Three of its thirteen screens contradict landed ADRs:

- **Today** shows *Next event / Next task / Resume*. ADR 0017 drops "one next task", "time-sensitive item" and "resume cards" **outright**, and fixes Today at three slots — intention, day, digest.
- **Settings** shows *Intelligence* and *Integrations*. ADR 0019 deletes both by name, with all four Home entries.
- **Context** leads with *Automatic* over six prefilled modes. ADR 0016 refuses inference entirely and ships nothing pre-made.

**But not one of those ADRs touched the visual vocabulary.** None says whether a row is a card, whether rows carry icons, or what a fill means. Eight ADRs have revised content and zero have revised vocabulary — a consistent practice that had been hiding as an accident.

So: **the reference decides what a row is; ADRs decide what goes in it, and win wherever they speak.**

Treating the reference as a mood board and deciding vocabulary fresh was rejected. Deciding it fresh means deciding it in prose, and issue #124 is the evidence of how that goes: four focus treatments came back indistinguishable precisely because they were drawn on a row shape nobody had decided. Discarding the one artifact that answers the question, in order to re-answer it without one, is the expensive path.

## An element is vocabulary if it encodes something a reader decodes

The reference contains a botanical drawing, and it is obviously not binding. The line is a test rather than a list, the same move ADR 0019 made for what earns a setting: state the rule, let it generate the list, and the list stays correct when a fourteenth screen exists.

The test has teeth because it **excludes** things, which is how it differs from a label for "everything I like about the picture".

Five rules survive it:

1. **Two row idioms.** A **card** for a block acted on once — Home's actions, Today's slots, Settings, AI Assist, the Notifications groups. A **hairline row** for a list that scrolls — Search results, Library apps, Context modes, Reflection's footer actions. It encodes *does this scroll*. The built app uses hairline rows for everything, so the drift is not "no cards" but one idiom where the reference has two and is consistent about which goes where.
2. **Fill carries two meanings, and only two.** **Tinted** for the current or summarising thing — Today's intention, the selected context mode, "View all notifications". **Solid accent** for the single primary action on a screen — Open, Continue writing, send. Neither exists in the build, which is what makes this a decision rather than cleanup: any later choice reaching for fill is choosing for or against a vocabulary already spent.
3. **A trailing chevron means this navigates**; its absence means it acts in place.
4. **Pill versus card.** A pill for a discrete button or field, a card for a row or block.
5. **Leading icon, chip versus bare.** A chip for Bodha's own glyphs, bare for a third party's mark — ADR 0021's voice-and-machinery split arriving in iconography.

Excluded by the same test: the Intent and Reflection artwork, the specific app icons, the phone chrome, Intent's two-column pill grid, Today's sun glyph.

**Page dots pass the test and still lose.** They encode "there are more pins than fit", but Home's pins are ADR 0011's and ADR 0016's, and content wins. The test will occasionally return something the content ADRs own; that is the tie-break.

## The unit is a component, not a screen

The vocabulary is built as **shared components in the design gallery**, and screens adopt them.

This is not a general preference for components. It is that the machinery which makes it pay already exists: ADR 0020's tree-walk and ADR 0022's traversal both run over the design gallery, and #26 says shared components carry semantics and the touch floor *by construction*. A vocabulary expressed as gallery components is covered by both guards the moment it exists. The same vocabulary expressed as fifty per-screen edits is covered by neither until someone lifts it out — which is the lift ADR 0020 already had to perform by hand for `AppRow`, `IconCell` and the letter rail.

Per-screen tickets were rejected because five screens re-deciding the same five rules locally is how five screens end up with four interpretations. Per-rule tickets were rejected because each would touch every file and they would collide.

Building a component layer ahead of its callers is normally guessing at an interface. **It is not guessing here**: the reference shows every usage of all five rules across thirteen screens, so the callers are known before the component is written. That condition is what makes this safe rather than premature, and it does not generalise to components whose callers are hypothetical.

The roster the rules imply: a card row, a list row, a pill (outlined, tinted and solid variants), a field, an icon chip, a section overline, and a trailing-chevron affordance. Section overlines are a convention the built Library already has, so they are carried rather than decided.

## It binds every surface, and the built ones owe a migration

The vocabulary is binding on **all** surfaces. The built screens carry a real migration debt, recorded against spec #26 and sliced by `/to-tickets` when someone goes to do it.

Binding only unbuilt surfaces was rejected, and it is the option that looks cheapest and is not. It does not defer the cost, it doubles it: a launcher running two row idioms makes "what does a row look like focused" two questions instead of one, which is precisely the trap this ADR exists to escape.

**The migration does not gate [#124](https://github.com/PGHQdev/BodhaLauncher/issues/124).** Focus is drawn per component and a component is built once, so that decision needs the roster above settled — which this ADR gives it — not the screens migrated. Gating a decision behind implementation is what this map does not do.

## What this ADR does not settle

Which screen adopts which component in what order. That is implementation, and the map's rule is to produce decisions rather than deliverables.

Whether the two idioms have a boundary case. Every surface in the reference is clearly one or the other, but a short fixed list that does not scroll — Focus's allowed apps, Context's "useful right now" — is drawn as neither, appearing as small cards in a row. It is left as drawn rather than promoted to a sixth rule on one instance.

Resolved in issue #127.
