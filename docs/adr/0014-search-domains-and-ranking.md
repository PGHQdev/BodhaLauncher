# Search: seven domains, prefix matching, four explainable tiers

Seven domains ship: apps, launcher shortcuts, contacts, calendar events, focus actions, intentions and recent tasks, and settings actions. Three of #6's ten are deferred — files opened through Bodha (no such history exists, and inventing one to fill a spec line is scope by accident), connector actions (#20 rules connectors out), and the web-search fallback (still an open boundary question). Contacts and calendar are optional permissions with named degraded states through the capability-education sheet (#18); neither absence ever shows an empty section without saying why. Contact ranking is lexical only — affinity signals are dead at minSdk 29 (#88) — and `READ_CONTACTS` acquires a Play declaration obligation when targetSdk reaches 37, which is paperwork to schedule, not a blocker.

A query matches on **prefix at a word boundary, accent-insensitive**: "insta" matches Instagram, "jo" matches John Okafor, "gram" matches neither. App Library moves from its shipped substring match (`Library.kt`) to the same rule so one semantic holds product-wide. This is a user-visible regression for anyone relying on mid-word matching, accepted because substring search over a corpus containing contacts is noise — "an" would match half an address book. Typo tolerance was rejected: fuzzy scoring is precisely the unexplainable ranking that #6's result-explanation requirement rules out.

Ranking is four tiers, each a number that could be shown to the user: exact label match, then explicit user default or pin, then match quality (which field matched and how early), then recency and frequency from the launch log (ADR 0013). #6's "contextual relevance" and "inferred suggestions" tiers are dropped rather than faked — no prediction API is reachable, AppSearch ranks on three axes only, and nothing readable backs a prediction-framed claim (#88). A time-of-day boost was rejected for v1: it moves results for reasons the user did not cause. The "why this result" line draws only on these four tiers.

Search opens **empty** — no recents, no suggestions, keyboard up. You swiped down because you had something in mind, and an empty state cannot become a browsing surface. Showing recent apps was rejected: the first thing you see would be a list of what you already do too much of.

Hide and pin carry over unchanged from App Library (#61, #62). A **per-query default** — "when I type mail, this one first" — is tier two, reversible from the same long-press, and explainable in one line.

Domains render in a fixed section order — apps, shortcuts, contacts, calendar, actions — so nothing reorders under the finger. Slower providers fill in beneath what is already drawn and never displace it. Latency budgets are #27's.

Calendar in Search couples it to the calendar provider, which is unspecified and sits behind the Today ticket (#101). Search fixes what a calendar *result* is; the provider's shape does not belong here.

Resolved in issue #92.
