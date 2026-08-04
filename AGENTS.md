# Bodha

Intentional Android launcher. Kotlin + Jetpack Compose. Open source; distributed on Google Play.

Product spec: `prd.md`. Screen and interaction spec: `screens.md`. Visual reference: `bodhalauncher.png`.

## Workflow

Spec vs ticket: a **spec** is one issue describing a whole feature (what, edge cases, test seams); a **ticket** is one vertical slice of buildable work with `blocked-by` links. One spec becomes many tickets.

Skill sequence:

1. `/wayfinder` — big foggy efforts only (more than one session can hold). Creates a map issue plus decision tickets; resolves them one at a time. Use it to settle prd.md §26 open decisions.
2. `/grill-with-docs` — stress-test a plan or design; writes ADRs and `CONTEXT.md` glossary entries as decisions land.
3. `/research` (background, primary sources) and `/prototype` (throwaway code) — feed answers into the above.
4. `/to-spec` — synthesize the current conversation into a spec issue, labeled `ready-for-agent`.
5. `/to-tickets` — break a spec (or prd.md section) into dependency-ordered tracer-bullet tickets.
6. `/triage` — move incoming issues through `needs-triage` → `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`.
7. `/implement` — build a spec or ticket with `/tdd` at agreed seams, run checks, `/code-review`, commit.

Day-to-day: small feature → discuss → `/to-spec` → `/implement`. Big murky effort → `/wayfinder` first. External issues → `/triage`.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues (via the `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
