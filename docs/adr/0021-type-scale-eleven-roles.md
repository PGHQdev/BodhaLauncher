# The type scale: eleven roles over nine sizes, absorbed from the screens

Spec #26 says screens consume roles and never raw values. They did the opposite: 63 raw `fontSize` literals across 11 of 15 UI files, over 12 distinct sizes. `#90` landed the faces but could not fold the sizes into roles, because six roles could not absorb them.

The reason the six failed is worth stating, because it decided how this scale was built. **`BodhaType` was never adopted.** Its only consumers were `EducationSheet` and `DesignGallery` — the screenshot fixture. Nothing validated its values against a screen, and two were already wrong: the clock renders at 64sp while `voiceClock` said 56sp, and Home's intention is serif italic 19sp while `voiceLine` said italic 18sp. The gallery was photographing roles nobody used.

## The scale is absorbed from the screens, not designed fresh

The 63 literals are not noise. 15sp is *exactly* the six footer actions — Save, Delete, Clear, Go — and nothing else. 13sp-with-tracking is *exactly* the overlines. A design system already existed implicitly and had been applied consistently; the defect was that it was unnamed. So the scale takes the sizes on screen as evidence of real need, collapses the near-duplicates, and names what survives.

A ratio-based ladder designed fresh was rejected: it would have to re-derive those distinctions from scratch, and would likely have lost the 15/14 and 13/12 pairs, which carry real meaning — an action is not a caption.

**Serif is the voice, sans is the machinery** (ADR 0010), and both italic and letter-spacing are baked into roles rather than added at call sites. ADR 0010 calls the daily intention "a centered serif italic line", so italic is constitutive there rather than decorative; and a role a call site has to adjust is the thing this ADR exists to delete. That accepts two roles one point apart when they are genuinely different things.

| Role | Face | Size | Style |
| --- | --- | --- | --- |
| `voiceClock` | serif | 64 | roman |
| `voiceTitle` | serif | 22 | roman |
| `voiceLine` | serif | 19 | italic |
| `voicePassage` | serif | 18 | roman, lineHeight 28 |
| `voiceInput` | serif | 16 | italic |
| `title` | sans | 22 | roman |
| `body` | sans | 16 | roman |
| `action` | sans | 15 | roman |
| `label` | sans | 14 | roman |
| `overline` | sans | 13 | roman, letterSpacing 2 |
| `caption` | sans | 12 | roman |

Eleven roles over nine sizes. `action` 15 / `body` 16 / `label` 14 — three roles inside two points — is the place the scale looks over-fine, and it is kept because each maps to a clean semantic set that the screens already respected.

## The 22sp group is ADR 0010 applied literally

The ticket named this as the blocker: `voiceTitle` was serif at 22sp, but sheet headings at 22sp are operational text, which ADR 0010 assigns to sans, and there was no sans title role. The four serif 22sp sites cleave cleanly on the ADR's own words — "serif is the *voice* (clock, intention, closing line); sans is the *machinery*", "humanist sans for all operational text":

- `OpenCheckSheet`'s app label and `AppActionsSheet`'s app label are third-party data, a name Bodha did not author, sitting beside the app's own icon. **Machinery — sans `title`.**
- `IntentPromptSheet`'s "What are you here for?" and `SessionEndSheet`'s closing line are Bodha speaking. **Voice — serif `voiceTitle`.** The ADR names closing lines specifically.

So the scale carries a sans title and a serif voice title at the same 22sp: machinery and voice at equal rank, which is the contrast ADR 0010 buys. Keeping all four serif on the grounds that the sheet *speaks* the app's name was rejected once the icon beside the label made it plain that this is a row identifying a thing, not a sentence. Demoting app names below title rank was rejected as changing the design rather than naming it.

## The collapses, and what each costs

- **56 is dropped** for the 64sp actually on screen. The role was never consumed, so the screen is the only evidence of intent.
- **28 is dropped.** `PlaceholderSurface` takes sans `title`, a surface name being a label rather than a spoken line — and the stub is deleted as surfaces ship anyway.
- **"Still want to open it?" moves 16→18**, into `voicePassage`. A one-point shift on one site, in exchange for not carrying a sixth serif role.
- **Three tappable 13sp controls move to 14** — the Library's layout switcher, "New group …", "Shown in search". This makes 13sp uniquely the overline, and it grows three touch targets, so the type collapse and ADR 0020's 48dp floor push the same direction.
- **11 folds into `caption`.** One point on the icon-cell label and the rail letters; neither reflows.
- **Five overlines unify at letterSpacing 2**, and the sites that were plainly overlines missing their tracking get it. The census undercounted these while the scale was being drawn; implementing it found the real set. Two corrections, both recorded here rather than left as drift:
  - **Four sites, not two**, were untracked overlines: `OpenCheckDialogs`' "Open Check — …" and `GroupDialogs`' app label, plus `HomeDialogs`' action label and its "Edit Home" — both dialog eyebrows of exactly the same kind, which fell between the census's "tracked overline" and "tappable control" groups and so were counted in neither.
  - **The Library's "Apps" header is a seventh overline**, moving 14→13. It carried 2sp tracking already and was filed under 14sp as a plain label; a muted tracked screen eyebrow is an overline, so the collapse that was described as five-plus-two is really seven.
