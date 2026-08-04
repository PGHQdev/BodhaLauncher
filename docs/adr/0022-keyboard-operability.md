# Keyboard operability: focus and Enter, and nothing else guaranteed

ADR 0020 closed by naming keyboard operability as the thing it did not settle. This is that clause, and it amends the accessibility floor rather than standing beside it: the floor is one floor, and a docked user is not a different kind of user.

The reason it needed its own ADR is that ADR 0020's two fixes give a hardware-keyboard user **nothing**. Custom accessibility actions live on `AccessibilityNodeInfo` and are offered by a screen reader's menu; with no accessibility service running there is no menu. Home's six gestures and the letter rail's twenty-seven letters were made reachable for TalkBack and Switch Access and left exactly as unreachable as before for someone docked at a keyboard.

## What Android already gives, and what it does not

Spec #26's story 17 says "where Android exposes keys", and that phrase does real limiting work throughout this ADR. Establishing what was already true came first, because most of the answer turned out to be *don't break what's free*.

Free, with no code at all: **Tab and Shift+Tab** traversal and **arrow-key** two-dimensional focus search, both automatic in Compose; **Enter and Space** activating any `clickable` or `combinedClickable`, which also confers focusability; **PageUp/PageDown** scrolling a `LazyColumn`; and the full text editing and selection set inside `BasicTextField`.

Not free, and each one a gap in this repo:

- **Nothing maps Escape to back.** No default binding exists on Android, and ChromeOS's own input-compatibility guidance does not mention Escape either.
- **`combinedClickable` has no key for long-click.** Enter is click and only click.
- **`pointerInput` is inert to keys and publishes no node.** It is not merely unlabelled — there is nothing for focus to land on.
- **`touchTargetFloor()` is `defaultMinSize`**, so meeting ADR 0020's floor confers no focusability.

Against those, the state of the repo when this was written: a docked user on Home could Tab to the intention row, the pin rows, `＋` and Search, and press Enter. They could not reach Search, Library, Awareness or Today at all — the four `homeGestures` swipes are the only route to them under ADR 0011 — could not reach edit mode or any app's actions, could not use the rail, which Tab passes straight over, and had no back key.

## Parity over outcomes, gated by a speed test

**Every action a touch user can perform has a keyboard route.** Reachability of surfaces alone was rejected: "reachable some other way" is the reasoning that left the rail serviceable by TalkBack and inert to everything else, and it gives a guard nothing crisp to assert.

But parity ranges over **outcomes, not controls**, and the test is that the keyboard route is **at least as fast** as the touch control it answers. A mirror of every control was rejected because it forces twenty-seven tab stops onto the rail — the per-element traversal ADR 0020 refused on touch-target grounds, arriving from the other side.

The speed test is not an escape hatch, and the two cases that forced it prove it by disagreeing. **The rail passes**: a docked user types "m" into the search field instantly, so their path is strictly *shorter* than the rail's. **Long-press fails**: there is no keyboard route to app actions or edit mode at all, let alone a faster one, so it still owes one.

ADR 0020 refused to concede the rail to accessibility on the grounds that skip-ahead over three hundred rows matters most to users who cannot see the rows, and that conceding it makes their path strictly longer than everyone else's. **That argument does not transfer to a keyboard**, and the non-transfer is recorded here explicitly so this does not read as a quiet exception. Typing on a soft keyboard under TalkBack is slow; typing on the real keyboard the user is already docked at is not.

So **the rail is out of the keyboard's reach by design**. It needs no code: it is `clearAndSetSemantics` over `pointerInput` with no `clickable`, so it is already unfocusable, and this ADR makes that the intended state rather than an oversight.

## Focus and Enter is the only guaranteed route

**A keyboard route is focus plus Enter.** Never a chord we invented: `Ctrl+Shift+L` is a keymap no docked user will guess, and Tab and Enter are what Android exposes.

**A platform key may accelerate but is never the sole route to anything.** This is what keeps the Menu key below from being the first crack in the rule — nothing depends on it, so nothing breaks where it is absent.

**Escape is back**, and it is the single exception to focus-plus-Enter. Its justification is that back has no node to focus, so there is nothing for the general mechanism to attach to. It is cheap because ADR 0011 made back uniform: one binding at root, returning to root, with no per-surface variation. It binds on the non-preview `onKeyEvent` so a focused child may consume Escape first.

Binding arrow keys on Home's root to perform the four swipes was rejected. It reads as the natural mapping for a radial model, but arrow keys are already Compose's two-dimensional focus search, so it would either fight traversal or work only while nothing else holds focus.

## Gestures acquire focus-revealed affordances

Home's six gestures need focusable nodes. They are **focus-revealed**: nothing at rest, and the node draws itself when it takes focus — the skip-link pattern.

Permanent visible affordances were rejected as the design change ADR 0011's radial model exists to avoid; Home is deliberately spare. **Invisible focusable nodes were rejected on ADR 0020's own terms**: the gallery walk asserts every node carrying `OnClick` is at least 48dp on both axes, so an invisible node either fails the guard or occupies 48dp of transparent space that then swallows touches meant for the swipe layer. ADR 0020 spent its argument on the claim that a floor with an exception list is not a floor, and the amendment is not the place to prove it wrong. Focus-revealed costs the touch design nothing and a focused node is real, drawn and floored, so no exemption is needed.

