#!/usr/bin/env bash
# PROTOTYPE — wipe me. Issue #115. The cheaper guard, in the idiom ADR 0021
# already established for purely syntactic rules: a grep step in the existing CI
# job, not a lint module.
#
# It does not judge whether a gesture is reachable — it cannot, and the lint
# prototype next to this file is the evidence. It asserts that every hand-rolled
# gesture site is one a human deliberately signed off, by name.
set -uo pipefail

# Every chain allowed to hand-roll a gesture, with why it is reachable anyway.
# Adding a line here is the review: it is the moment someone states the claim.
ALLOWED=(
  "app/src/main/kotlin/com/bodhalauncher/app/ui/HomeGestures.kt"    # semantics{} on the same chain: six custom actions (#111)
  "app/src/main/kotlin/com/bodhalauncher/app/ui/LibraryScreen.kt"   # rail: clearAndSetSemantics; AppRow: combinedClickable (#114)
)

found=$(grep -rn --include='*.kt' -E '\.pointerInput\(' app/src engine/src || true)

offenders=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  file="${line%%:*}"
  ok=""
  for a in "${ALLOWED[@]}"; do [ "$file" = "$a" ] && ok=1; done
  [ -z "$ok" ] && offenders+="$line"$'\n'
done <<< "$found"

if [ -n "$offenders" ]; then
  echo "Hand-rolled gesture outside the allowlist (ADR 0020): every actionable"
  echo "node must be named and reachable. Add the site to the allowlist in"
  echo "$0 with the reason it is reachable, or give it semantics."
  echo
  echo "$offenders"
  exit 1
fi

echo "$(printf '%s\n' "$found" | grep -c . ) gesture site(s), all allowlisted"