- **The date under the clock folds into `label`**, losing its 1sp tracking. A seventh sans role for a single `Text` was the alternative. If the pairing with the 64sp clock genuinely needs the tracking, the golden diff will say so and a role can be added then, rather than invented now on a guess.

## What implementing it changed

Two things the collapses did not predict, both consequences of the floor and the scale meeting:

The **layout switcher scrolls** rather than squeezes. Its five labels moved from 13sp to `label`, and each gained the 48dp floor, so the row needs about 332dp — which a 360dp phone does not have once the page padding is off. The floor wins over the layout, so the row gives way: `horizontalScroll`, which is invisible at 411dp and keeps all five reachable below it.

The **gallery's captures were clipped and had been for some time.** The screenshot fixture rendered at the Robolectric default of 320×480 while the gallery was already taller, so the large-type captures — the ones proving "large text never breaks a layout" — were photographing the top third. That was true before this change and unrelated to it; it surfaced because the actionable components were added below the fold and did not appear. The fixture now gets a display taller than itself, and the same trap catches the walk: a node past the window's height measures zero tall, which reads as a floor violation that is really a clipped fixture.

**Text fields are actionable nodes**, which the floor's first pass missed. Compose gives a `BasicTextField` click semantics, so every one of the six in the app was an unnamed ~22dp target: its own contents are not a name, so a screen reader announced an edit box for nothing in particular. Each now names what it edits and takes the floor.

Two things about that are worth writing down, because both are traps rather than choices. The floor must be the **innermost** modifier on a field: it raises the minimum passed to whatever follows, so a `padding` inside it comes straight back off the node the reader activates, and the target measures the floor minus the padding while looking correctly written. And wrapping a field in a floored container does nothing — the field's own node keeps its text height, so the floor has to be on the field.

Four of the six sit inside sheets and dialogs that compose `ModalBottomSheet` or `Dialog` directly, so no fixture can reach them without splitting a content composable out of each. They are fixed but unguarded. That is the same coverage hole in a new place, and its real answer is issue #115: a check that reads call sites rather than fixtures does not care whether a component is reachable from a gallery.

## Roles carry no colour

A role is face, size, style, tracking and lineHeight. Colour stays at the call site, reading `LocalBodhaColors`.

The evidence is `action`: `accent` for Save and Go, `inkMuted` for Delete and Clear — same rank, opposite emphasis. `LibraryScreen`'s layout switcher flips `ink`↔`inkMuted` by selection state. Colour tracks emphasis and state; size and face track rank. A role that fixed colour would need `actionPrimary`/`actionMuted` pairs and would double the ladder to encode state in the type scale.

The honest boundary this leaves: "screens consume roles, never raw values" becomes true of sizes and faces, and stays half-true overall, since every site still names a colour token.

## `BodhaFaces` is deleted

All 15 `fontFamily` sites named `BodhaFaces.serif`, and every one maps to a `voice*` role above. `BodhaFaces.sans` had zero consumers — `LocalTextStyle` already provides it. So the transitional seam #90 added is removed outright, and nothing outside `BodhaTheme.kt` names a family. Making it `private` was rejected as a deletion someone did not finish.

Two things stay. **The `LocalTextStyle` sans default stays**: redundant on paper once every site names a role, but it is what makes a `Text` that forgets one render as machinery rather than inheriting a platform serif, which is the defect #90 fixed. And ADR 0010's possible future serif↔sans clock toggle needs faces addressable — inside the theme file, which is where they now live exclusively.

## The guard is a CI grep

The rule is purely syntactic: no `fontSize =` and no `fontFamily =` anywhere outside `BodhaTheme.kt`. It needs no semantics tree, so it is a grep step in CI rather than a custom lint rule, and it lands without waiting on issue #115 to decide whether a lint module is worth building. If #115 builds one, this rule can move into it; coupling it to #115 now would leave the scale unenforced in the meantime, which is how 63 literals accumulated under a spec that already forbade them.

## The release variant is excluded from unit tests

`:app:testReleaseUnitTest` fails on a clean tree, and not for the reason #109 recorded. It is not a missing comparison target: **all 20 tests fail**, `HomeGesturesAccessibilityTest` included, with `Unable to resolve activity for Intent … cmp=com.bodhalauncher.app/androidx.activity.ComponentActivity`. `androidx.compose.ui.test.manifest` is declared `debugImplementation`, so the release variant's merged test manifest has no activity and every `createComposeRule` test dies at launch. Roborazzi is not involved.

The variant is **excluded** rather than wired, for a reason the ticket did not have: the Roborazzi tests capture to relative paths — `captureRoboImage("src/test/screenshots/gallery_light.png")`. Wiring the release variant would make `./gradlew test` run every capture twice against the same 16 files, debug then release. That is latent today only because release dies before capturing; fixing the manifest would activate it. Beyond that, running Robolectric UI tests against the minified variant tests R8 configuration rather than behaviour, and CI already builds `assembleRelease`. Release-only test-compile breakage still surfaces through `compileReleaseUnitTestKotlin`.

Resolved in issue #109.