`longPressEmpty` is one of the six, so "Edit layout" is covered here rather than below.

## Per-item actions: a node reached by Right, and the Menu key beside it

What long-press still owes is the context menu on a focused item — Home's pins (`optionsFor`) and the Library's apps (`actionsFor`).

**Each focused row reveals an Actions node, reached by the Right arrow rather than Tab.** Compose's two-dimensional focus search already binds arrows, so the node costs zero tab stops: tabbing the Library stays one stop per app, and Right from a focused row reaches its actions. Putting it in the tab order was rejected because it doubles traversal through a three-hundred-row list to serve the rarer of the two intents.

**`KEYCODE_MENU` is an accelerator on the same node.** Android's `Generic.kl` maps scancode 139 (`KEY_MENU`) and scancode 127 (`KEY_COMPOSE`, what a PC keyboard's Application key sends) to `MENU`, so a full-size external keyboard does deliver it.

It cannot be the only route, and the check that established this is worth keeping: **Chromebook keyboards have no Menu key**, and ChromeOS's input-compatibility guidance answers context menus with right-click and `OnContextClickListener` — a mouse, not a key. Shift+F10 does not help either; Android maps `F10` to `F10` and synthesizes nothing. A Menu-key-only answer would fail precisely on the docked case story 17 is about.

## Focus lands on arrival, with the IME suppressed

**When a surface opens, focus goes to its first field.** Nothing in the repo called `requestFocus` before this; no surface autofocused anything.

Branching on `Configuration.hardKeyboardHidden` was considered and rejected: it would preserve touch behaviour byte-for-byte, but it makes behaviour diverge by input model, which nothing else in Bodha does. One behaviour for everyone was preferred over an additive branch.

The cost is real and is accepted rather than hidden. **Compose has no supported "focus without showing the IME".** It exposes `SoftwareKeyboardController.hide()` and `FocusRequester`, and the implementation is therefore hide-on-focus-gain — a hide *after* a show, which can flash the IME on slow devices. `windowSoftInputMode="stateAlwaysHidden"` does not cover it, since it governs window focus rather than a composable's `requestFocus`. This is buildable and not clean, and it is the residual most likely to need revisiting.

## Down enters the list beneath a field

On the App Library and Search, **Down from the text field moves focus into the first result row**. Enter then activates it through the row's existing `clickable`, for free. A single-line `BasicTextField` is expected to consume Up and Down as cursor commands and swallow them rather than pass them to focus search, so the binding must be explicit; that behaviour needs confirming against `TextFieldKeyInput` at implementation time.

A true combobox — the field keeps focus, Down drives a highlighted row, Enter activates it — is what a desktop user's fingers expect and is one keystroke faster on re-query, since typing more query text here means Up or Shift+Tab back to the field. It was rejected because it introduces a **selection** distinct from focus, which nothing in Bodha has: the highlighted row would not be the focused node, so neither the guard below nor the focus-plus-Enter rule would describe what is actually happening on the two surfaces a docked user spends the most time in. Bodha's queries are prefix-matched and short (ADR 0014), so the re-query path is Up-then-type rather than a long round trip.

## The guard is the tab traversal, walked

ADR 0020 argued that a rule with nothing to catch the next violation quietly becomes advisory. The answer here is **a Tab-traversal test**: inject key presses over the design gallery with `performKeyPress` and assert every node carrying `OnClick` is actually reached, and that Right reaches each Actions node.

Extending ADR 0020's existing tree-walk with "every node carrying `OnClick` is focusable" was rejected as nearly vacuous — `clickable` confers focus, so it passes by construction, and the failure mode is a new `pointerInput`, which publishes no node for a walk to see. The traversal test is the only form that would have caught the four gestures, and it fails loudly the moment someone adds a gesture-only affordance, because the count reached by Tab stops matching the count carrying actions.

Two consequences follow. Home's focus-revealed affordances are **lifted into the gallery** so the traversal can see them — the same move ADR 0020 made for `AppRow`, `IconCell` and the rail, for the same reason, and the sixteen goldens re-record again. And key injection under Robolectric with Compose focus traversal is unverified in this repo's test setup; if it does not work, the fallback is ADR 0020's walk plus a CI grep banning `pointerInput` outside a named allowlist, which is weaker and closer to the lint rule ADR 0020 left open.

## What this ADR does not settle

Right-arrow-to-actions is a convention a docked user must discover, and nothing announces it. The node being focus-revealed is the only hint, and it reveals only once the key has already been pressed. No answer is proposed here; onboarding (ADR 0018) is explicitly five steps and one grant, and does not teach gestures either.

The residual ADR 0020 recorded is unchanged and now has a sibling: neither #111's semantics nor this ADR's traversal has been exercised against a physical device — TalkBack for the first, a real docked keyboard for the second.

Resolved in issue #117.
