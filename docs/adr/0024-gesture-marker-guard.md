# The guard against the next unlabelled gesture is a marker, not a lint rule

ADR 0020 left open "whether a lint rule can catch the next `pointerInput` that ships without semantics", noting it defined the rule such a check would enforce rather than whether that rule is expressible. It is not, and this records why, and what stands in its place.

**Every `pointerInput` call carries an inline `// reachable: <reason>` marker, and CI fails on one that does not.**

## The lint rule's verdict would be wrong four times in five

A rough chain-aware detector was written and run, because the question was empirical and the ticket said so.

**The repo could not answer it.** Five `pointerInput` sites exist, in three chains, all correct — and not one of the shapes the false-positive worry named (nestedScroll companions, dismiss-on-overscroll, decorative handlers) exists here at all. Running any rule over this code returns a vacuous zero, so "it flags nothing today" was never going to be evidence.

Two sharper tests replaced it.

**Sensitivity, against real history.** Run over the tree as it stood before #111, the detector flags both defects a person had to catch by reading — Home's gestures and the letter rail — at the exact chains #111 and #114 fixed. So the rule catches the cases it exists for.

**Specificity, against five realistic probes: one true positive, four false.** It flags a scrim that swallows taps so they do not fall through; a drag-to-dismiss on a sheet that back already dismisses; a gesture whose semantics sit on the parent `Box`; and a gesture living in a `Modifier` extension whose callers supply the semantics.

Those four do not fail for one reason, and the split is what decides this ADR. **Extension-inlining a real Android Lint UAST detector could fix** — it resolves calls. **Semantics-on-an-ancestor and decorative-versus-actionable it cannot.** The first needs Compose's node tree, which Lint does not model; the second needs to know whether a gesture *does* anything, which is a judgement rather than a fact. The scrim and the dismiss handler are structurally identical — both are `pointerInput` with a lambda and no semantics — and only the meaning of what the lambda calls separates them.

Since the undecidable kind cannot be engineered away, suppressions become mandatory, and a rule suppressed at most of its call sites is a rule that means nothing. That was the stated bar, and a rule wrong four times in five on legitimate code does not clear it.

## The marker asserts a signature, not a verdict

The guard stops trying to judge reachability — the thing that cannot be judged — and asserts only that **someone stated the claim**. A grep over `.pointerInput(` requires a `// reachable:` marker on each call; a site without one fails CI.

This inverts the failure the lint rule has. The grep also "flags" all five probes, and that is correct behaviour rather than a false positive: it is not claiming they are broken, it is claiming nobody has signed them off. The scrim's marker reads that it exposes nothing because there is nothing to expose; the dismiss handler's reads that back already does it. Both are true, both are unprovable statically, and both are exactly what a reviewer needs to see.

It is the idiom ADR 0021 already established — a purely syntactic rule is a grep step in the existing CI job rather than a custom lint module, and it does not wait on whether a lint module is worth building.

## Per call, and inline rather than a central list

**The marker attaches per call, not per chain.** `homeGestures` chains two `pointerInput` calls, and one marker silently covering both is the same quiet coverage gap this guard exists to close.

**A file-level allowlist was rejected**, though it is what ADR 0021's font grep does literally. That precedent is weaker than it looks: there the allowed location is one file *by design* and the risk is anything outside it, whereas here legitimate gesture sites grow as surfaces ship, so a central list goes stale. Worse, it leaves the hole most likely to matter — a fourth gesture added inside `LibraryScreen.kt`, already listed, would pass silently, and that is where one would plausibly land. **A file-plus-count allowlist** closes that but blesses a number rather than a site, so swapping one gesture for another still passes.

The inline marker has no blind spot, and the reason appears in the diff where a reviewer is already reading.

The honest limit: a marker is a comment, and nothing forces the reason to be a real one. That is equally true of an allowlist entry — no mechanism can compel a genuine justification. What the marker buys is that the string has to be written at the moment the gesture is written, beside the code, rather than as a number bumped in a file nobody opens.

This supersedes the fallback shape ADR 0022 sketched for its traversal test, which described a grep "banning `pointerInput` outside a named allowlist". The marker is that guard in its decided form, and it stands whether or not the traversal test proves workable.

## What this ADR does not settle

The guard proves a claim was *made*, never that it is true. Nothing mechanical closes that gap, and the residual is accepted rather than engineered around: the marker moves the check from "someone must notice an omission" to "someone must write a sentence", which is a smaller ask and a visible one, not a proof.

Resolved in issue #115.
