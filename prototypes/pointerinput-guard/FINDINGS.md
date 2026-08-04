# What the prototype measured — issue #115

Throwaway. The decision it fed is ADR 0024; this branch is the primary source.

## Corpus

Five `.pointerInput(` sites, three chains, all correct:

- `HomeGestures.kt` — two calls, one chain, `semantics {}` with six custom actions (#111)
- `LibraryScreen.kt` rail — two calls, one chain, `clearAndSetSemantics` (#114)
- `LibraryScreen.kt` `AppRow` — one call, chain also carries `combinedClickable`

None of the false-positive shapes the ticket worried about exist here, so
"flags nothing today" is vacuous as evidence.

## Sensitivity — real history, not a mock

    git show 9386331^:…/{HomeGestures,LibraryScreen,HomeScreen}.kt
    python3 guard.py <that tree>

    HomeGestures.kt:24   chain: pointerInput.pointerInput
    LibraryScreen.kt:418 chain: onSizeChanged.pointerInput.pointerInput
    2 flagged across 3 files

Both defects a person had to catch by reading. The rule works on the cases it
exists for.

## Specificity — five probes, one true positive

`probes.kt.txt`, all five flagged:

| Probe | Should flag | Flagged |
| --- | --- | --- |
| New surface, gesture-only, no semantics | yes | yes |
| Scrim swallowing taps (decorative) | no | yes |
| Drag-to-dismiss, back already dismisses | no | yes |
| Semantics on the parent `Box` | no | yes |
| Gesture in a `Modifier` extension, caller adds semantics | no | yes |

Four false positives, and they do not fail for one reason. Extension-inlining
is fixable by a real UAST detector. Semantics-on-an-ancestor needs Compose's
node tree, which Lint does not model. Decorative-versus-actionable is not
decidable at all — the scrim and the dismiss handler are structurally
identical and differ only in what the lambda means.

## The alternative, run

`allowlist-grep.sh` passes on the tree as it stands (`5 gesture site(s), all
allowlisted`) and fails on every site of a newly added file. ADR 0024 takes
its idea and drops the central list for a per-call inline marker, because a
file-level allowlist would let a fourth gesture inside `LibraryScreen.kt`
pass silently.
