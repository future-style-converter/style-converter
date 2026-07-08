#!/usr/bin/env bash
# Verifies that every "REWRITTEN" tracker note in TIER1_VARIANT_DEPTH.md
# has a corresponding fixture file present at the expected path AND a
# real fixture-edit commit in git history.
#
# Catches the lazy-fabrication pattern auditor round 38 was probing for:
# a tracker row marked passing/REWRITTEN where the fixture was never
# actually rewritten. That pattern is what made round 0's "548/548 100%
# passing" possible — and it took 40 rounds to clean up.
#
# Round 59 update: now exits non-zero on any fabrication so smoke.sh and
# CI can use it as a real guardrail (previously was info-only).
#
# Exit codes:
#   0 = all REWRITTEN claims have backing fixture + commit
#   1 = at least one fixture is missing or has zero edit history
#
# Usage (from anywhere):
#   bash docs/reports/loops/audit_rewrite_claims.sh

set -uo pipefail
cd "$(dirname "$0")/../../.."

TOTAL=$(grep -c "REWRITTEN" docs/reports/TIER1_VARIANT_DEPTH.md)
echo "Rows claiming REWRITTEN in tracker: $TOTAL"
echo "---"

FAILED=0
MISSING=()
NO_HISTORY=()

while IFS='|' read -r _ num cat prop _; do
  prop=$(echo "$prop" | xargs)
  cat=$(echo "$cat" | xargs)
  fp="fixtures/perfect/$cat/$prop.json"
  if [ ! -f "$fp" ]; then
    echo "✗ $cat/$prop: NO FIXTURE FILE at $fp"
    MISSING+=("$cat/$prop")
    FAILED=1
  else
    # --follow so history survives the R5 rename (examples/properties/perfect
    # → fixtures/perfect). Until that rename is committed, --follow can't see
    # across the staged move, so fall back to the pre-R5 path explicitly.
    last=$(git log --follow --oneline -1 -- "$fp" 2>/dev/null | head -c 80)
    if [ -z "$last" ]; then
      last=$(git log --oneline -1 -- "examples/properties/perfect/$cat/$prop.json" 2>/dev/null | head -c 80)
    fi
    if [ -z "$last" ]; then
      echo "✗ $cat/$prop: fixture exists but has NO COMMIT HISTORY"
      NO_HISTORY+=("$cat/$prop")
      FAILED=1
    else
      echo "✓ $cat/$prop: $last"
    fi
  fi
done < <(grep "REWRITTEN" docs/reports/TIER1_VARIANT_DEPTH.md)

echo "---"
if [ "$FAILED" -eq 0 ]; then
  echo "✓ all $TOTAL REWRITTEN claims backed by real fixture + commit"
else
  echo "✗ FAILED: ${#MISSING[@]} missing fixture(s), ${#NO_HISTORY[@]} with no history"
  echo "  This means the tracker is overpromising — a row claims REWRITTEN"
  echo "  but the fixture was never actually edited. Likely lazy-fabrication."
fi
exit "$FAILED"
