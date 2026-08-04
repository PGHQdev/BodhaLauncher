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
- **Five overlines unify at letterSpacing 2**, and two sites that were plainly overlines missing their tracking — `OpenCheckDialogs`' "Open Check — …" and `GroupDialogs`' app label — get it.
- **The date under the clock folds into `label`**, losing its 1sp tracking. A seventh sans role for a single `Text` was the alternative. If the pairing with the 64sp clock genuinely needs the tracking, the golden diff will say so and a role can be added then, rather than invented now on a guess.

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
